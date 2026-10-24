package com.yuanyu.safety.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Token 黑名单，适合单机开发环境。
 */
@Component
@Profile("!redis")
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, Long> expiredAt = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String token, long ttlMillis) {
        expiredAt.put(token, System.currentTimeMillis() + Math.max(ttlMillis, 1000));
        cleanExpired();
    }

    @Override
    public boolean isBlacklisted(String token) {
        Long expire = expiredAt.get(token);
        if (expire == null) {
            return false;
        }
        if (expire < System.currentTimeMillis()) {
            expiredAt.remove(token);
            return false;
        }
        return true;
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        expiredAt.entrySet().removeIf(e -> e.getValue() < now);
    }
}
