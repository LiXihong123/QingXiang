package com.qingxiang.controller;


import com.qingxiang.dto.Result;
import com.qingxiang.service.IVoucherOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>秒杀订单控制器 — Redis Lua 原子秒杀</p>
 *
 * <h3>技术亮点</h3>
 * 三层防护：Redis Lua 原子操作 → DB 联合唯一索引 → DuplicateKeyException 兜底
 */
@RestController
@RequestMapping("/voucher-order")
@Api(tags = "秒杀模块")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    @ApiOperation("秒杀优惠券（Redis Lua 原子操作）")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
