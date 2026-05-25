package org.moyu.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRequest(
        @NotBlank(message = "问题不能为空") String query,
        @NotNull(message = "向量化模型配置ID不能为空") Long embeddingConfigId,
        @NotNull(message = "对话模型配置ID不能为空") Long chatConfigId,
        Integer topK) {}
