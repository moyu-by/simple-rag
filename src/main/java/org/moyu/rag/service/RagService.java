package org.moyu.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.moyu.rag.entity.ModelConfig;
import org.moyu.rag.mapper.ModelConfigMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStoreService vectorStoreService;
    private final ModelFactory modelFactory;
    private final ModelConfigMapper modelConfigMapper;

    public record ChatResult(String answer, List<Document> chunks) {}

    /** 检索 + 构建上下文，返回 chunks 和已拼接好的 context 文本 */
    private record RetrievalResult(List<Document> chunks, String context) {}

    /** 纯检索 */
    public List<Document> search(Long kbId, String query, int topK, Long embeddingConfigId) {
        ModelConfig config = modelConfigMapper.selectById(embeddingConfigId);
        log.info("检索使用嵌入模型: name={}, modelName={}, baseUrl={}",
                config.getName(), config.getModelName(), config.getBaseUrl());
        EmbeddingModel embeddingModel = modelFactory.createEmbeddingModel(config);
        float[] queryEmbedding = embeddingModel.embed(query);
        return vectorStoreService.search(kbId, queryEmbedding, topK);
    }

    /** 检索 → 构建上下文字符串，chat / chatStream 共用 */
    private RetrievalResult retrieve(Long kbId, String query, Long embeddingConfigId, int topK) {
        List<Document> chunks = search(kbId, query, topK, embeddingConfigId);
        if (chunks.isEmpty()) {
            return new RetrievalResult(List.of(), "");
        }
        String context = chunks.stream()
                .map(Document::getText)
                .collect(java.util.stream.Collectors.joining("\n---\n"));
        return new RetrievalResult(chunks, context);
    }

    /** RAG 对话，返回答案 + 引用来源 */
    public ChatResult chat(Long kbId, String query, Long embeddingConfigId, Long chatConfigId, int topK) {
        RetrievalResult rr = retrieve(kbId, query, embeddingConfigId, topK);
        if (rr.context.isEmpty()) {
            return new ChatResult("未找到相关资料。", List.of());
        }

        ModelConfig chatConfig = modelConfigMapper.selectById(chatConfigId);
        ChatModel chatModel = modelFactory.createChatModel(chatConfig);

        var response = chatModel.call(new Prompt(List.of(
                systemMsg(rr.context),
                new UserMessage(query))));
        return new ChatResult(response.getResult().getOutput().getText(), rr.chunks);
    }

    /** RAG 对话（流式输出），先检索 → 再流式生成 */
    public Flux<String> chatStream(Long kbId, String query, Long embeddingConfigId, Long chatConfigId, int topK) {
        RetrievalResult rr = retrieve(kbId, query, embeddingConfigId, topK);
        if (rr.context.isEmpty()) {
            return Flux.just("未找到相关资料。");
        }

        ChatModel chatModel = modelFactory.createStreamingChatModel(chatConfigId);

        return chatModel.stream(new Prompt(List.of(
                systemMsg(rr.context),
                new UserMessage(query))))
                .map(r -> r.getResult().getOutput().getText());
    }

    /** 构建 SystemMessage，将检索资料注入 prompt */
    private SystemMessage systemMsg(String context) {
        return new SystemMessage("""
                你是一个知识库助手。请根据以下资料回答用户的问题。
                如果资料中没有相关信息，请如实告知，不要编造。
                
                资料：
                %s
                """.formatted(context));
    }
}
