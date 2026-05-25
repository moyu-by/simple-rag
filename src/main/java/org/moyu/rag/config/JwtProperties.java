package org.moyu.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性。
 *
 * <p>对应 {@code application.yml} 中的 {@code jwt.*} 配置项。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥（HMAC-SHA） */
    private String secretKey;

    /** token 过期时间（毫秒），默认 7 天 */
    private long ttl = 7 * 24 * 60 * 60 * 1000L;
}
