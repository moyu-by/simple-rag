package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis 配置。
 *
 * <p>Key 统一用 String 序列化；Value / HashValue 用 {@link RedisSerializer#json()}，
 * 自动适配当前 classpath 上的 Jackson 版本（3.x），序列化时嵌入 {@code @class} 类型信息。</p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // Key 统一 String；Value / HashValue 统一 JSON（含 @class 类型信息，任意类型随便存）
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.json());
        template.setHashValueSerializer(RedisSerializer.json());

        template.setConnectionFactory(redisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
