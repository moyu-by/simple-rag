package org.moyu.rag.service;

import org.moyu.rag.dto.ModelConfigRequest;
import org.moyu.rag.dto.ModelConfigResponse;

import java.util.List;

public interface ModelConfigService {

    List<ModelConfigResponse> list(Long kbId);

    ModelConfigResponse create(Long kbId, ModelConfigRequest request);

    ModelConfigResponse update(Long kbId, Long configId, ModelConfigRequest request);

    void delete(Long kbId, Long configId);
}
