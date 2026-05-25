package org.moyu.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 模型配置请求。
 *
 * <p>如果前端用 RSA 公钥加密了 apiKey，设置 {@code encrypted = true}，
 * 后端会用 RSA 私钥先解密再 AES 加密落库。</p>
 */
public record ModelConfigRequest(
        @NotBlank(message = "配置名称不能为空") String name,
        @NotBlank(message = "模型类型不能为空") String modelType,
        @NotBlank(message = "提供商不能为空") String provider,
        String baseUrl,
        @NotBlank(message = "API密钥不能为空") String apiKey,
        @NotBlank(message = "模型名称不能为空") String modelName,
        Object parameters,
        Boolean isActive,
        /** apiKey 是否经过 RSA 公钥加密。true 时后端先 RSA 解密再 AES 落库 */
        Boolean encrypted) {}
