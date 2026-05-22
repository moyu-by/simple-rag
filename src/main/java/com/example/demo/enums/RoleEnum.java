package com.example.demo.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 用户角色枚举。
 *
 * <p>数字存库，前端展示字符串，互不影响。</p>
 */
public enum RoleEnum {

    USER(0, "普通用户"),
    ADMIN(1, "管理员"),
    BOSS(2, "超级管理员");

    @EnumValue  // MyBatis-Plus 存库用这个值
    private final int code;

    @JsonValue  // Jackson 序列化输出这个值
    private final String desc;

    RoleEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RoleEnum fromCode(int code) {
        for (RoleEnum role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return USER;
    }
}
