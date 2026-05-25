package org.moyu.rag.controller;

import org.moyu.rag.annotation.NoLoginRequired;
import org.moyu.rag.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 幂等 Token 获取接口。
 *
 * <p>前端调用此接口获取唯一 token，提交表单时放在请求头
 * {@code Idempotent-Token} 中，配合 {@code @Idempotent} 注解防重复提交。</p>
 */
@RestController
@RequestMapping("/idempotent")
@RequiredArgsConstructor
public class TokenController {

    private static final String KEY_PREFIX = "idempotent:";

    private final RedisTemplate<String, Object> redisTemplate;

    @NoLoginRequired
    @GetMapping("/token")
    public Result<String> getToken() {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", 300, TimeUnit.SECONDS);
        return Result.ok(token);
    }
}
