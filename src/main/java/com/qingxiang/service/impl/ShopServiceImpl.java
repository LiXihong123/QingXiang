package com.qingxiang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qingxiang.dto.Result;
import com.qingxiang.entity.Shop;
import com.qingxiang.enums.ErrorCode;
import com.qingxiang.mapper.ShopMapper;
import com.qingxiang.service.IShopService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingxiang.utils.CacheClient;
import com.qingxiang.utils.RedisData;
import com.qingxiang.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.qingxiang.utils.RedisConstants.*;

/**
 * <p>
 *  商铺服务实现 — Redis 多级缓存策略（Cache Aside + 逻辑过期 + 互斥锁）
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    private final CacheClient cacheClient;
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
            return Result.fail(ErrorCode.SHOP_NOT_FOUND);
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
            return Result.fail(ErrorCode.SHOP_ID_NULL);
        }
        //1.先更新数据库
        updateById(shop);
        //2.再删除Redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    // ==================== 以下方法为"Controller 业务逻辑下沉"优化新增 ====================

    @Override
    public Result saveShop(Shop shop) {
        /*
         * 优化说明：
         * 原 Controller 直接调用 shopService.save(shop) + 返回 shop.getId()
         * 下沉到 Service 层后，可以在这里统一做：
         * 1. 数据校验（价格、坐标合法性等）
         * 2. 缓存预热（新店铺写入 Redis）
         * 3. GEO 坐标同步到 Redis（附近商户搜索功能）
         */
        save(shop);
        // 同步店铺坐标到 Redis GEO（附近商户搜索）
        // 大厂亮点：利用 Redis GEO 数据结构，O(log N) 时间复杂度搜索附近商户
        if (shop.getX() != null && shop.getY() != null) {
            stringRedisTemplate.opsForGeo()
                    .add(SHOP_GEO_KEY,
                            new org.springframework.data.geo.Point(shop.getX(), shop.getY()),
                            shop.getId().toString());
        }
        return Result.ok(shop.getId());
    }

    @Override
    public Result queryByType(Integer typeId, Integer current) {
        /*
         * 优化说明：
         * 原 Controller 内联：shopService.query().eq("type_id", typeId).page(...)
         * 下沉到 Service 后：
         * 1. 可加 @Cacheable 注解或手动缓存
         * 2. 后续改 SQL 只需改此处
         * 3. 单元测试可 Mock 此方法
         */
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result queryByName(String name, Integer current) {
        /*
         * 优化说明：
         * 原 Controller 内联：shopService.query().like(StrUtil.isNotBlank(name), "name", name).page(...)
         * 下沉到 Service 后，搜索逻辑内聚在一个方法中，方便后续接入 Elasticsearch 做全文搜索。
         */
        Page<Shop> page = query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result queryNearby(Double lng, Double lat, Integer radius) {
        /*
         * Redis GEO 附近商户搜索（大厂面试亮点）
         *
         * 数据结构：GEO = Sorted Set 的地理扩展，将经纬度编码为 GeoHash Score
         * GEOADD shop:geo lng lat member — 添加坐标
         * GEORADIUS shop:geo lng lat radius km — 半径搜索（Spring Data Redis 2.x API）
         *
         * 为什么不用 MySQL 算经纬度？
         * 1. MySQL 计算两点距离需要全表扫描（无法走索引）
         * 2. Redis GEO 底层用 GeoHash + Sorted Set，查询 O(log N)
         * 3. 美团/饿了么都使用 Redis GEO 做"附近商家"功能
         */
        String key = SHOP_GEO_KEY;
        // GEORADIUS：以 (lng, lat) 为中心，半径 radius 公里内搜索
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key,
                        new Circle(new Point(lng, lat), new Distance(radius, Metrics.KILOMETERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .sortAscending()
                                .limit(50));
        // 解析结果，返回店铺 ID + 距离
        List<java.util.Map<String, Object>> shopList = new java.util.ArrayList<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : results.getContent()) {
                String shopIdStr = geoResult.getContent().getName();
                Distance distance = geoResult.getDistance();
                // 查 DB 获取店铺详情（可优化：批量查）
                Shop shop = getById(Long.valueOf(shopIdStr));
                if (shop != null) {
                    shop.setDistance(distance.getValue()); // distance 是 @TableField(exist = false) 的 transient 字段
                    shopList.add(BeanUtil.beanToMap(shop, new java.util.HashMap<>(), false, true));
                }
            }
        }
        return Result.ok(shopList);
    }
}
