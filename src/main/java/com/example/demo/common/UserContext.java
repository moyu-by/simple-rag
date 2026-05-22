package com.example.demo.common;

import com.example.demo.enums.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户上下文。
 * <p>由 {@link ContextUtil} 管理，请求链路内任意地方可获取当前用户。</p>
 */
@Data
@AllArgsConstructor
public class UserContext {
    private Long userId;
    private RoleEnum role;
}
