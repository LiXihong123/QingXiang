package com.qingxiang.service;

import com.qingxiang.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  商铺类型服务接口
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询所有商铺类型，按 sort 字段升序排列
     * <p>
     * 优化说明：原 Controller 直接调用 typeService.query().orderByAsc("sort").list()
     * 属于业务逻辑泄漏，现下沉到 Service 层封装。
     *
     * @return 排序后的商铺类型列表
     */
    List<ShopType> queryAllSorted();
}
