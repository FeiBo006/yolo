package com.yuanyu.safety.security;

/**
 * 登录用户上下文。
 */
public record LoginUser(Long id, String username, String realName, String roleCode) {
}
