package com.student.service;

import com.student.dto.LoginRequest;
import com.student.dto.LoginResponse;
import com.student.entity.User;
import com.student.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务
 * 处理用户登录、注册、验证码验证等认证相关业务
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserService userService;
    private final CodeService codeService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${imkqas.security.jwt.expiration:86400000}")
    private Long jwtExpiration;

    /**
     * 发送登录验证码
     *
     * @param phone 手机号
     * @return 是否发送成功
     */
    public boolean sendLoginCode(String phone) {
        // 检查手机号格式（简化验证）
        if (!isValidPhone(phone)) {
            log.warn("手机号格式无效: {}", phone);
            return false;
        }

        // 检查是否可发送验证码
        if (!codeService.canSendCode(phone)) {
            log.warn("验证码发送过于频繁: {}", phone);
            return false;
        }

        // 发送验证码
        boolean sent = codeService.sendCode(phone);
        if (sent) {
            log.info("验证码发送成功: {}", phone);
        } else {
            log.warn("验证码发送失败: {}", phone);
        }

        return sent;
    }

    /**
     * 验证码登录
     *
     * @param request 登录请求
     * @return 登录响应
     */
    @Transactional
    public LoginResponse loginWithCode(LoginRequest request) {
        // 使用username作为手机号，captcha作为验证码
        String phone = request.getUsername();
        String code = request.getCaptcha();

        // 验证手机号格式
        if (!isValidPhone(phone)) {
            return LoginResponse.error("手机号格式无效");
        }

        // 验证验证码
        if (!codeService.verifyCode(phone, code)) {
            return LoginResponse.error("验证码错误或已过期");
        }

        // 查找用户，如果不存在则自动注册
        User user = userService.findByPhone(phone);
        if (user == null) {
            // 自动注册新用户
            user = User.builder()
                    .phone(phone)
                    .username(generateUsername(phone))
                    .password(passwordEncoder.encode(generateRandomPassword())) // 设置随机加密密码
                    .role(User.Role.USER)
                    .build();
            boolean saved = userService.save(user);
            if (!saved) {
                throw new RuntimeException("用户注册失败");
            }
            // 保存后重新获取用户以确保ID已设置
            user = userService.findByPhone(phone);
            if (user == null) {
                throw new RuntimeException("用户注册后查询失败");
            }
            log.info("新用户自动注册: {}", phone);
        }

        // 生成JWT令牌
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );

        // 构建登录响应
        Long expiresAt = System.currentTimeMillis() + jwtExpiration;
        return LoginResponse.success(
                token,
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getPhone(),
                user.getHealthProfile(),
                expiresAt
        );
    }

    /**
     * 验证手机号格式（简单验证）
     *
     * @param phone 手机号
     * @return 是否有效
     */
    private boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        // 简单验证：11位数字，以1开头
        return phone.matches("^1[3-9]\\d{9}$");
    }

    /**
     * 生成默认用户名（手机号后4位）
     *
     * @param phone 手机号
     * @return 默认用户名
     */
    private String generateUsername(String phone) {
        if (phone.length() >= 4) {
            return "用户" + phone.substring(phone.length() - 4);
        }
        return "用户" + phone;
    }

    /**
     * 刷新令牌
     *
     * @param oldToken 旧令牌
     * @return 新令牌，如果刷新失败返回null
     */
    public String refreshToken(String oldToken) {
        try {
            // 验证旧令牌
            if (!jwtUtil.validateToken(oldToken)) {
                return null;
            }

            // 从旧令牌中提取用户信息
            Long userId = jwtUtil.getUserIdFromToken(oldToken);
            String username = jwtUtil.getUsernameFromToken(oldToken);
            String role = jwtUtil.getRoleFromToken(oldToken);

            // 验证用户是否存在
            User user = userService.getById(userId);
            if (user == null) {
                return null;
            }

            // 生成新令牌
            return jwtUtil.generateToken(userId, username, role);
        } catch (Exception e) {
            log.error("刷新令牌失败", e);
            return null;
        }
    }

    /**
     * 登出（客户端应删除本地存储的令牌）
     * 服务端可考虑将令牌加入黑名单（需要Redis支持）
     *
     * @param token 令牌
     * @return 是否成功
     */
    public boolean logout(String token) {
        // 简化实现：服务端无状态，客户端删除令牌即可
        // 如有需要，可将令牌加入黑名单（需要Redis）
        log.info("用户登出: token={}", token);
        return true;
    }

    /**
     * 生成随机密码
     * 用于自动注册用户的默认密码
     *
     * @return 随机密码字符串
     */
    private String generateRandomPassword() {
        // 生成16位随机字符串作为密码
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            password.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return password.toString();
    }

    /**
     * 验证令牌是否有效
     *
     * @param token 令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }

    /**
     * 从令牌中获取用户ID
     *
     * @param token 令牌
     * @return 用户ID，无效时返回null
     */
    public Long getUserIdFromToken(String token) {
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.error("从令牌获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 从令牌中获取用户角色
     *
     * @param token 令牌
     * @return 用户角色，无效时返回null
     */
    public String getRoleFromToken(String token) {
        try {
            return jwtUtil.getRoleFromToken(token);
        } catch (Exception e) {
            log.error("从令牌获取用户角色失败", e);
            return null;
        }
    }
}