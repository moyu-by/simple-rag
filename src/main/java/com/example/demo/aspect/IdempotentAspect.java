package com.example.demo.aspect;

import com.example.demo.annotation.Idempotent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 幂等校验切面。
 *
 * <p>从请求头 {@code Idempotent-Token} 获取 token，在 Redis 中尝试 {@code DEL}：
 * <ul>
 *   <li>DEL 成功（返回 1）→ 第一次请求，放行</li>
 *   <li>DEL 失败（返回 0）→ 重复提交，抛异常</li>
 * </ul>
 * </p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String KEY_PREFIX = "idempotent:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        String token = request.getHeader("Idempotent-Token");

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("缺少幂等 Token，请在请求头携带 Idempotent-Token");
        }

        // DEL 是原子操作：key 存在 → 删掉并返回 1；不存在 → 返回 0
        Boolean deleted = redisTemplate.delete(KEY_PREFIX + token);

        if (deleted == null || !deleted) {
            log.warn("幂等拦截: token={}, uri={}", token, request.getRequestURI());
            throw new RuntimeException(idempotent.message());
        }

        return joinPoint.proceed();
    }
}
