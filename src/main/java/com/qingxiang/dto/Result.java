package com.qingxiang.dto;

import com.qingxiang.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * <p>统一 API 响应体</p>
 *
 * <h3>优化说明（大厂最佳实践）</h3>
 * <ul>
 *   <li><b>code 字段：</b> 新增 Integer 类型错误码，前端可据此做程序化判断（如 code=40005 → 弹窗"已领取"），
 *       不再需要解析中文 errorMsg 字符串。这是阿里/美团所有 API 的标配。</li>
 *   <li><b>ErrorCode 枚举：</b> 错误码集中管理在 {@link ErrorCode} 枚举中，添加新错误时只需新增枚举值，
 *       不会散落在代码各处，方便统一维护和国际化。</li>
 *   <li><b>向后兼容：</b> 保留了 success + errorMsg 字段，前端可以渐进式升级，不会因新增 code 而报错。</li>
 *   <li><b>总数字段：</b> total 用于分页查询返回总数，配合 MyBatis-Plus 的 Page 对象使用。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    /**
     * 请求是否成功
     */
    private Boolean success;

    /**
     * 业务错误码（0=成功，1xxxx=通用错误，2xxxx=用户模块，3xxxx=商户模块，4xxxx=秒杀模块...）
     * 大厂规范：所有 API 必须返回数字 code，前端根据 code 做差异化处理（弹窗/跳转/重试）
     */
    private Integer code;

    /**
     * 面向用户的提示信息（中文，可直接在界面上展示）
     */
    private String errorMsg;

    /**
     * 响应数据体（单条数据或列表）
     */
    private Object data;

    /**
     * 总数（仅分页查询时使用）
     */
    private Long total;

    // ==================== 成功响应（使用 ErrorCode.SUCCESS 的 code = 0） ====================

    public static Result ok() {
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, null, null);
    }

    public static Result ok(Object data) {
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, data, null);
    }

    public static Result ok(List<?> data, Long total) {
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, data, total);
    }

    // ==================== 失败响应（使用 ErrorCode 枚举，自动携带 code + message） ====================

    /**
     * 通过 ErrorCode 枚举构建失败响应
     * <p>
     * 大厂实践：所有错误都走这个方法，确保每个错误都有唯一的数字 code。
     * 前端可以根据 code 做差异化处理（如：code=40005 表示已领取 → 弹窗提示，code=40004 表示抢光 → 置灰按钮）
     *
     * @param errorCode 错误码枚举
     * @return 携带 code + message 的失败响应
     */
    public static Result fail(ErrorCode errorCode) {
        return new Result(false, errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    /**
     * 构建失败响应（覆盖默认错误消息）
     * <p>
     * 适用场景：参数校验失败时，code 保持统一的 PARAM_INVALID，但 message 使用具体字段的校验提示。
     * 例如：code=10002, message="手机号格式错误" 而不是 code=10002, message="请求参数不合法"
     *
     * @param errorCode 错误码枚举（使用其 code）
     * @param message   自定义错误消息（覆盖枚举的默认 message）
     */
    public static Result fail(ErrorCode errorCode, String message) {
        return new Result(false, errorCode.getCode(), message, null, null);
    }
}
