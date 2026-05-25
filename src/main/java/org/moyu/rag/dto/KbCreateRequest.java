package org.moyu.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record KbCreateRequest(
        @NotBlank(message = "知识库名称不能为空") String name,
        String description) {}
