package com.example.demo.annotation;

import java.lang.annotation.*;

/**
 * 幂等校验注解。
 *
 * <p>防止接口重复提交。配合 {@code IdempotentAspect} 使用，
 * 基于 Token + Redis DEL 原子操作实现。</p>
 *
 * <pre>{@code
 * @Idempotent
 * @PostMapping("/submit-order")
 * public Result<Void> submit(@Valid @RequestBody OrderReq req) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 重复提交时的提示消息 */
    String message() default "请勿重复提交";

    /** Token 过期时间（秒） */
    int timeout() default 300;
}
