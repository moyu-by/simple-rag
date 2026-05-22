package com.example.demo.interceptor;

import com.example.demo.annotation.NoLoginRequired;
import com.example.demo.common.ContextUtil;
import com.example.demo.common.UserContext;
import com.example.demo.exception.JwtException;
import com.example.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器。
 *
 * <p>标记了 {@link NoLoginRequired} 的接口跳过 token 校验，
 * 其他接口默认需要登录。校验通过后将 {@link UserContext} 注入 {@link ContextUtil}，
 * 后续 Controller/Service 可调用 {@code ContextUtil.getUserId()} / {@code ContextUtil.getRole()} 获取。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 静态资源放行
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        // 标了 @NoLoginRequired 的接口跳过 token 校验
        if (hm.getMethodAnnotation(NoLoginRequired.class) != null) {
            return true;
        }

        // 提取 Authorization 头
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            throw new JwtException(JwtException.Type.EMPTY);
        }

        String token = authHeader.trim();
        if (!token.startsWith("Bearer ")) {
            throw new JwtException(JwtException.Type.INVALID);
        }

        // 去掉 "Bearer " 前缀得到实际 token
        token = token.substring(7);

        // 解析 token 并注入完整上下文（userId + role）
        UserContext userContext = jwtUtil.getUserContext(token);
        ContextUtil.setUser(userContext.getUserId(), userContext.getRole());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束清理 ThreadLocal，防止内存泄漏
        ContextUtil.clear();
    }
}
