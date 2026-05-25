package org.moyu.rag.common;

/**
 * 用户上下文持有器。
 *
 * <p>基于 {@link ThreadLocal}，在请求开始时由拦截器注入 userId，
 * 请求结束时自动清理。</p>
 *
 * <pre>{@code
 * Long userId = ContextUtil.getUserId();
 * }</pre>
 */
public class ContextUtil {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    /** 注入当前用户 ID */
    public static void setUser(Long userId) {
        CONTEXT.set(new UserContext(userId));
    }

    /** 获取当前用户 ID，可能为 null */
    public static Long getUserId() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    /** 请求结束时清理，防止内存泄漏 */
    public static void clear() {
        CONTEXT.remove();
    }
}
