package org.moyu.rag.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.Result;
import org.moyu.rag.dto.ModelConfigRequest;
import org.moyu.rag.dto.ModelConfigResponse;
import org.moyu.rag.service.ModelConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledge-base/{kbId}/model-config")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService configService;

    @GetMapping
    public Result<List<ModelConfigResponse>> list(@PathVariable Long kbId) {
        return Result.ok(configService.list(kbId));
    }

    @PostMapping
    public Result<ModelConfigResponse> create(@PathVariable Long kbId,
                                              @Valid @RequestBody ModelConfigRequest request) {
        return Result.ok(configService.create(kbId, request));
    }

    @PutMapping("/{configId}")
    public Result<ModelConfigResponse> update(@PathVariable Long kbId,
                                              @PathVariable Long configId,
                                              @Valid @RequestBody ModelConfigRequest request) {
        return Result.ok(configService.update(kbId, configId, request));
    }

    @DeleteMapping("/{configId}")
    public Result<Void> delete(@PathVariable Long kbId, @PathVariable Long configId) {
        configService.delete(kbId, configId);
        return Result.ok();
    }
}
