package org.moyu.rag.dto;

/**
 * 历史消息对，用于在 ChatRequest 中传递对话上下文。
 */
public record MessagePair(String role, String content) {
}
