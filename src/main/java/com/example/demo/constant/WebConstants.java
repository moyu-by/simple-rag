package com.example.demo.constant;

/**
 * Web 层通用常量。
 */
public final class WebConstants {
    private WebConstants() {}

    /** 请求中存储当前用户 ID 的 Key */
    public static final String USER_ID_KEY = "userId";

    /** JWT 认证排除路径 */
    public static final String[] EXCLUDE_AUTH_PATHS = {
            "/auth/**",
            "/swagger-ui/**",
            "/v3/**",
            "/error"
    };
}
