package com.qingxiang.enums;

/**
 * <p>统一错误码枚举</p>
 *
 * <h3>优化说明（大厂最佳实践）</h3>
 * <ul>
 *   <li><b>为什么需要错误码？</b> 前端/客户端需要通过数字错误码做程序化判断（如 40005 表示已领取 → 弹窗提示），
 *       而不是解析中文 String。大厂所有 API 都必须返回 code + message。</li>
 *   <li><b>编码规范：</b> 参考阿里 Java 开发手册，使用 5 位数字，按模块分段：
 *       1xxxx 通用、2xxxx 用户、3xxxx 商户、4xxxx 秒杀、5xxxx 博客、9xxxx 文件</li>
 *   <li><b>面试亮点：</b> 统一错误码体系是"生产级项目"和"Demo 项目"的分水岭。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2026-07-06
 */
public enum ErrorCode {

    // ==================== 0: 成功 ====================
    SUCCESS(0, "ok"),

    // ==================== 1xxxx: 通用系统错误 ====================
    SYSTEM_ERROR(10001, "服务器异常，请稍后再试"),
    PARAM_INVALID(10002, "请求参数不合法"),
    UNAUTHORIZED(10003, "请先登录"),
    NOT_FOUND(10004, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(10005, "请求方法不支持"),
    TOO_MANY_REQUESTS(10006, "请求过于频繁，请稍后再试"),
    DUPLICATE_KEY(10007, "数据已存在，请勿重复操作"),
    BUSINESS_ERROR(10008, "业务处理异常"),

    // ==================== 2xxxx: 用户模块 ====================
    USER_PHONE_INVALID(20001, "手机号格式错误"),
    USER_CODE_ERROR(20002, "验证码错误"),
    USER_NOT_FOUND(20003, "用户不存在"),
    USER_NOT_LOGIN(20004, "用户未登录"),

    // ==================== 3xxxx: 商户模块 ====================
    SHOP_NOT_FOUND(30001, "店铺不存在"),
    SHOP_ID_NULL(30002, "店铺ID不能为空"),

    // ==================== 4xxxx: 秒杀模块 ====================
    SECKILL_VOUCHER_NOT_FOUND(40001, "优惠券不存在"),
    SECKILL_NOT_STARTED(40002, "秒杀尚未开始"),
    SECKILL_ENDED(40003, "秒杀已经结束"),
    SECKILL_STOCK_EMPTY(40004, "手慢了，优惠券已被抢光"),
    SECKILL_ALREADY_ORDERED(40005, "您已经领取过该优惠券，请勿重复领取"),
    SECKILL_ORDER_FAILED(40006, "下单失败，请重试"),

    // ==================== 5xxxx: 博客模块 ====================
    BLOG_CONTENT_EMPTY(50001, "博客内容不能为空"),
    BLOG_IMAGE_EXCEED(50002, "图片数量不能超过9张"),

    // ==================== 9xxxx: 文件上传模块 ====================
    UPLOAD_FAILED(90001, "文件上传失败"),
    UPLOAD_FILENAME_INVALID(90002, "错误的文件名称");

    /**
     * 业务错误码（5位数字，按模块分段，参考阿里 Java 开发手册）
     */
    private final int code;

    /**
     * 面向用户的提示信息（中文，可前端直接展示）
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
