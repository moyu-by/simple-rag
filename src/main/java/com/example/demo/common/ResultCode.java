package com.example.demo.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务码定义。
 *
 * <p>与 HTTP 状态码保持一致，方便前端统一处理。
 * <table>
 *   <tr><th>码</th><th>含义</th></tr>
 *   <tr><td>200</td><td>成功</td></tr>
 *   <tr><td>400</td><td>参数错误 / 业务校验失败</td></tr>
 *   <tr><td>401</td><td>未认证 / token 无效或过期</td></tr>
 *   <tr><td>403</td><td>无权限</td></tr>
 *   <tr><td>404</td><td>资源不存在</td></tr>
 *   <tr><td>429</td><td>请求过于频繁（限流）</td></tr>
 *   <tr><td>500</td><td>服务器内部错误</td></tr>
 * </table>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 token 已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;
}
