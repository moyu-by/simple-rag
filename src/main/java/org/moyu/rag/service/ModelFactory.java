package org.moyu.rag.service;

import com.anthropic.client.AnthropicClient;
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
 * <p>根据 model_config 表的 {@code provider} 字段动态构建不同提供商的模型实例，
 * 支持不同知识库使用不同的 API key 和 base_url。</p>
 *
 * <h3>支持列表</h3>
 * <table>
 *   <tr><th>provider</th><th>对话</th><th>嵌入</th><th>说明</th></tr>
 *   <tr><td>openai / custom</td><td>✅</td><td>✅</td><td>OpenAI 兼容协议（OneAPI、硅基流动、vLLM 等）</td></tr>
 *   <tr><td>anthropic</td><td>✅</td><td>❌</td><td>Anthropic Claude（仅对话，无嵌入模型）</td></tr>
 *   <tr><td>ollama</td><td>需加依赖</td><td>需加依赖</td><td>本地部署，见下方扩展说明</td></tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
public class ModelFactory {

    private final ModelConfigMapper modelConfigMapper;
    private final AesEncryptor aesEncryptor;

    // ==================== 嵌入模型 ====================

    public EmbeddingModel createEmbeddingModel(Long configId) {
        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) throw new IllegalArgumentException("模型配置不存在: " + configId);
        return createEmbeddingModel(config);
    }

    public EmbeddingModel createEmbeddingModel(ModelConfig config) {
        String provider = safeProvider(config.getProvider());
        return switch (provider) {
            case "openai", "custom" -> openAiEmbedding(config);
            default -> throw new IllegalArgumentException(
                    "不支持的嵌入模型提供商: " + provider + "（支持 OpenAI 兼容协议）");
        };
    }

    // ==================== 对话模型 ====================

    public ChatModel createChatModel(Long configId) {
        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) throw new IllegalArgumentException("模型配置不存在: " + configId);
        return createChatModel(config);
    }

    public ChatModel createChatModel(ModelConfig config) {
        String provider = safeProvider(config.getProvider());
        return switch (provider) {
            case "openai", "custom" -> openAiChat(config);
            case "anthropic" -> anthropicChat(config);
            default -> throw new IllegalArgumentException("不支持的对话模型提供商: " + provider);
        };
    }

    /** 构建流式聊天模型 */
    public ChatModel createStreamingChatModel(Long configId) {
        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) throw new IllegalArgumentException("模型配置不存在: " + configId);
        return createStreamingChatModel(config);
    }

    public ChatModel createStreamingChatModel(ModelConfig config) {
        String provider = safeProvider(config.getProvider());
        return switch (provider) {
            case "openai", "custom" -> openAiChat(config);
            case "anthropic" -> anthropicChat(config);
            default -> throw new IllegalArgumentException("不支持的流式对话提供商: " + provider);
        };
    }

    // ==================== 内部构建方法 ====================

    private OpenAiEmbeddingModel openAiEmbedding(ModelConfig config) {
        var apiKey = aesEncryptor.decrypt(config.getApiKey());
        var baseUrl = emptyToDefault(config.getBaseUrl(), "https://api.openai.com");
        var client = OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        var options = OpenAiEmbeddingOptions.builder()
                .model(config.getModelName())
                .build();
        return new OpenAiEmbeddingModel(client, MetadataMode.EMBED, options);
    }

    private OpenAiChatModel openAiChat(ModelConfig config) {
        var apiKey = aesEncryptor.decrypt(config.getApiKey());
        var baseUrl = emptyToDefault(config.getBaseUrl(), "https://api.openai.com");
        var client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey).baseUrl(baseUrl).build();
        var asyncClient = OpenAIOkHttpClientAsync.builder()
                .apiKey(apiKey).baseUrl(baseUrl).build();
        var options = OpenAiChatOptions.builder()
                .model(config.getModelName()).build();
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

    private String emptyToDefault(String url, String defaultUrl) {
        return (url == null || url.isBlank()) ? defaultUrl : url;
    }

    private String safeProvider(String provider) {
        return provider == null ? "openai" : provider.toLowerCase();
    }
}
