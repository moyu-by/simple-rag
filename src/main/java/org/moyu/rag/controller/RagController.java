package org.moyu.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.ContextUtil;
import org.moyu.rag.common.Result;
import org.moyu.rag.dto.*;
import org.moyu.rag.entity.ChatMessage;
import org.moyu.rag.entity.KbMembership;
import org.moyu.rag.enums.KbRole;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.mapper.ChatMessageMapper;
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
    private final ChatMessageMapper chatMessageMapper;

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
                request.embeddingConfigId(), request.chatConfigId(), topK, request.history());
        List<SearchResponse> sources = result.chunks().stream()
                .map(d -> new SearchResponse(d.getText(), d.getMetadata()))
                .toList();
        return Result.ok(new RichChatResponse(result.answer(), sources));
    }

    /** 获取聊天历史 */
    @GetMapping("/chat/history")
    public Result<List<ChatMessageResponse>> history(@PathVariable Long kbId) {
        requireMembership(kbId);
        List<ChatMessage> list = chatMessageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("kb_id", kbId)
                        .orderByDesc("create_time")
                        .last("LIMIT 50"));
        // 反转按时间正序返回
        java.util.Collections.reverse(list);
        List<ChatMessageResponse> result = list.stream()
                .map(m -> new ChatMessageResponse(
                        m.getId(), m.getRole(), m.getContent(),
                        ragService.parseSources(m.getSources()),
                        m.getCreateTime()))
                .toList();
        return Result.ok(result);
    }

    /** 保存聊天消息 */
    @PostMapping("/chat/message")
    public Result<Void> saveMessage(@PathVariable Long kbId,
                                     @Valid @RequestBody SaveMessageRequest request) {
        requireMembership(kbId);
        ChatMessage msg = new ChatMessage();
        msg.setKbId(kbId);
        msg.setUserId(ContextUtil.getUserId());
        msg.setRole(request.role());
        msg.setContent(request.content());
        msg.setSources(request.sourcesJson());
        chatMessageMapper.insert(msg);
        return Result.ok();
    }

    /** RAG 对话（流式 SSE） */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@PathVariable Long kbId,
                                  @Valid @RequestBody ChatRequest request) {
        requireMembership(kbId);
        int topK = request.topK() != null ? request.topK() : 3;

        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        Flux<String> flux = ragService.chatStream(kbId, request.query(),
                request.embeddingConfigId(), request.chatConfigId(), topK, request.history());

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
