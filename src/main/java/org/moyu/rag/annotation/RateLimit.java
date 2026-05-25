package org.moyu.rag.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解（令牌桶算法）。
 *
 * <p>打在需要限流的 Controller 方法上，配合 {@code RateLimitAspect} 使用。</p>
 *
 * <pre>{@code
 * @RateLimit(ratePerSecond = 5, maxCapacity = 10)
 * @PostMapping("/send-code")
 * public Result<Void> sendCode(@RequestParam String phone) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 每秒令牌生成速率 */
    int ratePerSecond() default 10;

    /** 令牌桶最大容量（突发峰值） */
    int maxCapacity() default 20;

    /** 被限流时的提示消息 */
    String message() default "请求过于频繁，请稍后再试";
}
