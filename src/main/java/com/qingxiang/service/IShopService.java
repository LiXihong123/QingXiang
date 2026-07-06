package com.qingxiang.service;

import com.qingxiang.dto.Result;
import com.qingxiang.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  商铺服务接口
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据 ID 查询商铺（含 Redis 多级缓存）
     */
    Result queryById(Long id);

    /**
     * 更新商铺信息（先更新数据库，再删除缓存 — Cache Aside 模式）
     */
    Result update(Shop shop);

    /**
     * 新增商铺
     * <p>
     * 优化说明：原 Controller 直接调用 shopService.save(shop)，
     * 现下沉到 Service 层，方便后续加缓存预热、数据校验等逻辑。
     */
    Result saveShop(Shop shop);

    /**
     * 按商铺类型分页查询
     * <p>
     * 优化说明：原 Controller 直接组装 Lambda 查询链，
     * 现将分页查询逻辑封装到 Service 层。
     */
    Result queryByType(Integer typeId, Integer current);

    /**
     * 按名称关键字搜索商铺
     * <p>
     * 优化说明：同上，搜索逻辑下沉到 Service，方便后续接入 Elasticsearch 或加缓存。
     */
    Result queryByName(String name, Integer current);

    /**
     * 附近商户搜索（Redis GEO 实现）
     * <p>
     * 大厂亮点：使用 Redis GEO 数据结构做地理位置搜索，
     * 替代 MySQL 的经纬度计算（慢且无法利用索引）。
     * GEOADD 存坐标 → GEOSEARCH 按半径搜索 → 返回带距离的商户列表。
     *
     * @param lng    当前经度
     * @param lat    当前纬度
     * @param radius 搜索半径（公里）
     */
    Result queryNearby(Double lng, Double lat, Integer radius);
}
