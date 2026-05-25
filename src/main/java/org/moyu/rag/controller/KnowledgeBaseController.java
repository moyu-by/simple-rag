package org.moyu.rag.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.Result;
import org.moyu.rag.dto.KbCreateRequest;
import org.moyu.rag.dto.KbResponse;
import org.moyu.rag.dto.KbUpdateRequest;
import org.moyu.rag.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @PostMapping
    public Result<KbResponse> create(@Valid @RequestBody KbCreateRequest request) {
        return Result.ok(kbService.create(request));
    }

    @GetMapping
    public Result<List<KbResponse>> list() {
        return Result.ok(kbService.list());
    }

    @GetMapping("/{id}")
    public Result<KbResponse> get(@PathVariable Long id) {
        return Result.ok(kbService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<KbResponse> update(@PathVariable Long id, @RequestBody KbUpdateRequest request) {
        return Result.ok(kbService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbService.delete(id);
        return Result.ok();
    }
}
