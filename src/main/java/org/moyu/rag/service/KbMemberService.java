package org.moyu.rag.service;

import org.moyu.rag.dto.AddMemberRequest;
import org.moyu.rag.dto.KbMemberResponse;
import org.moyu.rag.dto.UpdateMemberRoleRequest;

import java.util.List;

public interface KbMemberService {

    List<KbMemberResponse> listMembers(Long kbId);

    void addMember(Long kbId, AddMemberRequest request);

    void updateMemberRole(Long kbId, Long targetUserId, UpdateMemberRoleRequest request);

    void removeMember(Long kbId, Long targetUserId);
}
