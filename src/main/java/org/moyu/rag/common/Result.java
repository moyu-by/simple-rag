package org.moyu.rag.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应体。
 * <p>所有接口统一返回该类型，前端根据 {@code code} 判断业务成功/失败。</p>
 *
 * <pre>{@code
 * // 成功
 * return Result.ok(data);
 * // 失败
 * return Result.fail(400, "参数错误");
 * }</pre>
 *
 * @param <T> data 类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 业务状态码，200 表示成功 */
    private int code = 200;

    /** 提示信息 */
    private String message = "success";

    /** 业务数据 */
    private T data;

    // ========== 静态工厂 ==========

    /** 成功（无返回数据） */
    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    /** 成功（带返回数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /** 失败（默认 500） */
    public static <T> Result<T> fail() {
        return fail(500, "fail");
    }

    /** 失败（自定义消息，状态码 500） */
    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    /** 失败（自定义状态码和消息） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ========== 便捷判读 ==========

    /** 是否业务成功 */
    public boolean isSuccess() {
        return code == 200;
    }
}
