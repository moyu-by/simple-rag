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

    private static final Set<String> EMBEDDING_PROVIDERS = Set.of("openai", "custom");
    private static final Set<String> CHAT_PROVIDERS = Set.of("openai", "custom", "anthropic");
    private static final Set<String> VALID_COMPAT_TYPES = Set.of("openai", "anthropic");

    private final ModelConfigMapper configMapper;
    private final KbMembershipMapper membershipMapper;
    private final AesEncryptor aesEncryptor;
    private final RsaUtil rsaUtil;

    @Override
    public List<ModelConfigResponse> list(Long kbId) {
        requireMembership(kbId);
        return configMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getKbId, kbId)
        ).stream().map(this::toResponse).toList();
    }

    @Override
    public ModelConfigResponse create(Long kbId, ModelConfigRequest request) {
        requireAdmin(kbId);
        validateRequest(request);

        ModelConfig config = new ModelConfig();
        config.setKbId(kbId);
        applyRequest(config, request);
        config.setCreatedBy(ContextUtil.getUserId());
        configMapper.insert(config);

        return toResponse(config);
    }

    @Override
    public ModelConfigResponse update(Long kbId, Long configId, ModelConfigRequest request) {
        requireAdmin(kbId);
        validateRequest(request);

        ModelConfig config = getAndCheckOwnership(kbId, configId);
        applyRequest(config, request);
        configMapper.updateById(config);

        return toResponse(config);
    }

    @Override
    public void delete(Long kbId, Long configId) {
        requireAdmin(kbId);
        getAndCheckOwnership(kbId, configId);
        configMapper.deleteById(configId);
    }

    // ==================== 映射 ====================

    /** Request → Entity（create 和 update 共用） */
    private void applyRequest(ModelConfig config, ModelConfigRequest request) {
        config.setName(request.name());
        config.setModelType(request.modelType());
        config.setProvider(request.provider());
        config.setCompatType(resolveCompatType(request));
        config.setBaseUrl(request.baseUrl());
        config.setApiKey(resolveApiKey(request));
        config.setModelName(request.modelName());
        config.setParameters(request.parameters());
        config.setIsActive(request.isActive() != null && request.isActive());
    }

    /** Entity → Response */
    private ModelConfigResponse toResponse(ModelConfig c) {
        return new ModelConfigResponse(
                c.getId(), c.getKbId(), c.getName(), c.getModelType(),
                c.getProvider(), c.getCompatType(), c.getBaseUrl(),
                maskApiKey(aesEncryptor.decrypt(c.getApiKey())),
                c.getModelName(), c.getParameters(), c.getIsActive(),
                c.getCreatedBy(), c.getCreateTime()
        );
    }

    // ==================== 校验 ====================

    private void validateRequest(ModelConfigRequest request) {
        // 校验 provider + modelType 组合
        String provider = safe(request.provider());
        Set<String> allowed = "EMBEDDING".equals(request.modelType()) ? EMBEDDING_PROVIDERS : CHAT_PROVIDERS;
        if (!allowed.contains(provider)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED,
                    "模型类型[" + request.modelType() + "]不支持提供商[" + provider + "]");
        }

        // 校验 compatType（仅 provider=custom 时有意义）
        String compatType = safe(request.compatType());
        if ("custom".equals(provider) && !VALID_COMPAT_TYPES.contains(compatType)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED,
                    "provider=custom 时兼容协议必须为 openai 或 anthropic");
        }
    }

    // ==================== 内部方法 ====================

    /** 非 custom 时 compatType 无意义，归一化为 null；custom 时默认 openai */
    private String resolveCompatType(ModelConfigRequest request) {
        if (!"custom".equals(safe(request.provider()))) return null;
        String ct = safe(request.compatType());
        return ct.isEmpty() ? "openai" : ct;
    }

    private ModelConfig getAndCheckOwnership(Long kbId, Long configId) {
        ModelConfig config = configMapper.selectById(configId);
        if (config == null || !config.getKbId().equals(kbId)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "配置不存在");
        }
        return config;
    }

    private String resolveApiKey(ModelConfigRequest request) {
        String plaintext = Boolean.TRUE.equals(request.encrypted())
                ? rsaUtil.decrypt(request.apiKey())
                : request.apiKey();
        return aesEncryptor.encrypt(plaintext);
    }

    private void requireMembership(Long kbId) {
        if (membershipMapper.selectCount(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, ContextUtil.getUserId())
                        .eq(KbMembership::getKbId, kbId)) == 0) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "无权限访问该知识库");
        }
    }

    private void requireAdmin(Long kbId) {
        KbMembership m = membershipMapper.selectOne(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, ContextUtil.getUserId())
                        .eq(KbMembership::getKbId, kbId));
        if (m == null || !m.getRoleInKb().atLeast(KbRole.ADMIN)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "权限不足");
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 4) return "****";
        return "****" + key.substring(key.length() - 4);
    }

    private String safe(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
