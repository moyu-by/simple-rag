package org.moyu.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 知识库内部角色。
 *
 * <p>在某个具体知识库内，成员的角色决定能做什么：</p>
 * <ul>
 *   <li>BOSS(2) — 库主，全部权限：管理文档/模型/成员、解散知识库</li>
 *   <li>ADMIN(1) — 管理员：管理文档/模型/群员（不能动其他管理员和BOSS）</li>
 *   <li>MEMBER(0) — 群员：只能查询使用</li>
 * </ul>
 */
public enum KbRole {

    MEMBER(0, "群员"),
    ADMIN(1, "管理员"),
    BOSS(2, "库主");

    @EnumValue
    private final int code;

    @JsonValue
    private final String desc;

    KbRole(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static KbRole fromCode(int code) {
        for (KbRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return MEMBER;
    }

    /** 是否有该角色的管理权限？ */
    public boolean atLeast(KbRole target) {
        return this.code >= target.code;
    }
}
