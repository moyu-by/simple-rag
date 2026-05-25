package org.moyu.rag.dto;

import java.time.LocalDateTime;

public record KbMemberResponse(
        Long userId,
        String account,
        String displayName,
        String roleInKb,
        LocalDateTime joinTime) {}
