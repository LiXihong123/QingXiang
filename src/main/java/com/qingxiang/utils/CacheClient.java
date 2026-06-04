package com.qingxiang.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.qingxiang.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期对象
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds( time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

     public <R,ID> R queryWithPassThrough(String keyPrefix , ID id,
                                          Class<R> type, Function<ID,R> dbFallback,
                                          Long time, TimeUnit unit){
        //1.从Redis中查询缓存
        String Json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        //2.判断shopJson是否存在
        if(StrUtil.isNotBlank(Json)){
            //缓存存在，直接返回店铺信息
            return JSONUtil.toBean(Json, type);
        }
        //空值检测，防止缓存穿透
        if(Json != null){
            return null;
        }
        //3.Redis缓存未命中，根据getById(id)查询数据库
        R r = dbFallback.apply(id);
        //4.数据库不存在，返回错误
        if(r == null){
            //将空值写入redis，解决缓存穿透
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;//返回错误信息
        }
        //5.数据库存在，写入Redis
        this.set(keyPrefix + id, r, time, unit);
        //6.返回
        return r;
    }

    // 优化后的缓存重建线程池（替换原来的Executors.newFixedThreadPool(10)）
private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
    // 核心线程数=CPU核心数，缓存重建是CPU密集型任务
    Runtime.getRuntime().availableProcessors(),
    // 最大线程数=核心线程数，不需要非核心线程
    Runtime.getRuntime().availableProcessors(),
    0L, TimeUnit.MILLISECONDS,
    // 有界队列，最多存放100个等待任务，避免OOM
    new ArrayBlockingQueue<>(100),
    // 自定义线程工厂，设置有意义的线程名，方便排查问题
    new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "cache-rebuild-thread-" + threadNumber.getAndIncrement());
        }
    },
    // 自定义拒绝策略：当队列满了，由调用线程自己执行任务
    // 这样既不会丢失任务，也不会让请求线程直接返回失败
    new ThreadPoolExecutor.CallerRunsPolicy()
);

    public  <R,ID> R queryWithLogicalExpire(String keyPrefix , ID id, Class<R> type,
                                            Function<ID, R> dbFallback,
                                            Long time, TimeUnit unit){
        String key =keyPrefix + id;
        //1.从Redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断shopJson是否存在
        if(StrUtil.isBlank(json)){
            R r = dbFallback.apply(id);
            if(r == null){
                // 数据库也不存在，缓存空值防止穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
                return null;
            }
            // 数据库存在，写入Redis并返回
            this.setWithLogicalExpire(key, r, time, unit);
            return r;
        }
        //4.缓存命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //取到店铺数据
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //取到过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断过期时间
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1.未过期，直接返回店铺信息
            return r;
        }
        //5.2.已过期，需要缓存重建
        //6.重建缓存
        //6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2判断获取互斥锁是否成功
        if(isLock){
            //6.3成功获取锁，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    //第一步：查询数据库
                    R r1 = dbFallback.apply(id);
                    //第二步：写入Redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.4返回过期的店铺信息
        return r;
    }

    //尝试获取互斥锁，获取锁成功返回true，获取锁失败则返回false，用于解决缓存击穿问题
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    //释放互斥锁，用于解决缓存击穿问题
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
