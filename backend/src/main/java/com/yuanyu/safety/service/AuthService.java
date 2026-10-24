package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuanyu.safety.common.BizException;
import com.yuanyu.safety.dto.LoginRequest;
import com.yuanyu.safety.entity.User;
import com.yuanyu.safety.mapper.UserMapper;
import com.yuanyu.safety.security.JwtUtil;
import com.yuanyu.safety.security.LoginUser;
import com.yuanyu.safety.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    public Map<String, Object> login(LoginRequest request) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(400, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用，请联系管理员");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwtUtil.generate(user));
        result.put("user", user);
        return result;
    }

    public User currentUser(LoginUser loginUser) {
        User user = userMapper.selectById(loginUser.id());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return user;
    }

    public void logout(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenStore.blacklist(authorization.substring(7).trim(), jwtUtil.expireMillis());
        }
    }
}
