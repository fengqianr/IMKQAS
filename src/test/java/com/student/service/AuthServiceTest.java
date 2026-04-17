package com.student.service;

import com.student.dto.LoginRequest;
import com.student.dto.LoginResponse;
import com.student.entity.User;
import com.student.service.common.AuthService;
import com.student.service.common.CodeService;
import com.student.service.common.UserService;
import com.student.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 认证服务单元测试
 * 测试AuthService的各种方法
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private CodeService codeService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .phone("13800000000")
                .role(User.Role.PATIENT)
                .healthProfile("{\"age\": 30, \"gender\": \"male\"}")
                .deleted(0)
                .build();

        // 创建登录请求
        loginRequest = new LoginRequest();
        loginRequest.setUsername("13800000000"); // 手机号
        loginRequest.setCaptcha("123456");

        // 设置JWT过期时间
        ReflectionTestUtils.setField(authService, "jwtExpiration", 86400000L);
    }

    @Test
    void testSendLoginCode_ValidPhone() {
        // 准备
        String phone = "13800000000";
        when(codeService.canSendCode(phone)).thenReturn(true);
        when(codeService.sendCode(phone)).thenReturn(true);

        // 执行
        boolean result = authService.sendLoginCode(phone);

        // 验证
        assertTrue(result);
        verify(codeService).canSendCode(phone);
        verify(codeService).sendCode(phone);
    }

    @Test
    void testSendLoginCode_InvalidPhone() {
        // 准备
        String phone = "12345"; // 无效手机号

        // 执行
        boolean result = authService.sendLoginCode(phone);

        // 验证
        assertFalse(result);
        verify(codeService, never()).canSendCode(anyString());
        verify(codeService, never()).sendCode(anyString());
    }

    @Test
    void testSendLoginCode_CannotSend() {
        // 准备
        String phone = "13800000000";
        when(codeService.canSendCode(phone)).thenReturn(false);

        // 执行
        boolean result = authService.sendLoginCode(phone);

        // 验证
        assertFalse(result);
        verify(codeService).canSendCode(phone);
        verify(codeService, never()).sendCode(anyString());
    }

    @Test
    void testLoginWithCode_ValidCode_ExistingUser() {
        // 准备
        String phone = "13800000000";
        String code = "123456";
        String token = "jwt-token";
        Long expiresAt = System.currentTimeMillis() + 86400000L;

        when(codeService.verifyCode(phone, code)).thenReturn(true);
        when(userService.findByPhone(phone)).thenReturn(testUser);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn(token);

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证
        assertNotNull(response);
        assertEquals(token, response.getToken());
        assertEquals(testUser.getId(), response.getUserId());
        assertEquals(testUser.getUsername(), response.getUsername());
        assertEquals(testUser.getRole().name(), response.getRole());
        assertEquals(testUser.getPhone(), response.getPhone());
        assertNotNull(response.getExpiresAt());
        verify(codeService).verifyCode(phone, code);
        verify(userService).findByPhone(phone);
        verify(jwtUtil).generateToken(testUser.getId(), testUser.getUsername(), testUser.getRole().name());
    }

    @Test
    void testLoginWithCode_ValidCode_NewUser() {
        // 准备
        String phone = "13900000000"; // 新手机号
        String code = "123456";
        String token = "jwt-token";
        loginRequest.setUsername(phone);

        when(codeService.verifyCode(phone, code)).thenReturn(true);
        when(userService.findByPhone(phone)).thenReturn(null).thenReturn(testUser);
        when(userService.save(any(User.class))).thenReturn(true);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn(token);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证
        assertNotNull(response);
        assertEquals(token, response.getToken());
        verify(codeService).verifyCode(phone, code);
        verify(userService, times(2)).findByPhone(phone); // 第一次null，第二次返回用户
        verify(userService).save(any(User.class));
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    void testLoginWithCode_InvalidPhone() {
        // 准备
        loginRequest.setUsername("12345"); // 无效手机号

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证
        assertNotNull(response);
        assertNull(response.getToken());
        assertNotNull(response.getMessage());
        verify(codeService, never()).verifyCode(anyString(), anyString());
        verify(userService, never()).findByPhone(anyString());
    }

    @Test
    void testLoginWithCode_InvalidCode() {
        // 准备
        String phone = "13800000000";
        String code = "wrong-code";
        loginRequest.setCaptcha(code); // 设置验证码以匹配stub

        when(codeService.verifyCode(phone, code)).thenReturn(false);

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证
        assertNotNull(response);
        assertNull(response.getToken());
        assertNotNull(response.getMessage());
        verify(codeService).verifyCode(phone, code);
        verify(userService, never()).findByPhone(anyString());
    }

    @Test
    void testRefreshToken_ValidToken() {
        // 准备
        String oldToken = "old-jwt-token";
        String newToken = "new-jwt-token";
        Long userId = 1L;
        String username = "testuser";
        String role = "USER";

        when(jwtUtil.validateToken(oldToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(oldToken)).thenReturn(userId);
        when(jwtUtil.getUsernameFromToken(oldToken)).thenReturn(username);
        when(jwtUtil.getRoleFromToken(oldToken)).thenReturn(role);
        when(userService.getById(userId)).thenReturn(testUser);
        when(jwtUtil.generateToken(userId, username, role)).thenReturn(newToken);

        // 执行
        String result = authService.refreshToken(oldToken);

        // 验证
        assertEquals(newToken, result);
        verify(jwtUtil).validateToken(oldToken);
        verify(jwtUtil).getUserIdFromToken(oldToken);
        verify(jwtUtil).getUsernameFromToken(oldToken);
        verify(jwtUtil).getRoleFromToken(oldToken);
        verify(userService).getById(userId);
        verify(jwtUtil).generateToken(userId, username, role);
    }

    @Test
    void testRefreshToken_InvalidToken() {
        // 准备
        String oldToken = "invalid-jwt-token";
        when(jwtUtil.validateToken(oldToken)).thenReturn(false);

        // 执行
        String result = authService.refreshToken(oldToken);

        // 验证
        assertNull(result);
        verify(jwtUtil).validateToken(oldToken);
        verify(jwtUtil, never()).getUserIdFromToken(anyString());
        verify(userService, never()).getById(any());
    }

    @Test
    void testRefreshToken_UserNotFound() {
        // 准备
        String oldToken = "valid-jwt-token";
        Long userId = 999L;

        when(jwtUtil.validateToken(oldToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(oldToken)).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(null);

        // 执行
        String result = authService.refreshToken(oldToken);

        // 验证
        assertNull(result);
        verify(jwtUtil).validateToken(oldToken);
        verify(jwtUtil).getUserIdFromToken(oldToken);
        verify(userService).getById(userId);
        verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
    }

    @Test
    void testLogout() {
        // 准备
        String token = "jwt-token";

        // 执行
        boolean result = authService.logout(token);

        // 验证
        assertTrue(result);
        // 简化实现，仅记录日志
    }
}