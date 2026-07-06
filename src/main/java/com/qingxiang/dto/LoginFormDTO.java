package com.qingxiang.dto;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * <p>登录表单 DTO</p>
 *
 * <h3>优化说明（大厂最佳实践）</h3>
 * <ul>
 *   <li><b>JSR-380 Bean Validation：</b> 使用标准校验注解替代手工 if/else 校验，代码更简洁，错误信息更统一。</li>
 *   <li><b>@NotBlank vs @NotNull：</b> @NotBlank 会同时校验 null 和空字符串 ""，适合字符串入参。</li>
 *   <li><b>@Pattern 正则：</b> 手机号格式用正则声明式校验，收敛校验逻辑到 DTO 层，Service 层不再手写校验。</li>
 *   <li><b>message 提示：</b> 每个注解都带中文 message，校验失败时通过 MethodArgumentNotValidException 全局处理返回给前端。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Data
public class LoginFormDTO {

    /**
     * 手机号（支持中国大陆手机号格式）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    /**
     * 短信验证码（6位数字）
     */
    @NotBlank(message = "验证码不能为空")
    @Length(min = 6, max = 6, message = "验证码长度必须为6位")
    private String code;

    /**
     * 密码（密码登录模式时使用，可选）
     */
    private String password;
}
