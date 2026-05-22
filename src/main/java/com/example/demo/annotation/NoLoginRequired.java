package com.example.demo.annotation;

import java.lang.annotation.*;

/**
 * 标记接口不需要登录即可访问。
 *
 * <p>不加此注解默认需要登录验证，加了则 {@code JwtInterceptor} 跳过 token 校验。</p>
 *
 * <pre>{@code
 * @NoLoginRequired
 * @PostMapping("/login")
 * public Result<?> login(@Valid @RequestBody LoginReq req) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoLoginRequired {
}
