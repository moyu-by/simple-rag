package org.moyu.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.ContextUtil;
import org.moyu.rag.common.Result;
import org.moyu.rag.dto.*;
import org.moyu.rag.entity.KbMembership;
import org.moyu.rag.enums.KbRole;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.mapper.KbMembershipMapper;
import org.moyu.rag.service.DocumentProcessor;
import org.moyu.rag.service.RagService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/knowledge-base/{kbId}")
@RequiredArgsConstructor
public class RagController {

    private final DocumentProcessor documentProcessor;
    private final RagService ragService;
    private final KbMembershipMapper membershipMapper;

    /** 触发文档处理：admin+ 权限 */
    @PostMapping("/document/{docId}/process")
    public Result<Void> process(@PathVariable Long kbId, @PathVariable Long docId,
                                @Valid @RequestBody ProcessRequest request) {
        requireAdmin(kbId);
        documentProcessor.process(kbId, docId, request.embeddingConfigId());
        return Result.ok();
    }

    /** 纯检索：任意成员 */
    @PostMapping("/search")
    public Result<List<SearchResponse>> search(@PathVariable Long kbId,
                                                @Valid @RequestBody SearchRequestDto request) {
        requireMembership(kbId);
        int topK = request.topK() != null ? request.topK() : 5;
        List<Document> results = ragService.search(kbId, request.query(), topK, request.embeddingConfigId());
        List<SearchResponse> list = results.stream()
                .map(d -> new SearchResponse(d.getText(), d.getMetadata()))
                .toList();
        return Result.ok(list);
    }

    /** RAG 对话：任意成员 */
    @PostMapping("/chat")
    public Result<RichChatResponse> chat(@PathVariable Long kbId,
                                          @Valid @RequestBody ChatRequest request) {
        requireMembership(kbId);
        int topK = request.topK() != null ? request.topK() : 3;
        RagService.ChatResult result = ragService.chat(kbId, request.query(),
                request.embeddingConfigId(), request.chatConfigId(), topK);
        List<SearchResponse> sources = result.chunks().stream()
                .map(d -> new SearchResponse(d.getText(), d.getMetadata()))
                .toList();
        return Result.ok(new RichChatResponse(result.answer(), sources));
    }

    /** RAG 对话（流式 SSE） */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@PathVariable Long kbId,
                                  @Valid @RequestBody ChatRequest request) {
        requireMembership(kbId);
        int topK = request.topK() != null ? request.topK() : 3;

        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        Flux<String> flux = ragService.chatStream(kbId, request.query(),
                request.embeddingConfigId(), request.chatConfigId(), topK);

        flux.subscribe(
                token -> {
                    try {
                        emitter.send(SseEmitter.event().data(token));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );

        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    private void requireMembership(Long kbId) {
        Long userId = ContextUtil.getUserId();
        if (membershipMapper.selectCount(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, userId)
                        .eq(KbMembership::getKbId, kbId)) == 0) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "无权限访问该知识库");
        }
    }

    private void requireAdmin(Long kbId) {
        Long userId = ContextUtil.getUserId();
        KbMembership m = membershipMapper.selectOne(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, userId)
                        .eq(KbMembership::getKbId, kbId));
        if (m == null || !m.getRoleInKb().atLeast(KbRole.ADMIN)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "权限不足");
        }
    }
}
