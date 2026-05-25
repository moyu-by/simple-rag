package org.moyu.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.moyu.rag.entity.User;
import org.moyu.rag.exception.AuthException;
import org.moyu.rag.mapper.UserMapper;
import org.moyu.rag.service.UserService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public void updateDisplayName(Long userId, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "显示名不能为空");
        }
        if (displayName.length() > 100) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "显示名不能超过100个字符");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AuthException(AuthException.Type.UNAUTHORIZED, "用户不存在");
        }
        user.setDisplayName(displayName.trim());
        userMapper.updateById(user);
    }
}
