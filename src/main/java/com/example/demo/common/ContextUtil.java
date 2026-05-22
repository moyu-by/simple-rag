package com.example.demo.common;

import com.example.demo.enums.RoleEnum;

/**
 * 用户上下文持有器。
 *
 * <p>基于 {@link ThreadLocal}，在请求开始时由拦截器注入，
 * 请求结束时自动清理，不影响其他请求。</p>
 *
 * <pre>{@code
 * // 在 Controller 或 Service 中获取当前用户
 * Long userId = ContextUtil.getUserId();
 * RoleEnum role = ContextUtil.getRole();
 * }</pre>
 */
public class ContextUtil {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    /** 注入当前用户 */
    public static void setUser(Long userId) {
        CONTEXT.set(new UserContext(userId, null));
    }

    /** 注入当前用户（含角色） */
    public static void setUser(Long userId, RoleEnum role) {
        CONTEXT.set(new UserContext(userId, role));
    }

    /** 获取当前用户 ID，可能为 null */
    public static Long getUserId() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    /** 获取当前用户角色，可能为 null */
    public static RoleEnum getRole() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getRole() : null;
    }

    /** 是否是管理员 */
    public static boolean isAdmin() {
        RoleEnum role = getRole();
        return role == RoleEnum.ADMIN || role == RoleEnum.BOSS;
    }

    /** 获取当前用户上下文，可能为 null */
    public static UserContext getUser() {
        return CONTEXT.get();
    }

    /** 请求结束时清理，防止内存泄漏 */
    public static void clear() {
        CONTEXT.remove();
    }
}
