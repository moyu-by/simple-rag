package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Jackson 3 全局配置。
 *
 * <p>Jackson 3 的 {@link ObjectMapper} 不可变，通过 {@code rebuild()} 返回
 * {@code MapperBuilder} 进行定制，再 {@code build()} 出新实例。</p>
 */
@Configuration
public class JacksonConfig {

    private final ObjectMapper objectMapper;

    public JacksonConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        var module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);

        return objectMapper.rebuild()
                .defaultTimeZone(TimeZone.getTimeZone("GMT+8"))
                .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .addModule(module)
                .build();
    }
}
