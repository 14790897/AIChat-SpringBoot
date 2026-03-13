package com.liuweiqing.aichat.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtUtil jwtUtil;

    private static final String SECRET = "test-secret-key-for-unit-testing-only-1234567890";
    private static final long EXPIRATION_MS = 86400000L;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS, redisTemplate);
    }

    @Test
    void generateToken_returnsValidToken() {
        String token = jwtUtil.generateToken("testuser");

        assertNotNull(token);
        assertFalse(token.isBlank());
        verify(valueOperations).set(eq("jwt:token:" + token), eq("testuser"), eq(EXPIRATION_MS), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void validateToken_validToken_returnsUsername() {
        String token = jwtUtil.generateToken("testuser");
        when(valueOperations.get("jwt:token:" + token)).thenReturn("testuser");

        String username = jwtUtil.validateTokenAndGetUsername(token);

        assertEquals("testuser", username);
    }

    @Test
    void validateToken_removedFromRedis_returnsNull() {
        String token = jwtUtil.generateToken("testuser");
        when(valueOperations.get("jwt:token:" + token)).thenReturn(null);

        String username = jwtUtil.validateTokenAndGetUsername(token);

        assertNull(username);
    }

    @Test
    void validateToken_invalidToken_returnsNull() {
        String username = jwtUtil.validateTokenAndGetUsername("invalid.token.here");

        assertNull(username);
    }

    @Test
    void validateToken_expiredToken_returnsNull() {
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        String expiredToken = JWT.create()
                .withSubject("testuser")
                .withIssuedAt(new Date(System.currentTimeMillis() - 200000))
                .withExpiresAt(new Date(System.currentTimeMillis() - 100000))
                .sign(algorithm);

        String username = jwtUtil.validateTokenAndGetUsername(expiredToken);

        assertNull(username);
    }

    @Test
    void removeToken_deletesFromRedis() {
        jwtUtil.removeToken("some_token");

        verify(redisTemplate).delete("jwt:token:some_token");
    }
}
