package org.moyu.rag.service;

import org.moyu.rag.dto.KbCreateRequest;
import org.moyu.rag.dto.KbResponse;
import org.moyu.rag.dto.KbUpdateRequest;

import java.util.List;

public interface KnowledgeBaseService {

    KbResponse create(KbCreateRequest request);

    List<KbResponse> list();

    KbResponse getById(Long kbId);

    KbResponse update(Long kbId, KbUpdateRequest request);

    void delete(Long kbId);
}
