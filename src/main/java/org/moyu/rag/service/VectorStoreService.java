package org.moyu.rag.service;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * pgvector 向量存储服务。使用 JdbcTemplate 直接操作 vector_store 表，
 * 支持按 kb_id 过滤检索，不依赖 Spring AI 的 PgVectorStore。
 */
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final JdbcTemplate jdbcTemplate;

    /** 存储已向量化的文档块 */
    public void store(List<Document> chunks, List<float[]> embeddings) {
        String sql = """
                INSERT INTO vector_store (content, metadata, embedding)
                VALUES (?, ?::jsonb, ?::vector)
                """;
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String metadataJson = metadataToJson(chunk.getMetadata());
            batch.add(new Object[]{chunk.getText(), metadataJson, embeddings.get(i)});
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    /** 向量相似度检索 */
    public List<Document> search(Long kbId, float[] queryEmbedding, int topK) {
        String sql = """
                SELECT content, metadata, 1 - (embedding <=> ?::vector) AS similarity
                FROM vector_store
                WHERE metadata->>'kb_id' = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setObject(1, queryEmbedding);
                    ps.setObject(2, String.valueOf(kbId));
                    ps.setObject(3, queryEmbedding);
                    ps.setInt(4, topK);
                },
                (rs, rowNum) -> {
                    Document doc = new Document(rs.getString("content"));
                    doc.getMetadata().put("kb_id", kbId);
                    doc.getMetadata().put("score", rs.getDouble("similarity"));
                    return doc;
                });
    }

    /** 删除知识库下的所有向量 */
    public void deleteByKbId(Long kbId) {
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'kb_id' = ?", String.valueOf(kbId));
    }

    private String metadataToJson(Map<String, Object> meta) {
        return JSONUtil.toJsonStr(meta);
    }
}
