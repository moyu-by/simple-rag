package org.moyu.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveMessageRequest(
        @NotBlank String role,
        @NotBlank String content,
        String sourcesJson) {
}
