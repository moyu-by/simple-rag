package org.moyu.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProcessRequest(
        @NotNull(message = "向量化模型配置ID不能为空") Long embeddingConfigId) {}
