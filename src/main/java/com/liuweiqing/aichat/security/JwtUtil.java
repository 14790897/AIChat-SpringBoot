package com.liuweiqing.aichat.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
public class JwtUtil {

    private static final String TOKEN_PREFIX = "jwt:token:";

    private final Algorithm algorithm;
    private final long expirationMs;
    private final StringRedisTemplate redisTemplate;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms}") long expirationMs,
                   StringRedisTemplate redisTemplate) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationMs = expirationMs;
        this.redisTemplate = redisTemplate;
    }

    public String generateToken(String username) {
        String token = JWT.create()
                .withSubject(username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMs))
                .sign(algorithm);
        // 存入 Redis，过期时间与 JWT 一致
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, username, expirationMs, TimeUnit.MILLISECONDS);
        return token;
    }

    public String validateTokenAndGetUsername(String token) {
        try {
            String username = JWT.require(algorithm).build().verify(token).getSubject();
            // 检查 Redis 中是否存在该 token（支持登出失效）
            String cached = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
            if (cached == null) {
                return null;
            }
            return username;
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    public void removeToken(String token) {
        redisTemplate.delete(TOKEN_PREFIX + token);
    }
}
