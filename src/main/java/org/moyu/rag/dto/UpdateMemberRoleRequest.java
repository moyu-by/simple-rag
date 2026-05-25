package org.moyu.rag.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "角色不能为空") String roleInKb) {}
