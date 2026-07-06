package com.qingxiang.controller;


import com.qingxiang.dto.Result;
import com.qingxiang.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  商铺类型控制器
 * </p>
 *
 * <h3>优化说明</h3>
 * <ul>
 *   <li><b>逻辑下沉：</b> 原 Controller 直接调用 {@code typeService.query().orderByAsc("sort").list()}
 *       现已移到 {@link IShopTypeService#queryAllSorted()} 方法中。</li>
 *   <li><b>单一职责：</b> Controller 只做参数接收 + 结果返回，业务逻辑全部在 Service 层。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        return Result.ok(typeService.queryAllSorted());
    }
}
