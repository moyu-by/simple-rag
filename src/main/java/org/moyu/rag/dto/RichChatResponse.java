package org.moyu.rag.dto;

import java.util.List;

public record RichChatResponse(String answer, List<SearchResponse> sources) {}
