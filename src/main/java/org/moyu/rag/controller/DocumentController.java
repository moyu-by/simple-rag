package org.moyu.rag.controller;

import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.Result;
import org.moyu.rag.dto.DocumentResponse;
import org.moyu.rag.service.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/knowledge-base/{kbId}/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public Result<DocumentResponse> upload(@PathVariable Long kbId,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) Long embeddingConfigId) {
        if (embeddingConfigId != null) {
            return Result.ok(documentService.upload(kbId, file, embeddingConfigId));
        }
        return Result.ok(documentService.upload(kbId, file));
    }

    @GetMapping
    public Result<List<DocumentResponse>> list(@PathVariable Long kbId) {
        return Result.ok(documentService.list(kbId));
    }

    @GetMapping("/{docId}")
    public Result<DocumentResponse> get(@PathVariable Long kbId, @PathVariable Long docId) {
        return Result.ok(documentService.getById(kbId, docId));
    }

    @DeleteMapping("/{docId}")
    public Result<Void> delete(@PathVariable Long kbId, @PathVariable Long docId) {
        documentService.delete(kbId, docId);
        return Result.ok();
    }
}
