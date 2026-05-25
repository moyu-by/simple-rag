package org.moyu.rag.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageResponse(
        Long id,
        String role,
        String content,
        List<SearchResponse> sources,
        LocalDateTime createTime) {
}
