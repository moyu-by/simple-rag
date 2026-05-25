package org.moyu.rag.interceptor;

import org.moyu.rag.annotation.NoLoginRequired;
import org.moyu.rag.common.ContextUtil;
import org.moyu.rag.exception.JwtException;
import org.moyu.rag.utils.JwtUtil;
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
 * 其他接口默认需要登录。校验通过后将 userId 注入 {@link ContextUtil}。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        if (hm.getMethodAnnotation(NoLoginRequired.class) != null) {
            return true;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            throw new JwtException(JwtException.Type.EMPTY);
        }

        String token = authHeader.trim();
        if (!token.startsWith("Bearer ")) {
            throw new JwtException(JwtException.Type.INVALID);
        }

        token = token.substring(7);

        Long userId = jwtUtil.getUserId(token);
        ContextUtil.setUser(userId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ContextUtil.clear();
    }
}
