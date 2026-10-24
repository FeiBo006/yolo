package com.yuanyu.safety.security;

import com.yuanyu.safety.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-hours:12}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String generate(User user) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("name", user.getRealName())
                .claim("role", user.getRoleCode())
                .issuedAt(now)
                .expiration(expire)
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public LoginUser toLoginUser(Claims claims) {
        Number uid = claims.get("uid", Number.class);
        return new LoginUser(
                uid == null ? null : uid.longValue(),
                claims.getSubject(),
                claims.get("name", String.class),
                claims.get("role", String.class)
        );
    }

    public long expireMillis() {
        return expireHours * 3600_000L;
    }
}
