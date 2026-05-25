package org.moyu.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.ContextUtil;
import org.moyu.rag.dto.KbCreateRequest;
import org.moyu.rag.dto.KbResponse;
import org.moyu.rag.dto.KbUpdateRequest;
import org.moyu.rag.entity.Document;
import org.moyu.rag.entity.KbMembership;
import org.moyu.rag.entity.KnowledgeBase;
import org.moyu.rag.entity.ModelConfig;
import org.moyu.rag.enums.KbRole;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.mapper.DocumentMapper;
import org.moyu.rag.mapper.KbMembershipMapper;
import org.moyu.rag.mapper.KnowledgeBaseMapper;
import org.moyu.rag.mapper.ModelConfigMapper;
import org.moyu.rag.service.KnowledgeBaseService;
import org.moyu.rag.service.VectorStoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final KbMembershipMapper membershipMapper;
    private final DocumentMapper documentMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final VectorStoreService vectorStoreService;

    @Override
    @Transactional
    public KbResponse create(KbCreateRequest request) {
        Long userId = ContextUtil.getUserId();

        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(request.name());
        kb.setDescription(request.description());
        kb.setOwnerId(userId);
        kb.setStatus(1);
        kbMapper.insert(kb);

        KbMembership membership = new KbMembership();
        membership.setKbId(kb.getId());
        membership.setUserId(userId);
        membership.setRoleInKb(KbRole.BOSS);
        membershipMapper.insert(membership);

        return toResponse(kb, KbRole.BOSS);
    }

    @Override
    public List<KbResponse> list() {
        Long userId = ContextUtil.getUserId();

        List<KbMembership> memberships = membershipMapper.selectList(
                new LambdaQueryWrapper<KbMembership>().eq(KbMembership::getUserId, userId));

        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> kbIds = memberships.stream().map(KbMembership::getKbId).toList();

        List<KnowledgeBase> kbs = kbMapper.selectBatchIds(kbIds);

        Map<Long, KbRole> roleMap = memberships.stream()
                .collect(Collectors.toMap(KbMembership::getKbId, KbMembership::getRoleInKb));

        return kbs.stream()
                .map(kb -> toResponse(kb, roleMap.get(kb.getId())))
                .toList();
    }

    @Override
    public KbResponse getById(Long kbId) {
        Long userId = ContextUtil.getUserId();
        KbMembership membership = requireMembership(userId, kbId);
        KnowledgeBase kb = kbMapper.selectById(kbId);
        return toResponse(kb, membership.getRoleInKb());
    }

    @Override
    public KbResponse update(Long kbId, KbUpdateRequest request) {
        Long userId = ContextUtil.getUserId();
        KbMembership membership = requireMembership(userId, kbId);
        requireAtLeast(KbRole.ADMIN, membership);

        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (request.name() != null) {
            kb.setName(request.name());
        }
        if (request.description() != null) {
            kb.setDescription(request.description());
        }
        kbMapper.updateById(kb);

        return toResponse(kb, membership.getRoleInKb());
    }

    @Override
    @Transactional
    public void delete(Long kbId) {
        Long userId = ContextUtil.getUserId();
        KbMembership membership = requireMembership(userId, kbId);
        requireAtLeast(KbRole.BOSS, membership);

        documentMapper.delete(new QueryWrapper<Document>().eq("kb_id", kbId));
        modelConfigMapper.delete(new QueryWrapper<ModelConfig>().eq("kb_id", kbId));
        membershipMapper.delete(new QueryWrapper<KbMembership>().eq("kb_id", kbId));
        vectorStoreService.deleteByKbId(kbId);
        kbMapper.deleteById(kbId);
    }

    // ==================== 内部方法 ====================

    private KbResponse toResponse(KnowledgeBase kb, KbRole myRole) {
        return new KbResponse(
                kb.getId(), kb.getName(), kb.getDescription(),
                kb.getOwnerId(), myRole.getDesc(), kb.getCreateTime());
    }

    private KbMembership requireMembership(Long userId, Long kbId) {
        KbMembership m = membershipMapper.selectOne(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getUserId, userId)
                        .eq(KbMembership::getKbId, kbId));
        if (m == null) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "无权限访问该知识库");
        }
        return m;
    }

    private void requireAtLeast(KbRole required, KbMembership membership) {
        if (!membership.getRoleInKb().atLeast(required)) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "权限不足");
        }
    }
}
