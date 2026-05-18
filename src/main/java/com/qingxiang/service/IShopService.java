package com.qingxiang.service;

import com.qingxiang.dto.Result;
import com.qingxiang.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
public interface IShopService extends IService<Shop> {


    Result queryById(Long id);

    Result update(Shop shop);
}
