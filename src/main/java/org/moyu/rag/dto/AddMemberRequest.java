package org.moyu.rag.dto;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
        @NotNull(message = "用户ID不能为空") Long userId,
        @NotNull(message = "角色不能为空") String roleInKb) {}
