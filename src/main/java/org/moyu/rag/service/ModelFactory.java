package org.moyu.rag.service;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.entity.ModelConfig;
import org.moyu.rag.mapper.ModelConfigMapper;
import org.moyu.rag.utils.AesEncryptor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Service;

/**
 * 运行时模型工厂。
 *
 * <p>根据 model_config 表的 {@code provider} + {@code compatType} 动态构建模型实例。</p>
 *
 * <h3>路由规则</h3>
 * <pre>
 * provider=openai     → OpenAI SDK，base_url 默认 api.openai.com
 * provider=anthropic  → Anthropic SDK（仅对话，无嵌入）
 * provider=custom     → 由 compatType 决定用哪个 SDK，用户必须提供 base_url
 *   ├─ compatType=openai    → OpenAI SDK
 *   └─ compatType=anthropic → Anthropic SDK
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ModelFactory {

    private final ModelConfigMapper modelConfigMapper;
    private final AesEncryptor aesEncryptor;

    // ==================== 嵌入模型 ====================

    public EmbeddingModel createEmbeddingModel(Long configId) {
        return createEmbeddingModel(requireConfig(configId));
    }

    public EmbeddingModel createEmbeddingModel(ModelConfig config) {
        String sdk = resolveSdk(config);
        if ("anthropic".equals(sdk)) {
            throw new IllegalArgumentException("Anthropic 不提供嵌入模型，请使用 OpenAI 兼容协议");
        }
        return openAiEmbedding(config);
    }

    // ==================== 对话模型 ====================

    public ChatModel createChatModel(Long configId) {
        return createChatModel(requireConfig(configId));
    }

    public ChatModel createChatModel(ModelConfig config) {
        return buildChatModel(config);
    }

    public ChatModel createStreamingChatModel(Long configId) {
        return createStreamingChatModel(requireConfig(configId));
    }

    public ChatModel createStreamingChatModel(ModelConfig config) {
        return buildChatModel(config);
    }

    // ==================== 核心路由 ====================

    /**
     * 统一决定用哪个 SDK：
     * - provider=openai    → "openai"
     * - provider=anthropic → "anthropic"
     * - provider=custom    → 看 compatType（openai/anthropic）
     */
    private String resolveSdk(ModelConfig config) {
        String provider = safe(config.getProvider());
        return switch (provider) {
            case "openai" -> "openai";
            case "anthropic" -> "anthropic";
            case "custom" -> {
                String compatType = safe(config.getCompatType());
                if (compatType.isEmpty() || "openai".equals(compatType)) yield "openai";
                if ("anthropic".equals(compatType)) yield "anthropic";
                throw new IllegalArgumentException(
                        "provider=custom 时 compatType 必须为 openai 或 anthropic，当前值: " + config.getCompatType());
            }
            default -> throw new IllegalArgumentException("不支持的 provider: " + provider);
        };
    }

    private ChatModel buildChatModel(ModelConfig config) {
        return "anthropic".equals(resolveSdk(config))
                ? anthropicChat(config)
                : openAiChat(config);
    }

    // ==================== SDK 构建 ====================

    private OpenAiEmbeddingModel openAiEmbedding(ModelConfig config) {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(aesEncryptor.decrypt(config.getApiKey()))
                .baseUrl(baseUrlOrDefault(config, "https://api.openai.com"))
                .build();
        var options = OpenAiEmbeddingOptions.builder()
                .model(config.getModelName())
                .build();
        return new OpenAiEmbeddingModel(client, MetadataMode.EMBED, options);
    }

    private OpenAiChatModel openAiChat(ModelConfig config) {
        var apiKey = aesEncryptor.decrypt(config.getApiKey());
        var baseUrl = baseUrlOrDefault(config, "https://api.openai.com");
        var client = OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        var asyncClient = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        var options = OpenAiChatOptions.builder().model(config.getModelName()).build();
        return OpenAiChatModel.builder()
                .openAiClient(client).openAiClientAsync(asyncClient).options(options).build();
    }

    private AnthropicChatModel anthropicChat(ModelConfig config) {
        var client = AnthropicOkHttpClient.builder()
                .apiKey(aesEncryptor.decrypt(config.getApiKey()))
                .build();
        var model = com.anthropic.models.messages.Model.of(config.getModelName());
        var options = AnthropicChatOptions.builder().model(model).build();
        return AnthropicChatModel.builder().anthropicClient(client).options(options).build();
    }

    // ==================== 工具 ====================

    private ModelConfig requireConfig(Long configId) {
        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) throw new IllegalArgumentException("模型配置不存在: " + configId);
        return config;
    }

    /**
     * openai/anthropic provider → 没填就用官方默认地址
     * custom provider → 必须填 base_url
     */
    private String baseUrlOrDefault(ModelConfig config, String defaultUrl) {
        String url = config.getBaseUrl();
        if (url != null && !url.isBlank()) return url;
        if ("custom".equals(safe(config.getProvider()))) {
            throw new IllegalArgumentException("provider=custom 时必须填写 API 地址");
        }
        return defaultUrl;
    }

    private String safe(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
