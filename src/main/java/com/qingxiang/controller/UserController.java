package com.qingxiang.controller;


import com.qingxiang.dto.LoginFormDTO;
import com.qingxiang.dto.Result;
import com.qingxiang.enums.ErrorCode;
import com.qingxiang.entity.User;
import com.qingxiang.entity.UserInfo;
import com.qingxiang.service.IUserInfoService;
import com.qingxiang.service.IUserService;
import com.qingxiang.utils.RedisConstants;
import com.qingxiang.utils.UserHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * <p>用户控制器 — 短信登录、鉴权、个人信息</p>
 *
 * @author 李锡宏
 * @since 2024-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Api(tags = "用户模块")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("code")
    @ApiOperation("发送短信验证码")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    @PostMapping("/login")
    @ApiOperation("手机号+验证码登录")
    public Result login(@Valid @RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }

    @PostMapping("/logout")
    @ApiOperation("退出登录")
    public Result logout(){
        return Result.fail(ErrorCode.BUSINESS_ERROR);
    }

    @GetMapping("/me")
    @ApiOperation("获取当前登录用户信息")
    public Result me(){
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    @ApiOperation("查询用户详细信息")
    public Result info(@PathVariable("id") Long userId){
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    /**
     * 查询用户基本信息（供前端 other-info.html 调用）
     * <p>返回 User 实体，Lombok 序列化时会忽略敏感字段（password 等）</p>
     */
    @GetMapping("/{id}")
    @ApiOperation("查询用户基本信息")
    public Result userById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }
        // 敏感字段 password/phone 已通过 @JsonIgnore 全局脱敏，无需手动置 null
        return Result.ok(user);
    }

    // ==================== Redis Bitmap 签到功能（大厂面试亮点） ====================

    /**
     * 用户每日签到（Redis Bitmap 实现）
     * <p>
     * 大厂亮点：
     * <ul>
     *   <li><b>为什么用 Bitmap？</b> 每位用户每月只需 ~400 bytes（31 位 / 8）。
     *       1 亿用户 = 40GB，如果用 MySQL 表则需要 TB 级别存储。</li>
     *   <li><b>SETBIT sign:{userId}:{yyyy:MM} offset 1：</b>
     *       将当天（1~31）对应的 bit 置为 1，天生去重幂等。</li>
     *   <li><b>BITCOUNT：</b> O(N) 统计本月签到天数，极快。</li>
     *   <li><b>对比方案：</b> MySQL 签到表需要 (user_id, date) 联合索引，亿级用户时索引巨大。</li>
     * </ul>
     */
    @PostMapping("/sign")
    @ApiOperation("每日签到（Redis Bitmap SETBIT）")
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        int dayOfMonth = now.getDayOfMonth();
        // SETBIT 将指定偏移量的 bit 置为 1，幂等操作（已签到重复调用无影响）
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok("签到成功");
    }

    /**
     * 查询本月累计签到天数（Redis Bitmap BITCOUNT）
     */
    @GetMapping("/sign/count")
    @ApiOperation("查询本月签到天数（Redis Bitmap BITCOUNT）")
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        // BITCOUNT 统计所有位为 1 的数量 = 本月签到天数
        // 注：Spring Data Redis 2.3 中 BITCOUNT 需通过 execute + RedisCallback 调用
        Long count = stringRedisTemplate.execute(
                (RedisCallback<Long>) connection -> connection.bitCount(key.getBytes()));
        // 注意：如果是空 key（本月还没签过到），Redis BITCOUNT 返回 0（不会报错）
        return Result.ok(count);
    }

    /**
     * 查询今天是否已签到（Redis Bitmap GETBIT）
     */
    @GetMapping("/sign/today")
    @ApiOperation("查询今日是否已签到（Redis Bitmap GETBIT）")
    public Result signToday() {
        Long userId = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        int dayOfMonth = now.getDayOfMonth();
        // GETBIT 返回指定偏移量的 bit 值（true/false）
        Boolean signed = stringRedisTemplate.opsForValue().getBit(key, dayOfMonth - 1);
        return Result.ok(Boolean.TRUE.equals(signed));
    }
}
