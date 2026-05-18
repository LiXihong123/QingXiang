package com.qingxiang.service.impl;

import com.qingxiang.dto.Result;
import com.qingxiang.entity.SeckillVoucher;
import com.qingxiang.entity.VoucherOrder;
import com.qingxiang.mapper.VoucherOrderMapper;
import com.qingxiang.service.ISeckillVoucherService;
import com.qingxiang.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingxiang.utils.RedisIdWorker;
import com.qingxiang.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //秒杀结束
              return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock()<1) {
            //库存不足
            return Result.fail("库存不足");
        }
        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id",voucherId)
                .update();
        if(!success){
            return Result.fail("库存不足");
        }

        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户ID
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //优惠券ID
        voucherOrder.setVoucherId((voucherId));
        //订单写入数据库
        save(voucherOrder);
        //7.返回订单ID

        return Result.ok(orderId);
    }
}
