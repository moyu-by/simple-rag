package com.example.demo.aspect;

import com.example.demo.annotation.RateLimit;
import com.example.demo.common.ContextUtil;
import com.example.demo.exception.RateLimitException;
import cn.hutool.extra.servlet.JakartaServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 接口限流切面。
 *
 * <p>使用令牌桶算法，基于 Redis + Lua 实现原子操作。
 * 登录用户按 {@code userId} 限流，未登录按 {@code ip} 限流。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(com.example.demo.annotation.RateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);

        String key = buildKey();
        boolean allowed = tryAcquire(key, rateLimit.ratePerSecond(), rateLimit.maxCapacity());

        if (!allowed) {
            log.warn("RateLimit exceeded: key={}, rate={}, capacity={}", key,
                    rateLimit.ratePerSecond(), rateLimit.maxCapacity());
            throw new RateLimitException(rateLimit.message());
        }

        return joinPoint.proceed();
    }

    /**
     * 构建限流 key。
     * <p>已登录 → {@code rate:user:{userId}:{path}}</p>
     * <p>未登录 → {@code rate:ip:{ip}:{path}}</p>
     */
    private String buildKey() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        assert attrs != null;
        HttpServletRequest request = attrs.getRequest();

        Long userId = ContextUtil.getUserId();
        String path = request.getRequestURI();

        if (userId != null) {
            return "rate:user:" + userId + ":" + path;
        }
        String ip = JakartaServletUtil.getClientIP(request);
        return "rate:ip:" + ip + ":" + path;
    }

    /**
     * 令牌桶获取尝试（原子操作，Lua 脚本实现）。
     *
     * @param key           限流 key
     * @param ratePerSecond 每秒生成令牌数
     * @param maxCapacity   桶最大容量
     * @return true 获取成功，false 被限流
     */
    private boolean tryAcquire(String key, int ratePerSecond, int maxCapacity) {
        String script = """
                local key = KEYS[1]
                local capacity = tonumber(ARGV[1])
                local rate = tonumber(ARGV[2])
                local now = tonumber(ARGV[3])
                local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time')
                local current_tokens = tonumber(bucket[1]) or capacity
                local last_refill_time = tonumber(bucket[2]) or now
                local token_will_fill = (now - last_refill_time) / 1000 * rate
                current_tokens = math.min(token_will_fill + current_tokens, capacity)
                local allow = 0
                if current_tokens >= 1 then
                    current_tokens = current_tokens - 1
                    allow = 1
                end
                redis.call('HSET', key, 'tokens', current_tokens, 'last_refill_time', now)
                redis.call('EXPIRE', key, 300)
                return allow
                """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(
                redisScript,
                List.of(key),
                String.valueOf(maxCapacity),
                String.valueOf(ratePerSecond),
                String.valueOf(System.currentTimeMillis())
        );

        return result != null && result == 1L;
    }
}
