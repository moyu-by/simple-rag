package org.moyu.rag.dto;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long kbId,
        String fileName,
        String fileUrl,
        Long fileSize,
        String fileType,
        Integer status,
        Integer chunkCount,
        Long uploadedBy,
        LocalDateTime createTime) {}
