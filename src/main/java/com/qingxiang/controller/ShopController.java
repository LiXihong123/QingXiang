package com.qingxiang.controller;


import com.qingxiang.dto.Result;
import com.qingxiang.entity.Shop;
import com.qingxiang.service.IShopService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  商铺控制器
 * </p>
 *
 * <h3>优化说明（Controller 逻辑下沉到 Service 层）</h3>
 * <ul>
 *   <li><b>原问题：</b> queryShopByType 和 queryShopByName 在 Controller 中直接组装 Mybatis-Plus Lambda 查询链，
 *       违反了"Controller 只做参数接收和结果返回"的分层原则。</li>
 *   <li><b>改造后：</b> 所有查询逻辑统一封装在 {@link IShopService} 中，Controller 仅做参数透传。
 *       这样既方便单元测试，也方便后续在 Service 层统一加缓存、改 SQL。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息（含 Redis 多级缓存）
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 优化：原直接调用 shopService.save(shop)，现下沉到 Service.saveShop() 封装
        return shopService.saveShop(shop);
    }

    /**
     * 更新商铺信息（Cache Aside 模式：先更新 DB，再删除 Redis 缓存）
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 优化：原在 Controller 内联组装 Lambda 查询，现委托给 Service.queryByType()
        return shopService.queryByType(typeId, current);
    }

    /**
     * 根据名称关键字搜索商铺
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return shopService.queryByName(name, current);
    }

    /**
     * 附近商户搜索（Redis GEO 实现）
     * <p>
     * 大厂亮点：使用 Redis GEO 数据结构代替 MySQL 经纬度计算，
     * GEOADD 存坐标 → GEORADIUS 半径搜索 → O(log N) 查询
     */
    @GetMapping("/nearby")
    public Result queryNearby(
            @RequestParam Double lng,
            @RequestParam Double lat,
            @RequestParam(defaultValue = "5") Integer radius) {
        return shopService.queryNearby(lng, lat, radius);
    }
}
