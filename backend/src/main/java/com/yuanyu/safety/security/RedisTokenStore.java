package com.yuanyu.safety.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis Token 黑名单，生产环境启用（profile=redis）。
 */
@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisTokenStore implements TokenStore {

    private static final String PREFIX = "yuanyu:jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String token, long ttlMillis) {
        try {
            redisTemplate.opsForValue().set(PREFIX + token, "1",
                    Duration.ofMillis(Math.max(ttlMillis, 1000)));
        } catch (Exception e) {
            log.warn("写入 Redis Token 黑名单失败，降级忽略：{}", e.getMessage());
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
        } catch (Exception e) {
            log.warn("读取 Redis Token 黑名单失败，按未拉黑处理：{}", e.getMessage());
            return false;
        }
    }
}
