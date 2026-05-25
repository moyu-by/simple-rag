package org.moyu.rag.service;

import org.moyu.rag.dto.LoginRequest;
import org.moyu.rag.dto.LoginResponse;
import org.moyu.rag.dto.RegisterRequest;
import org.moyu.rag.dto.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
