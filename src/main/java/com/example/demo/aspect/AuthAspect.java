package com.example.demo.aspect;

import com.example.demo.annotation.AdminRequired;
import com.example.demo.common.ContextUtil;
import com.example.demo.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 权限校验切面。
 *
 * <p>检查方法上的角色注解，和 {@code JwtInterceptor} 职责分离：
 * <ul>
 *   <li>JwtInterceptor — token 校验，保证已登录</li>
 *   <li>AuthAspect — 角色检查，保证有权限</li>
 * </ul>
 * </p>
 */
@Slf4j
@Aspect
@Component
public class AuthAspect {

    /**
     * 检查 {@link AdminRequired} 注解。
     * <p>当前用户不是管理员时抛 {@link AuthException}。</p>
     */
    @Before("@annotation(com.example.demo.annotation.AdminRequired)")
    public void checkAdmin() {
        if (!ContextUtil.isAdmin()) {
            log.warn("非管理员尝试访问管理接口, userId={}", ContextUtil.getUserId());
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "需要管理员权限");
        }
    }
}
