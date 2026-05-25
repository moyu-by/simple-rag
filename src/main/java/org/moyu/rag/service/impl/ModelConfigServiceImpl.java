package org.moyu.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.ContextUtil;
import org.moyu.rag.dto.ModelConfigRequest;
import org.moyu.rag.dto.ModelConfigResponse;
import org.moyu.rag.entity.KbMembership;
import org.moyu.rag.entity.ModelConfig;
import org.moyu.rag.enums.KbRole;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.mapper.KbMembershipMapper;
import org.moyu.rag.mapper.ModelConfigMapper;
import org.moyu.rag.service.ModelConfigService;
import org.moyu.rag.utils.AesEncryptor;
import org.moyu.rag.utils.RsaUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigMapper configMapper;
    private final KbMembershipMapper membershipMapper;
    private final AesEncryptor aesEncryptor;
    private final RsaUtil rsaUtil;

    @Override
    public List<ModelConfigResponse> list(Long kbId) {
        Long userId = ContextUtil.getUserId();
        requireMembership(userId, kbId);

        List<ModelConfig> configs = configMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getKbId, kbId));

        return configs.stream().map(c -> new ModelConfigResponse(
                c.getId(), c.getKbId(), c.getName(), c.getModelType(), c.getProvider(),
                c.getBaseUrl(), maskApiKey(aesEncryptor.decrypt(c.getApiKey())), c.getModelName(),
                c.getParameters(), c.getIsActive(), c.getCreatedBy(), c.getCreateTime()
        )).toList();
    }

    @Override
    public ModelConfigResponse create(Long kbId, ModelConfigRequest request) {
        Long userId = ContextUtil.getUserId();
        requireAdmin(userId, kbId);
        validateProvider(request.modelType(), request.provider());

        ModelConfig config = new ModelConfig();
        config.setKbId(kbId);
        config.setName(request.name());
        config.setModelType(request.modelType());
        config.setProvider(request.provider());
        config.setBaseUrl(request.baseUrl());
        config.setApiKey(resolveApiKey(request));
        config.setModelName(request.modelName());
        config.setParameters(request.parameters());
        config.setIsActive(request.isActive() != null ? request.isActive() : true);
        config.setCreatedBy(userId);
        configMapper.insert(config);

        return new ModelConfigResponse(
                config.getId(), config.getKbId(), config.getName(), config.getModelType(),
                config.getProvider(), config.getBaseUrl(), maskApiKey(config.getApiKey()),
                config.getModelName(), config.getParameters(), config.getIsActive(),
                config.getCreatedBy(), config.getCreateTime()
        );
    }

    @Override
    public ModelConfigResponse update(Long kbId, Long configId, ModelConfigRequest request) {
        Long userId = ContextUtil.getUserId();
        requireAdmin(userId, kbId);
        validateProvider(request.modelType(), request.provider());

        ModelConfig config = configMapper.selectById(configId);
        if (config == null || !config.getKbId().equals(kbId)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "配置不存在");
        }

        config.setName(request.name());
        config.setModelType(request.modelType());
        config.setProvider(request.provider());
        config.setBaseUrl(request.baseUrl());
        config.setApiKey(resolveApiKey(request));
        config.setModelName(request.modelName());
        config.setParameters(request.parameters());
        config.setIsActive(request.isActive() != null ? request.isActive() : true);
        configMapper.updateById(config);

        return new ModelConfigResponse(
                config.getId(), config.getKbId(), config.getName(), config.getModelType(),
                config.getProvider(), config.getBaseUrl(), maskApiKey(config.getApiKey()),
                config.getModelName(), config.getParameters(), config.getIsActive(),
                config.getCreatedBy(), config.getCreateTime()
        );
    }

    @Override
    public void delete(Long kbId, Long configId) {
        Long userId = ContextUtil.getUserId();
        requireAdmin(userId, kbId);

        ModelConfig config = configMapper.selectById(configId);
        if (config == null || !config.getKbId().equals(kbId)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "配置不存在");
        }

        configMapper.deleteById(configId);
    }

    // ==================== 内部方法 ====================

    /** 校验 provider 和 modelType 的组合是否合法 */
    private static final Set<String> EMBEDDING_PROVIDERS = Set.of("openai", "custom");
    private static final Set<String> CHAT_PROVIDERS = Set.of("openai", "custom", "anthropic");

    private void validateProvider(String modelType, String provider) {
        Set<String> allowed = "EMBEDDING".equals(modelType) ? EMBEDDING_PROVIDERS : CHAT_PROVIDERS;
        if (!allowed.contains(provider == null ? "" : provider.toLowerCase())) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED,
                    "模型类型[" + modelType + "]不支持提供商[" + provider + "]，嵌入模型仅支持 OpenAI 兼容协议(custom)，对话模型支持 OpenAI 兼容协议(custom)和 Anthropic");
        }
    }

    /**
     * 解析传输中的 apiKey：
     * encrypted=true  → 先 RSA 私钥解密得到明文 → 再 AES 加密落库
     * encrypted=false → 直接 AES 加密落库（HTTPS 明文传输场景）
     */
    private String resolveApiKey(ModelConfigRequest request) {
        String plaintext = Boolean.TRUE.equals(request.encrypted())
                ? rsaUtil.decrypt(request.apiKey())   // RSA 密文 → 明文
                : request.apiKey();                     // 已是明文
        return aesEncryptor.encrypt(plaintext);         // 明文 → AES 密文入 DB
    }

    private void requireMembership(Long userId, Long kbId) {
        if (membershipMapper.selectCount(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, userId)
                        .eq(KbMembership::getKbId, kbId)) == 0) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "无权限访问该知识库");
        }
    }

    private void requireAdmin(Long userId, Long kbId) {
        KbMembership m = membershipMapper.selectOne(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, userId)
                        .eq(KbMembership::getKbId, kbId));
        if (m == null || !m.getRoleInKb().atLeast(KbRole.ADMIN)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "权限不足");
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 4) return "****";
        return "****" + key.substring(key.length() - 4);
    }
}
