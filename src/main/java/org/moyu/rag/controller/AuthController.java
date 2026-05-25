package org.moyu.rag.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.annotation.NoLoginRequired;
import org.moyu.rag.common.Result;
import org.moyu.rag.dto.LoginRequest;
import org.moyu.rag.dto.LoginResponse;
import org.moyu.rag.dto.PublicKeyResponse;
import org.moyu.rag.dto.RegisterRequest;
import org.moyu.rag.dto.RegisterResponse;
import org.moyu.rag.service.AuthService;
import org.moyu.rag.utils.RsaUtil;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RsaUtil rsaUtil;
    private final AuthService authService;

    @GetMapping("/public-key")
    @NoLoginRequired
    public Result<PublicKeyResponse> publicKey() {
        return Result.ok(new PublicKeyResponse(rsaUtil.getPublicKey()));
    }

    @PostMapping("/register")
    @NoLoginRequired
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    @NoLoginRequired
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }
}
