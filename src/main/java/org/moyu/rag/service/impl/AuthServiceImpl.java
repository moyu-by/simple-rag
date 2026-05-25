package org.moyu.rag.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.moyu.rag.dto.LoginRequest;
import org.moyu.rag.dto.LoginResponse;
import org.moyu.rag.dto.RegisterRequest;
import org.moyu.rag.dto.RegisterResponse;
import org.moyu.rag.entity.User;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.mapper.UserMapper;
import org.moyu.rag.service.AuthService;
import org.moyu.rag.utils.JwtUtil;
import org.moyu.rag.utils.RsaUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RsaUtil rsaUtil;
    private final JwtUtil jwtUtil;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        String account = request.account();

        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getAccount, account)) > 0) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "账号已存在");
        }

        String rawPassword = rsaUtil.decrypt(request.password());
        String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());

        User user = new User();
        user.setAccount(account);
        user.setPasswordHash(hash);
        user.setDisplayName(account);
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId());
        return new RegisterResponse(user.getId(), token);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getAccount, request.account()));

        if (user == null) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "账号或密码错误");
        }

        String rawPassword = rsaUtil.decrypt(request.password());
        if (!BCrypt.checkpw(rawPassword, user.getPasswordHash())) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "账号或密码错误");
        }

        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .set(User::getLastLoginAt, LocalDateTime.now())
                        .eq(User::getId, user.getId()));

        String token = jwtUtil.generateToken(user.getId());
        return new LoginResponse(user.getId(), token);
    }
}
