package org.moyu.rag.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.common.ContextUtil;
import org.moyu.rag.common.Result;
import org.moyu.rag.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/display-name")
    public Result<Void> updateDisplayName(@Valid @RequestBody UpdateDisplayNameRequest request) {
        Long userId = ContextUtil.getUserId();
        userService.updateDisplayName(userId, request.displayName());
        return Result.ok();
    }

    public record UpdateDisplayNameRequest(
            @NotBlank(message = "显示名不能为空") String displayName) {}
}
