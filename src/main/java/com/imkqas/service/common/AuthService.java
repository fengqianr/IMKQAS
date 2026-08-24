package com.imkqas.service.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imkqas.dto.LoginRequest;
import com.imkqas.dto.LoginResponse;
import com.imkqas.dto.RegisterRequest;
import com.imkqas.dto.user.IdentityResponse;
import com.imkqas.entity.User;
import com.imkqas.exception.BusinessException;
import com.imkqas.service.his.FhirPatientService;
import com.imkqas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

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
    private final FhirPatientService fhirPatientService;
    private final ObjectMapper objectMapper;

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
     * 用户名密码登录
     *
     * @param request 登录请求
     * @return 登录响应
     */
    @Transactional
    public LoginResponse loginWithCode(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.trim().isEmpty()) {
            return LoginResponse.error("用户名不能为空");
        }

        if (password == null || password.trim().isEmpty()) {
            return LoginResponse.error("密码不能为空");
        }

        // 尝试查找用户：先按用户名查找，再按手机号查找
        User user = userService.findByUsername(username);
        if (user == null && isValidPhone(username)) {
            // 如果输入的是手机号格式，尝试按手机号查找
            user = userService.findByPhone(username);
        }

        if (user == null) {
            return LoginResponse.error("用户不存在");
        }

        // 验证密码（注册用户为 BCrypt 编码，兼容历史 MD5 种子账号）
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return LoginResponse.error("密码错误");
        }

        // 生成JWT令牌
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );

        // 构建登录响应（真实姓名从身份信息 identity 解析，刷新令牌沿用 JWT）
        Long expiresAt = System.currentTimeMillis() + jwtExpiration;
        return LoginResponse.success(
                token,
                token,
                user.getId(),
                user.getUsername(),
                resolveName(user),
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

    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册结果，成功返回true，失败返回false
     */
    @Transactional
    public boolean register(RegisterRequest request) {
        log.info("用户注册请求: username={}, phone={}", request.getUsername(), request.getPhone());

        // 角色白名单校验：医生/管理员账号仅能由管理员创建，禁止自助注册
        User.Role role = request.getRole() != null ? request.getRole() : User.Role.PATIENT;
        if (role == User.Role.DOCTOR || role == User.Role.ADMIN) {
            log.warn("自助注册被拒绝: username={}, role={}", request.getUsername(), role);
            throw new BusinessException("医生/管理员账号需由管理员在用户管理中创建");
        }

        // 检查用户名是否已存在
        if (userService.isUsernameExists(request.getUsername())) {
            log.warn("用户名已存在: {}", request.getUsername());
            return false;
        }

        // 检查手机号是否已注册
        if (userService.isPhoneRegistered(request.getPhone())) {
            log.warn("手机号已注册: {}", request.getPhone());
            return false;
        }

        // 验证密码和确认密码是否匹配
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("密码和确认密码不匹配");
            return false;
        }

        // 创建用户：真实姓名同步写入身份信息 identity，供用户中心回显
        IdentityResponse identity = new IdentityResponse();
        identity.setName(request.getName());
        String identityJson;
        try {
            identityJson = objectMapper.writeValueAsString(identity);
        } catch (JsonProcessingException e) {
            log.error("序列化身份信息失败: username={}", request.getUsername(), e);
            throw new BusinessException("注册失败，请稍后重试");
        }
        User user = User.builder()
                .username(request.getUsername())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .identity(identityJson)
                .build();

        boolean saved = userService.save(user);
        if (saved) {
            log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
            // 建用户后同步建立 FHIR 患者缓存（fhir_id=pat-{id}），注册后即可被医生端检索；
            // 性别未知传 null -> mapGender -> unknown。缓存同步失败不阻断注册主流程。
            try {
                fhirPatientService.upsertLocalPatient(
                        user.getId(), user.getPhone(), request.getName(), null);
            } catch (Exception e) {
                log.error("注册同步FHIR患者缓存失败: userId={}, name={}",
                        user.getId(), request.getName(), e);
            }
        } else {
            log.error("用户注册失败: username={}", request.getUsername());
        }
        return saved;
    }

    /**
     * 解析用户身份信息中的真实姓名
     * @param user 用户实体
     * @return 真实姓名，无身份信息或解析失败时返回 null
     */
    private String resolveName(User user) {
        if (user.getIdentity() == null || user.getIdentity().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(user.getIdentity(), IdentityResponse.class).getName();
        } catch (JsonProcessingException e) {
            log.warn("解析身份信息失败: userId={}", user.getId(), e);
            return null;
        }
    }

    /**
     * 获取当前登录用户信息（供 /auth/me 使用，不泄漏密码/健康档案）
     * @param userId 用户ID
     * @return 用户信息映射 {id, username, name, phone, role}；用户不存在返回 null
     */
    public Map<String, Object> getCurrentUserInfo(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return null;
        }
        // 用 HashMap 而非 Map.of：resolveName 可能返回 null（用户无身份信息），
        // Map.of 不允许 null 值，会抛 NullPointerException
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("name", resolveName(user));
        info.put("phone", user.getPhone());
        info.put("role", user.getRole().name());
        return info;
    }
}