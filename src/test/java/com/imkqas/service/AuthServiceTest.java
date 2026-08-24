package com.imkqas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imkqas.dto.LoginRequest;
import com.imkqas.dto.LoginResponse;
import com.imkqas.dto.RegisterRequest;
import com.imkqas.dto.user.IdentityResponse;
import com.imkqas.entity.User;
import com.imkqas.exception.BusinessException;
import com.imkqas.service.common.AuthService;
import com.imkqas.service.common.CodeService;
import com.imkqas.service.common.UserService;
import com.imkqas.service.his.FhirPatientService;
import com.imkqas.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 认证服务单元测试
 * 测试 AuthService 的用户名密码登录、注册（角色白名单）与令牌刷新
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

    @Mock
    private FhirPatientService fhirPatientService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // 注册用户带身份信息（identity 含真实姓名），验证姓名回显
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .phone("13800000000")
                .role(User.Role.PATIENT)
                .password("$2a$10$encoded-password-hash")
                .identity("{\"name\":\"张三\"}")
                .healthProfile("{\"age\": 30, \"gender\": \"male\"}")
                .build();

        // 创建登录请求（用户名+密码）
        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        // 设置JWT过期时间
        ReflectionTestUtils.setField(authService, "jwtExpiration", 86400000L);
    }

    // ===== 登录 =====

    @Test
    void testLoginWithCode_ValidPassword_ExistingUser() throws JsonProcessingException {
        // 准备
        String token = "jwt-token";
        when(userService.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(1L, "testuser", "PATIENT")).thenReturn(token);
        // 身份信息解析：真实姓名"张三"
        when(objectMapper.readValue(eq(testUser.getIdentity()), eq(IdentityResponse.class)))
                .thenReturn(buildIdentity("张三"));

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证：返回令牌、真实姓名、手机号、角色
        assertNotNull(response);
        assertEquals(token, response.getToken());
        assertEquals(token, response.getRefreshToken());
        assertEquals(1L, response.getUserId());
        assertEquals("testuser", response.getUsername());
        assertEquals("张三", response.getName());
        assertEquals("PATIENT", response.getRole());
        assertEquals("13800000000", response.getPhone());
        assertNotNull(response.getExpiresAt());
        verify(userService).findByUsername("testuser");
        verify(passwordEncoder).matches("password123", testUser.getPassword());
        verify(jwtUtil).generateToken(1L, "testuser", "PATIENT");
    }

    @Test
    void testLoginWithCode_LoginByPhone() throws JsonProcessingException {
        // 准备：输入手机号，先按用户名查不到，再按手机号查到用户
        loginRequest.setUsername("13800000000");
        String token = "jwt-token";
        when(userService.findByUsername("13800000000")).thenReturn(null);
        when(userService.findByPhone("13800000000")).thenReturn(testUser);
        when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(1L, "testuser", "PATIENT")).thenReturn(token);
        when(objectMapper.readValue(eq(testUser.getIdentity()), eq(IdentityResponse.class)))
                .thenReturn(buildIdentity("张三"));

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证
        assertNotNull(response);
        assertEquals(token, response.getToken());
        assertEquals("张三", response.getName());
        verify(userService).findByUsername("13800000000");
        verify(userService).findByPhone("13800000000");
    }

    @Test
    void testLoginWithCode_WrongPassword() {
        // 准备：密码不匹配
        when(userService.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("wrong-password", testUser.getPassword())).thenReturn(false);
        loginRequest.setPassword("wrong-password");

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证：返回密码错误，不发令牌
        assertNotNull(response);
        assertNull(response.getToken());
        assertEquals("密码错误", response.getMessage());
        verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
    }

    @Test
    void testLoginWithCode_UserNotFound() {
        // 准备：按用户名查不到（"testuser" 非手机号格式，不触发按手机号查询）
        when(userService.findByUsername("testuser")).thenReturn(null);

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证
        assertNotNull(response);
        assertNull(response.getToken());
        assertEquals("用户不存在", response.getMessage());
    }

    @Test
    void testLoginWithCode_EmptyUsername() {
        // 准备
        loginRequest.setUsername("");

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证：不查询数据库
        assertNotNull(response);
        assertNull(response.getToken());
        assertEquals("用户名不能为空", response.getMessage());
        verify(userService, never()).findByUsername(anyString());
        verify(userService, never()).findByPhone(anyString());
    }

    @Test
    void testLoginWithCode_EmptyPassword() {
        // 准备
        loginRequest.setPassword("");

        // 执行
        LoginResponse response = authService.loginWithCode(loginRequest);

        // 验证：不查询数据库
        assertNotNull(response);
        assertNull(response.getToken());
        assertEquals("密码不能为空", response.getMessage());
        verify(userService, never()).findByUsername(anyString());
    }

    // ===== 注册（角色白名单 + 真实姓名写 identity） =====

    @Test
    void testRegister_DoctorRoleRejected() {
        // 准备：注册请求选择医生角色
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newdoctor");
        request.setName("李医生");
        request.setPhone("13900000000");
        request.setPassword("Password123");
        request.setConfirmPassword("Password123");
        request.setRole(User.Role.DOCTOR);

        // 执行：抛出业务异常，医生账号须由管理员创建
        assertThrows(BusinessException.class, () -> authService.register(request));
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void testRegister_AdminRoleRejected() {
        // 准备：注册请求选择管理员角色
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newadmin");
        request.setName("王管理员");
        request.setPhone("13900000001");
        request.setPassword("Password123");
        request.setConfirmPassword("Password123");
        request.setRole(User.Role.ADMIN);

        // 执行：抛出业务异常
        assertThrows(BusinessException.class, () -> authService.register(request));
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void testRegister_SuccessWithRoleAndIdentity() throws JsonProcessingException {
        // 准备：注册学生角色，真实姓名同步写入 identity
        RegisterRequest request = new RegisterRequest();
        request.setUsername("student01");
        request.setName("赵学生");
        request.setPhone("13900000002");
        request.setPassword("Password123");
        request.setConfirmPassword("Password123");
        request.setRole(User.Role.STUDENT);

        when(userService.isUsernameExists("student01")).thenReturn(false);
        when(userService.isPhoneRegistered("13900000002")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("$2a$10$encoded");
        when(objectMapper.writeValueAsString(any(IdentityResponse.class)))
                .thenReturn("{\"name\":\"赵学生\"}");
        when(userService.save(any(User.class))).thenReturn(true);

        // 执行
        boolean result = authService.register(request);

        // 验证：角色与身份信息一并入库，同步建立 FHIR 患者缓存
        assertTrue(result);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        assertEquals(User.Role.STUDENT, captor.getValue().getRole());
        assertEquals("{\"name\":\"赵学生\"}", captor.getValue().getIdentity());
        // mock 的 save 不生成主键，此时 user.getId() 为 null
        verify(fhirPatientService).upsertLocalPatient(
                isNull(), eq("13900000002"), eq("赵学生"), isNull());
    }

    @Test
    void testRegister_UsernameExists() {
        // 准备：用户名已存在
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setName("张三");
        request.setPhone("13900000003");
        request.setPassword("Password123");
        request.setConfirmPassword("Password123");
        request.setRole(User.Role.PATIENT);

        when(userService.isUsernameExists("testuser")).thenReturn(true);

        // 执行
        boolean result = authService.register(request);

        // 验证：注册失败
        assertFalse(result);
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void testRegister_PasswordMismatch() {
        // 准备：两次密码不一致
        RegisterRequest request = new RegisterRequest();
        request.setUsername("student02");
        request.setName("孙学生");
        request.setPhone("13900000004");
        request.setPassword("Password123");
        request.setConfirmPassword("Password456");
        request.setRole(User.Role.PATIENT);

        when(userService.isUsernameExists("student02")).thenReturn(false);
        when(userService.isPhoneRegistered("13900000004")).thenReturn(false);

        // 执行
        boolean result = authService.register(request);

        // 验证：注册失败，未保存
        assertFalse(result);
        verify(userService, never()).save(any(User.class));
    }

    // ===== 令牌刷新 =====

    @Test
    void testRefreshToken_ValidToken() {
        // 准备
        String oldToken = "old-jwt-token";
        String newToken = "new-jwt-token";
        when(jwtUtil.validateToken(oldToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(oldToken)).thenReturn(1L);
        when(jwtUtil.getUsernameFromToken(oldToken)).thenReturn("testuser");
        when(jwtUtil.getRoleFromToken(oldToken)).thenReturn("PATIENT");
        when(userService.getById(1L)).thenReturn(testUser);
        when(jwtUtil.generateToken(1L, "testuser", "PATIENT")).thenReturn(newToken);

        // 执行
        String result = authService.refreshToken(oldToken);

        // 验证
        assertEquals(newToken, result);
        verify(jwtUtil).validateToken(oldToken);
        verify(jwtUtil).getUserIdFromToken(oldToken);
        verify(jwtUtil).getUsernameFromToken(oldToken);
        verify(jwtUtil).getRoleFromToken(oldToken);
        verify(userService).getById(1L);
        verify(jwtUtil).generateToken(1L, "testuser", "PATIENT");
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
        verify(jwtUtil, never()).getUserIdFromToken(anyString());
        verify(userService, never()).getById(any());
    }

    @Test
    void testRefreshToken_UserNotFound() {
        // 准备
        String oldToken = "valid-jwt-token";
        when(jwtUtil.validateToken(oldToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(oldToken)).thenReturn(999L);
        when(userService.getById(999L)).thenReturn(null);

        // 执行
        String result = authService.refreshToken(oldToken);

        // 验证
        assertNull(result);
        verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
    }

    // ===== 登出 =====

    @Test
    void testLogout() {
        // 准备
        String token = "jwt-token";

        // 执行
        boolean result = authService.logout(token);

        // 验证：无状态登出，客户端删除令牌即可
        assertTrue(result);
    }

    // ===== 辅助方法 =====

    /** 构造带姓名的身份响应 */
    private IdentityResponse buildIdentity(String name) {
        IdentityResponse identity = new IdentityResponse();
        identity.setName(name);
        return identity;
    }
}
