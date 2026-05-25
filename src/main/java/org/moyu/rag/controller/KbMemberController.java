package org.moyu.rag.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.Result;
import org.moyu.rag.dto.AddMemberRequest;
import org.moyu.rag.dto.KbMemberResponse;
import org.moyu.rag.dto.UpdateMemberRoleRequest;
import org.moyu.rag.service.KbMemberService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledge-base/{kbId}/member")
@RequiredArgsConstructor
public class KbMemberController {

    private final KbMemberService memberService;

    @GetMapping
    public Result<List<KbMemberResponse>> list(@PathVariable Long kbId) {
        return Result.ok(memberService.listMembers(kbId));
    }

    @PostMapping
    public Result<Void> add(@PathVariable Long kbId, @Valid @RequestBody AddMemberRequest request) {
        memberService.addMember(kbId, request);
        return Result.ok();
    }

    @PutMapping("/{userId}")
    public Result<Void> updateRole(@PathVariable Long kbId, @PathVariable Long userId,
                                   @Valid @RequestBody UpdateMemberRoleRequest request) {
        memberService.updateMemberRole(kbId, userId, request);
        return Result.ok();
    }

    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable Long kbId, @PathVariable Long userId) {
        memberService.removeMember(kbId, userId);
        return Result.ok();
    }
}
