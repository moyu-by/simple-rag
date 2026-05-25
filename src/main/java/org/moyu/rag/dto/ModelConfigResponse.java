package org.moyu.rag.dto;

import java.time.LocalDateTime;

public record ModelConfigResponse(
        Long id,
        Long kbId,
        String name,
        String modelType,
        String provider,
        String baseUrl,
        String apiKey,
        String modelName,
        Object parameters,
        Boolean isActive,
        Long createdBy,
        LocalDateTime createTime) {}
