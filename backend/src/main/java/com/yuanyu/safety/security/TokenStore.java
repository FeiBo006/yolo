package com.yuanyu.safety.security;

/**
 * Token 黑名单抽象：默认内存实现，生产可替换为 Redis。
 */
public interface TokenStore {

    void blacklist(String token, long ttlMillis);

    boolean isBlacklisted(String token);
}
