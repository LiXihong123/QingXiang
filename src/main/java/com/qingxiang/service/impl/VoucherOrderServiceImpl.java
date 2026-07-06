package com.qingxiang.service.impl;

import com.qingxiang.dto.Result;
import com.qingxiang.entity.SeckillVoucher;
import com.qingxiang.enums.ErrorCode;
import com.qingxiang.entity.VoucherOrder;
import com.qingxiang.mapper.VoucherOrderMapper;
import com.qingxiang.service.ISeckillVoucherService;
import com.qingxiang.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingxiang.utils.RedisConstants;
import com.qingxiang.utils.RedisIdWorker;
import com.qingxiang.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * <p>
 *  秒杀订单服务实现类 — 基于 Redis Lua 脚本的原子秒杀
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;

    private final RedisIdWorker redisIdWorker;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀 Lua 脚本：一条命令原子完成「判重复 → 判库存 → 扣库存 → 标记用户」四步操作
     *
     * KEYS[1] = seckill:stock:{voucherId}     库存 key
     * KEYS[2] = seckill:order:{voucherId}     已购买用户集合 key
     * ARGV[1] = userId                         当前用户 ID
     *
     * 返回值约定：
     *   0 — 秒杀成功（库存 -1，用户已标记）
     *   1 — 用户已购买过该优惠券（重复领取）
     *   2 — 库存不足
     */
    private static final String SECKILL_LUA_SCRIPT =
            "-- 1. 判断用户是否已经购买过该秒杀券\n" +
            "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then\n" +
            "    return 1\n" +
            "end\n" +
            "-- 2. 判断库存是否充足\n" +
            "local stock = tonumber(redis.call('GET', KEYS[1]))\n" +
            "if stock == nil or stock <= 0 then\n" +
            "    return 2\n" +
            "end\n" +
            "-- 3. 扣减库存\n" +
            "redis.call('DECR', KEYS[1])\n" +
            "-- 4. 将用户加入已购买集合，防止重复领取\n" +
            "redis.call('SADD', KEYS[2], ARGV[1])\n" +
            "return 0";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setScriptText(SECKILL_LUA_SCRIPT);
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // ==================== 1. 快速校验（读 DB，过滤掉明显无效的请求） ====================

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail(ErrorCode.SECKILL_VOUCHER_NOT_FOUND);
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail(ErrorCode.SECKILL_NOT_STARTED);
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail(ErrorCode.SECKILL_ENDED);
        }

        // ==================== 2. 库存懒加载到 Redis ====================

        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucherId;

        // 使用 SETNX 保证只有一个线程初始化库存，避免并发覆盖
        stringRedisTemplate.opsForValue()
                .setIfAbsent(stockKey, voucher.getStock().toString());

        // ==================== 3. 预生成订单 ID（提前生成，减少 Lua 脚本参数） ====================

        long orderId = redisIdWorker.nextId("order");

        // ==================== 4. 执行 Lua 脚本（核心：原子化秒杀） ====================

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(stockKey, orderKey),   // KEYS
                userId.toString()                     // ARGV
        );

        // ==================== 5. 根据返回值处理 ====================

        if (result == null) {
            // Redis 执行异常（如脚本语法错误），记录日志并返回兜底错误
            log.error("秒杀 Lua 脚本执行返回 null，voucherId={}, userId={}", voucherId, userId);
            return Result.fail(ErrorCode.SYSTEM_ERROR);
        }

        int r = result.intValue();

        if (r == 1) {
            // 用户已经买过了
            return Result.fail(ErrorCode.SECKILL_ALREADY_ORDERED);
        }
        if (r == 2) {
            // 库存不足
            return Result.fail(ErrorCode.SECKILL_STOCK_EMPTY);
        }

        // ==================== 6. Lua 执行成功，创建订单写入数据库 ====================

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setStatus(1);   // 1 = 未支付
        voucherOrder.setCreateTime(LocalDateTime.now());

        try {
            save(voucherOrder);
        } catch (DuplicateKeyException e) {
            // 数据库唯一索引兜底拦截：Redis 可能因重启丢失数据，此时 DB 作为最后防线
            // 注意：这里不需要补偿 Redis，因为 DB 拦截说明用户已购买，Redis 里也理应有记录
            log.warn("重复下单被数据库唯一索引拦截，voucherId={}, userId={}", voucherId, userId);
            return Result.fail(ErrorCode.SECKILL_ALREADY_ORDERED);
        } catch (Exception e) {
            // 其他 DB 写入异常 → 补偿 Redis，回滚库存和购买标记
            log.error("秒杀订单 DB 写入失败，执行 Redis 补偿。voucherId={}, userId={}", voucherId, userId, e);
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.opsForSet().remove(orderKey, userId.toString());
            return Result.fail(ErrorCode.SECKILL_ORDER_FAILED);
        }

        // ==================== 7. 返回订单 ID ====================

        return Result.ok(orderId);
    }
}