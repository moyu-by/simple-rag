package org.moyu.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.ContextUtil;
import org.moyu.rag.dto.AddMemberRequest;
import org.moyu.rag.dto.KbMemberResponse;
import org.moyu.rag.dto.UpdateMemberRoleRequest;
import org.moyu.rag.entity.KbMembership;
import org.moyu.rag.entity.User;
import org.moyu.rag.enums.KbRole;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.mapper.KbMembershipMapper;
import org.moyu.rag.mapper.UserMapper;
import org.moyu.rag.service.KbMemberService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KbMemberServiceImpl implements KbMemberService {

    private final KbMembershipMapper membershipMapper;
    private final UserMapper userMapper;

    @Override
    public List<KbMemberResponse> listMembers(Long kbId) {
        Long userId = ContextUtil.getUserId();
        requireMembership(userId, kbId);

        List<KbMembership> memberships = membershipMapper.selectList(
                new LambdaQueryWrapper<KbMembership>().eq(KbMembership::getKbId, kbId));

        List<Long> userIds = memberships.stream().map(KbMembership::getUserId).toList();

        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return memberships.stream().map(m -> {
            User u = userMap.get(m.getUserId());
            return new KbMemberResponse(
                    m.getUserId(),
                    u != null ? u.getAccount() : "",
                    u != null ? u.getDisplayName() : "",
                    m.getRoleInKb().getDesc(),
                    m.getCreateTime());
        }).toList();
    }

    @Override
    public void addMember(Long kbId, AddMemberRequest request) {
        Long callerId = ContextUtil.getUserId();
        KbRole callerRole = requireMembership(callerId, kbId).getRoleInKb();
        KbRole targetRole = roleFromString(request.roleInKb());

        User targetUser = userMapper.selectById(request.userId());
        if (targetUser == null) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "用户不存在");
        }

        KbMembership existing = membershipMapper.selectOne(
                new LambdaQueryWrapper<KbMembership>()
                        .eq(KbMembership::getKbId, kbId)
                        .eq(KbMembership::getUserId, request.userId()));
        if (existing != null) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "该用户已在知识库中");
        }

        if (callerRole == KbRole.MEMBER) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "权限不足");
        }
        if (callerRole == KbRole.ADMIN && targetRole != KbRole.MEMBER) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "管理员只能添加群员");
        }

        KbMembership membership = new KbMembership();
        membership.setKbId(kbId);
        membership.setUserId(request.userId());
        membership.setRoleInKb(targetRole);
        membershipMapper.insert(membership);
    }

    @Override
    public void updateMemberRole(Long kbId, Long targetUserId, UpdateMemberRoleRequest request) {
        Long callerId = ContextUtil.getUserId();
        KbRole callerRole = requireMembership(callerId, kbId).getRoleInKb();
        KbRole newRole = roleFromString(request.roleInKb());

        if (callerId.equals(targetUserId)) {
            throw new AuthException(AuthException.Type.FORBIDDEN, "不能修改自己的角色");
        }

        KbMembership targetMembership = requireMembership(targetUserId, kbId);

        if (callerRole == KbRole.MEMBER) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "权限不足");
        }
        if (callerRole == KbRole.ADMIN && targetMembership.getRoleInKb() != KbRole.MEMBER) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "管理员只能修改群员的角色");
        }
        if (callerRole == KbRole.ADMIN && newRole != KbRole.MEMBER && newRole != KbRole.ADMIN) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "管理员只能设置群员或管理员");
        }

        targetMembership.setRoleInKb(newRole);
        membershipMapper.updateById(targetMembership);
    }

    @Override
    public void removeMember(Long kbId, Long targetUserId) {
        Long callerId = ContextUtil.getUserId();
        KbRole callerRole = requireMembership(callerId, kbId).getRoleInKb();

        if (callerId.equals(targetUserId)) {
            throw new AuthException(AuthException.Type.FORBIDDEN, "不能移除自己");
        }

        KbMembership targetMembership = requireMembership(targetUserId, kbId);

        if (callerRole == KbRole.MEMBER) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "权限不足");
        }
        if (callerRole == KbRole.ADMIN && targetMembership.getRoleInKb() != KbRole.MEMBER) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "管理员只能移除群员");
        }

        membershipMapper.deleteById(targetMembership.getId());
    }

    // ==================== 内部方法 ====================

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

    private KbRole roleFromString(String role) {
        try {
            return KbRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "无效的角色: " + role);
        }
    }
}
