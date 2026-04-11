package com.student.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 用于生成和解析JWT令牌
 *
 * @author 系统
 * @version 1.0
 */
@Component
public class JwtUtil {

    @Value("${imkqas.security.jwt.secret:imkqas-medical-rag-secret-key-2026}")
    private String secret;

    @Value("${imkqas.security.jwt.expiration:86400000}")
    private Long expiration; // 默认24小时，单位毫秒

    private SecretKey getSigningKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * 生成JWT令牌
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param role 用户角色
     * @return JWT令牌字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从令牌中解析用户ID
     *
     * @param token JWT令牌
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从令牌中解析用户名
     *
     * @param token JWT令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从令牌中解析用户角色
     *
     * @param token JWT令牌
     * @return 用户角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 解析JWT令牌
     *
     * @param token JWT令牌
     * @return 令牌声明
     */
    @SuppressWarnings("all")
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .parseClaimsJws(token)
                    .getBody();
        } catch (RuntimeException e) {
            // 检查是否为令牌过期异常
            if (e instanceof ExpiredJwtException) {
                throw new RuntimeException("令牌已过期", e);
            }
            // 检查是否为JWT异常
            if (e instanceof JwtException) {
                throw new RuntimeException("无效令牌", e);
            }
            // 其他运行时异常
            throw e;
        }
    }

    /**
     * 验证令牌是否有效
     *
     * @param token JWT令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取令牌过期时间
     *
     * @param token JWT令牌
     * @return 过期时间
     */
    public Date getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 判断令牌是否即将过期（剩余时间小于指定阈值）
     *
     * @param token JWT令牌
     * @param thresholdMillis 阈值（毫秒）
     * @return 是否即将过期
     */
    public boolean isTokenExpiringSoon(String token, long thresholdMillis) {
        Date expiration = getExpirationFromToken(token);
        long timeLeft = expiration.getTime() - System.currentTimeMillis();
        return timeLeft < thresholdMillis;
    }
}