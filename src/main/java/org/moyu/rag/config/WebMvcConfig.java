package org.moyu.rag.config;

import org.moyu.rag.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>注册拦截器、CORS、静态资源映射等公共配置。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final FileProperties fileProperties;

    public WebMvcConfig(JwtInterceptor jwtInterceptor, FileProperties fileProperties) {
        this.jwtInterceptor = jwtInterceptor;
        this.fileProperties = fileProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 文件上传后的静态资源映射
        // 访问 http://localhost:8080/file/xxx.jpg → 映射到本地 storePath 目录
        registry.addResourceHandler("/" + fileProperties.getSubPath() + "/**")
                .addResourceLocations("file:" + fileProperties.getStorePath() + "/");
    }
}
