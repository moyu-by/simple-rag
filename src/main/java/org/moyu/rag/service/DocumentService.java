package org.moyu.rag.service;

import org.moyu.rag.dto.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentResponse upload(Long kbId, MultipartFile file);

    /** 上传并自动触发文档处理 */
    DocumentResponse upload(Long kbId, MultipartFile file, Long embeddingConfigId);

    List<DocumentResponse> list(Long kbId);

    DocumentResponse getById(Long kbId, Long docId);

    void delete(Long kbId, Long docId);
}
