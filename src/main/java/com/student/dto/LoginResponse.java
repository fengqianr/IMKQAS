package com.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 *
 * @author 系统
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT令牌
     */
    private String token;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户角色
     */
    private String role;

    /**
     * 用户手机号
     */
    private String phone;

    /**
     * 健康档案（简化版）
     */
    private String healthProfile;

    /**
     * 令牌过期时间（毫秒时间戳）
     */
    private Long expiresAt;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 成功登录响应
     * @param token JWT令牌
     * @param userId 用户ID
     * @param username 用户名
     * @param role 用户角色
     * @param phone 手机号
     * @param healthProfile 健康档案
     * @param expiresAt 过期时间
     * @return 登录响应
     */
    public static LoginResponse success(String token, Long userId, String username,
                                       String role, String phone, String healthProfile,
                                       Long expiresAt) {
        return new LoginResponse(token, userId, username, role, phone, healthProfile,
                expiresAt, "登录成功");
    }

    /**
     * 失败登录响应
     * @param message 错误消息
     * @return 登录响应
     */
    public static LoginResponse error(String message) {
        return new LoginResponse(null, null, null, null, null, null,
                null, message);
    }
}