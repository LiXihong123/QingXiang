package com.qingxiang.config;

import com.qingxiang.utils.LoginInterceptor;
import com.qingxiang.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * <p>Spring MVC 配置</p>
 *
 * <h3>优化说明</h3>
 * <ul>
 *   <li><b>双拦截器设计：</b> RefreshTokenInterceptor（刷新 Token） + LoginInterceptor（鉴权），职责分离。</li>
 *   <li><b>Knife4j 文档路径：</b> 添加静态资源映射，使 /doc.html 等 Knife4j 页面不被拦截器拦截。</li>
 *   <li><b>大厂实践：</b> 拦截器按 order 排序，先执行 Token 刷新（永不拦截），再执行业务鉴权。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // token刷新拦截器（order=0 优先执行，永不拦截，只负责刷新 Redis TTL 并存用户到 ThreadLocal）
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // 登录拦截器（order=1 后执行，拦截未登录请求）
        // 白名单：验证码、登录、博客热门列表、优惠券/秒杀、商铺、上传、Knife4j 文档
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/voucher/**",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        // Knife4j API 文档相关路径（放行，方便未登录时也能查看文档）
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs/**"
                ).order(1);

        WebMvcConfigurer.super.addInterceptors(registry);
    }

    /**
     * 静态资源映射 — 放行 Knife4j 文档页面所需的 JS/CSS 等静态资源
     * <p>
     * 如果 spring.mvc.static-path-pattern 是默认值，Spring Boot 会自动处理 /webjars/**
     * 但显式配置更安全，避免升级时行为变化。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
