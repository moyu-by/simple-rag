package com.example.demo.annotation;

import java.lang.annotation.*;

/**
 * 标记接口需要管理员权限才能访问。
 *
 * <p>配合 {@code AuthAspect} 使用，自动检查当前用户角色是否为管理员。
 * 默认已隐含登录要求。</p>
 *
 * <pre>{@code
 * @AdminRequired
 * @DeleteMapping("/user/{id}")
 * public Result<Void> deleteUser(@PathVariable Long id) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminRequired {
}
