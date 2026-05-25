package org.moyu.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.moyu.rag.config.FileProperties;
import org.moyu.rag.entity.Document;
import org.moyu.rag.entity.ModelConfig;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.mapper.DocumentMapper;
import org.moyu.rag.mapper.ModelConfigMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessor {

    private final DocumentMapper documentMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final VectorStoreService vectorStoreService;
    private final ModelFactory modelFactory;
    private final FileProperties fileProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 处理文档：解析 → 切块 → 向量化 → 存向量库。
     * 整个流程在一个事务内，失败时在新事务中标记 status=2。
     */
    public void process(Long kbId, Long docId, Long embeddingConfigId) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "文档不存在");
        }
        ModelConfig embedConfig = modelConfigMapper.selectById(embeddingConfigId);
        if (embedConfig == null || !embedConfig.getKbId().equals(kbId)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "模型配置不存在");
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    doProcess(kbId, doc, embedConfig);
                } catch (Exception e) {
                    status.setRollbackOnly();
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            log.error("文档 {} 处理失败", docId, e);
            markFailed(docId);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    private void doProcess(Long kbId, Document doc, ModelConfig embedConfig) throws Exception {
        Long docId = doc.getId();

        // 1. 解析
        Path filePath = Path.of(fileProperties.getStorePath(), doc.getFilePath());
        Tika tika = new Tika();
        String rawText;
        try (InputStream is = Files.newInputStream(filePath)) {
            rawText = tika.parseToString(is);
        }
        log.info("文档 {} 解析完成，文本长度: {}", docId, rawText.length());
        if (rawText.isBlank()) {
            doc.setStatus(2);
            documentMapper.updateById(doc);
            return;
        }

        // 2. 切块
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(100)
                .build();
        List<org.springframework.ai.document.Document> chunks = splitter
                .apply(List.of(new org.springframework.ai.document.Document(rawText)));

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("kb_id", kbId);
            chunks.get(i).getMetadata().put("doc_id", docId);
            chunks.get(i).getMetadata().put("chunk_index", i);
        }

        // 3. 向量化
        log.info("使用嵌入模型: name={}, modelName={}, provider={}, baseUrl={}",
                embedConfig.getName(), embedConfig.getModelName(),
                embedConfig.getProvider(), embedConfig.getBaseUrl());
        EmbeddingModel embeddingModel = modelFactory.createEmbeddingModel(embedConfig);
        List<float[]> embeddings = new ArrayList<>();
        for (org.springframework.ai.document.Document chunk : chunks) {
            float[] embedding = embeddingModel.embed(chunk.getText());
            embeddings.add(embedding);
        }

        // 4. 存入向量库
        vectorStoreService.store(chunks, embeddings);
        log.info("文档 {} 向量化完成，chunk 数: {}", docId, chunks.size());

        // 5. 更新状态
        doc.setStatus(1);
        doc.setChunkCount(chunks.size());
        documentMapper.updateById(doc);
    }

    /** 在独立事务中标记失败，不受主事务回滚影响 */
    private void markFailed(Long docId) {
        transactionTemplate.executeWithoutResult(status -> {
            Document doc = documentMapper.selectById(docId);
            if (doc != null) {
                doc.setStatus(2);
                documentMapper.updateById(doc);
            }
        });
    }
}
