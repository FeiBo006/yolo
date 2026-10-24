package com.yuanyu.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuanyu.safety.common.BizException;
import com.yuanyu.safety.common.PageResult;
import com.yuanyu.safety.entity.User;
import com.yuanyu.safety.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<User> page(String keyword, long page, long size) {
        var wrapper = Wrappers.<User>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getUsername, keyword).or()
                        .like(User::getRealName, keyword))
                .orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    public User create(User user) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BizException("用户名不能为空");
        }
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, user.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }
        user.setId(null);
        user.setPassword(passwordEncoder.encode(
                StringUtils.hasText(user.getPassword()) ? user.getPassword() : "123456"));
        user.setRoleCode(StringUtils.hasText(user.getRoleCode()) ? user.getRoleCode() : "INSPECTOR");
        user.setStatus(user.getStatus() == null ? 1 : user.getStatus());
        userMapper.insert(user);
        user.setPassword(null);
        return user;
    }

    public User update(Long id, User input) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        user.setRealName(input.getRealName());
        user.setPhone(input.getPhone());
        if (StringUtils.hasText(input.getRoleCode())) {
            user.setRoleCode(input.getRoleCode());
        }
        if (input.getStatus() != null) {
            user.setStatus(input.getStatus());
        }
        if (StringUtils.hasText(input.getPassword())) {
            user.setPassword(passwordEncoder.encode(input.getPassword()));
        }
        userMapper.updateById(user);
        user.setPassword(null);
        return user;
    }

    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if ("admin".equals(user.getUsername())) {
            throw new BizException("内置管理员账号不允许删除");
        }
        userMapper.deleteById(id);
    }
}
