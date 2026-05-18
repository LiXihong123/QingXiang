package com.qingxiang.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qingxiang.dto.Result;
import com.qingxiang.entity.Shop;
import com.qingxiang.mapper.ShopMapper;
import com.qingxiang.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingxiang.utils.CacheClient;
import com.qingxiang.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.qingxiang.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透： 添加空值
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id, Shop.class,
        //                                    this::getById, CACHE_NULL_TTL, TimeUnit.MINUTES);

        //解决缓存击穿： 互斥锁
        //Shop shop = queryWithMutex(id);

        //解决缓存击穿： 逻辑过期
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class,
                this::getById, 20L, TimeUnit.MINUTES);

        if(shop == null){
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(shop);
    }

    //线程池：缓存重建的执行器
    private static  final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id){
        //1.从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断shopJson是否存在
        if(StrUtil.isBlank(shopJson)){
            //缓存存在，直接返回店铺信息
            return null;
        }
        //4.缓存命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //取到店铺数据
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //取到过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断过期时间
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1.未过期，直接返回店铺信息
            return shop;
        }
        //5.2.已过期，需要缓存重建
        //6.重建缓存
        //6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2判断获取互斥锁是否成功
        if(isLock){
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShopToRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.4返回过期的店铺信息
        return shop;
    }

    public Shop queryWithMutex(Long id){
        //1.从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断shopJson是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //缓存存在，直接返回店铺信息
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //空值检测，防止缓存穿透
        if(shopJson != null){
            return null;
        }
        //3.实现缓存重建
        //3.1.获取互斥锁
        Shop shop = null;
        try {
            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
            //3.2.判断是否获取成功
            if(!isLock){
                //3.3.失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);//递归重试
            }
            //3.4.成功，再次检测Redis缓存是否存在，做double check，如果存在则无需重建缓存
            /*shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(shopJson)){
                return JSONUtil.toBean(shopJson, Shop.class);
            }*/

            //3.5根据getById(id)查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //4.数据库不存在，返回错误
            if(shop == null){
                //将空值写入redis，解决缓存穿透
                stringRedisTemplate.opsForValue()
                        .set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //5.数据库存在，写入Redis
            //CACHE_SHOP_TTL时间加上一个1-5分钟的随机数，防止缓存雪崩
            int randomMinutes = RandomUtil.randomInt(1, 6);
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + randomMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //6.释放互斥锁
            unLock(LOCK_SHOP_KEY + id);
        }
        //7.返回
        return shop;
    }

    //封装
    public Shop queryWithPassThrough(Long id){
        //1.从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断shopJson是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //缓存存在，直接返回店铺信息
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //空值检测，防止缓存穿透
        if(shopJson != null){
            return null;
        }
        //3.Redis缓存未命中，根据getById(id)查询数据库
        Shop shop = getById(id);
        //4.数据库不存在，返回错误
        if(shop == null){
            //将空值写入redis，解决缓存穿透
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;//返回错误信息
        }
        //5.数据库存在，写入Redis
        //CACHE_SHOP_TTL时间加上一个1-5分钟的随机数，防止缓存雪崩
        int randomMinutes = RandomUtil.randomInt(1, 6);
        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                        CACHE_SHOP_TTL + randomMinutes, TimeUnit.MINUTES);
        //6.返回
        return shop;
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

//将店铺信息和逻辑过期时间封装成RedisData对象，写入Redis的方法
public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional//事务注解：保证原子性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺ID不能为空！");
        }
        //1.先更新数据库
        updateById(shop);
        //2.再删除Redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
