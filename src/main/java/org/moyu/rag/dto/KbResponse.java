package org.moyu.rag.dto;

import java.time.LocalDateTime;

public record KbResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        String myRole,
        LocalDateTime createTime) {}
