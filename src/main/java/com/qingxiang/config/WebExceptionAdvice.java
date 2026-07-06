package com.qingxiang.config;

import com.qingxiang.dto.Result;
import com.qingxiang.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * <p>全局异常处理器</p>
 *
 * <h3>优化说明（大厂最佳实践）</h3>
 * <ul>
 *   <li><b>分级处理：</b> 从具体异常 → 通用异常逐级 catch，让每个异常都有对应的错误码，而不是全部返回"服务器异常"。</li>
 *   <li><b>统一日志：</b> 所有异常都在此处记录，避免 Service 层到处写 try-catch + log.error。</li>
 *   <li><b>生产级实践：</b> 大厂项目会在此处接入告警（如 Sentry / 企业微信通知），将 error 级别日志推送值班群。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    // ==================== 参数校验异常 ====================

    /**
     * Controller 参数缺失（如 @RequestParam 没传）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("请求参数缺失: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID);
    }

    /**
     * HTTP 方法错误（如 POST 接口用了 GET 请求）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    // ==================== 数据异常 ====================

    /**
     * JSR-380 参数校验失败（@Valid 触发，如手机号格式不对、验证码为空）
     * <p>
     * 大厂实践：校验失败时返回 400 + 第一个校验字段的错误 message，前端逐字段展示。
     * 这里遍历 BindingResult 拿到第一个 FieldError 的 message 返回。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", message);
        return Result.fail(ErrorCode.PARAM_INVALID, message);
    }

    // ==================== 数据异常 ====================

    /**
     * 数据库唯一键冲突（如重复下单，被 DB 唯一索引拦截）
     * 注意：秒杀场景下这个异常已被 VoucherOrderServiceImpl 内部 catch 处理，
     * 此处作为兜底，防止其他模块漏掉的 DuplicateKeyException 返回 500
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKey(DuplicateKeyException e) {
        log.warn("数据重复插入: {}", e.getMessage());
        return Result.fail(ErrorCode.DUPLICATE_KEY);
    }

    // ==================== 兜底异常 ====================

    /**
     * 运行时异常兜底
     * <p>
     * 大厂实践：此处是最后一道防线。所有未被上层 catch 的 RuntimeException 都会到这。
     * 线上环境会将此日志级别设为 ERROR，并接入告警系统。
     */
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("未捕获的运行时异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 终极兜底：处理所有未被上述 handler 覆盖的异常
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("未捕获的未知异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
