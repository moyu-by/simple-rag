package org.moyu.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SearchRequestDto(
        @NotBlank(message = "查询内容不能为空") String query,
        @NotNull(message = "向量化模型配置ID不能为空") Long embeddingConfigId,
        @NotNull(message = "返回条数不能为空") Integer topK) {}
