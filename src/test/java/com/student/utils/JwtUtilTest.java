package com.student.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT工具类单元测试
 * 测试JwtUtil的各种方法
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    private JwtUtil jwtUtil;

    private final String secret = "test-secret-key-for-jwt-unit-test-2026";
    private final Long expiration = 3600000L; // 1小时
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);

        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testGenerateToken() {
        // 准备
        Long userId = 1L;
        String username = "testuser";
        String role = "USER";

        // 执行
        String token = jwtUtil.generateToken(userId, username, role);

        // 验证
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // 验证令牌内容
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(userId, claims.get("userId", Long.class));
        assertEquals(username, claims.get("username", String.class));
        assertEquals(role, claims.get("role", String.class));
        assertEquals(username, claims.getSubject());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());

        // 验证过期时间
        Date expectedExpiration = new Date(claims.getIssuedAt().getTime() + expiration);
        assertEquals(expectedExpiration.getTime(), claims.getExpiration().getTime(), 1000); // 允许1秒误差
    }

    @Test
    void testGetUserIdFromToken() {
        // 准备
        Long userId = 1L;
        String username = "testuser";
        String role = "USER";
        String token = jwtUtil.generateToken(userId, username, role);

        // 执行
        Long result = jwtUtil.getUserIdFromToken(token);

        // 验证
        assertEquals(userId, result);
    }

    @Test
    void testGetUsernameFromToken() {
        // 准备
        Long userId = 1L;
        String username = "testuser";
        String role = "USER";
        String token = jwtUtil.generateToken(userId, username, role);

        // 执行
        String result = jwtUtil.getUsernameFromToken(token);

        // 验证
        assertEquals(username, result);
    }

    @Test
    void testGetRoleFromToken() {
        // 准备
        Long userId = 1L;
        String username = "testuser";
        String role = "USER";
        String token = jwtUtil.generateToken(userId, username, role);

        // 执行
        String result = jwtUtil.getRoleFromToken(token);

        // 验证
        assertEquals(role, result);
    }

    @Test
    void testValidateToken_ValidToken() {
        // 准备
        String token = jwtUtil.generateToken(1L, "testuser", "USER");

        // 执行
        boolean result = jwtUtil.validateToken(token);

        // 验证
        assertTrue(result);
    }

    @Test
    void testValidateToken_InvalidToken() {
        // 准备
        String invalidToken = "invalid.jwt.token";

        // 执行
        boolean result = jwtUtil.validateToken(invalidToken);

        // 验证
        assertFalse(result);
    }

    @Test
    void testValidateToken_ExpiredToken() throws InterruptedException {
        // 准备：创建短期有效的令牌
        ReflectionTestUtils.setField(jwtUtil, "expiration", 1L); // 1毫秒
        String token = jwtUtil.generateToken(1L, "testuser", "USER");

        // 等待令牌过期
        Thread.sleep(10);

        // 执行
        boolean result = jwtUtil.validateToken(token);

        // 验证
        assertFalse(result);

        // 恢复默认过期时间
        ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);
    }

    @Test
    void testGetExpirationFromToken() {
        // 准备
        String token = jwtUtil.generateToken(1L, "testuser", "USER");

        // 执行
        Date expirationDate = jwtUtil.getExpirationFromToken(token);

        // 验证
        assertNotNull(expirationDate);
        assertTrue(expirationDate.after(new Date()));
    }

    @Test
    void testIsTokenExpiringSoon_NotExpiring() {
        // 准备
        String token = jwtUtil.generateToken(1L, "testuser", "USER");
        long threshold = 60000L; // 1分钟

        // 执行
        boolean result = jwtUtil.isTokenExpiringSoon(token, threshold);

        // 验证
        assertFalse(result);
    }

    @Test
    void testIsTokenExpiringSoon_ExpiringSoon() {
        // 准备：创建短期有效的令牌
        ReflectionTestUtils.setField(jwtUtil, "expiration", 1000L); // 1秒
        String token = jwtUtil.generateToken(1L, "testuser", "USER");
        long threshold = 5000L; // 5秒（大于剩余时间）

        // 执行
        boolean result = jwtUtil.isTokenExpiringSoon(token, threshold);

        // 验证
        assertTrue(result);

        // 恢复默认过期时间
        ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);
    }

    @Test
    void testParseToken_ValidToken() {
        // 准备
        String token = jwtUtil.generateToken(1L, "testuser", "USER");

        // 执行
        Claims claims = jwtUtil.parseToken(token);

        // 验证
        assertNotNull(claims);
        assertEquals(1L, claims.get("userId", Long.class));
        assertEquals("testuser", claims.get("username", String.class));
        assertEquals("USER", claims.get("role", String.class));
    }

    @Test
    void testParseToken_InvalidToken() {
        // 准备
        String invalidToken = "invalid.jwt.token";

        // 执行和验证
        Exception exception = assertThrows(RuntimeException.class, () -> {
            jwtUtil.parseToken(invalidToken);
        });

        // 验证异常消息
        assertTrue(exception.getMessage().contains("无效令牌") ||
                   exception.getMessage().contains("令牌已过期") ||
                   exception.getCause() != null);
    }

    @Test
    void testParseToken_ExpiredToken() {
        // 准备：创建短期有效的令牌
        ReflectionTestUtils.setField(jwtUtil, "expiration", 1L); // 1毫秒
        String token = jwtUtil.generateToken(1L, "testuser", "USER");

        // 等待令牌过期
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // 忽略
        }

        // 执行和验证
        Exception exception = assertThrows(RuntimeException.class, () -> {
            jwtUtil.parseToken(token);
        });

        // 验证异常消息
        assertTrue(exception.getMessage().contains("令牌已过期") ||
                   exception.getCause() instanceof ExpiredJwtException);

        // 恢复默认过期时间
        ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);
    }
}