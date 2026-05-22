package com.example.demo.annotation;

import java.lang.annotation.*;

/**
 * 标记接口需要登录才能访问（默认行为，可省略）。
 *
 * <p>主要用于团队中显式声明意图：看到注解就知道这个接口需要登录，
 * 不加注解也一样需要登录。</p>
 *
 * <pre>{@code
 * @LoginRequired
 * @GetMapping("/me")
 * public Result<?> me() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginRequired {
}
