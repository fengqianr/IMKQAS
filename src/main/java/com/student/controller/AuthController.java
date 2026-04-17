package com.student.controller;

import com.student.dto.ApiResponse;
import com.student.dto.LoginRequest;
import com.student.dto.LoginResponse;
import com.student.service.common.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理用户登录、注册、验证码发送等认证相关接口
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "认证管理", description = "用户认证相关接口")
public class AuthController {

    private final AuthService authService;

    /**
     * 发送登录验证码
     *
     * @param phone 手机号
     * @return 发送结果
     */
    @PostMapping("/send-code")
    @Operation(summary = "发送验证码", description = "向指定手机号发送登录验证码")
    public ResponseEntity<ApiResponse> sendLoginCode(@RequestParam String phone) {
        log.info("请求发送验证码: phone={}", phone);

        boolean success = authService.sendLoginCode(phone);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("验证码发送成功"));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("验证码发送失败，请稍后重试"));
        }
    }

    /**
     * 验证码登录
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    @Operation(summary = "验证码登录", description = "使用手机号+验证码进行登录")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("用户登录请求: username={}", request.getUsername());

        LoginResponse response = authService.loginWithCode(request);
        if (response.getToken() != null) {
            log.info("用户登录成功: username={}, userId={}", request.getUsername(), response.getUserId());
            return ResponseEntity.ok(ApiResponse.success("登录成功", response));
        } else {
            log.warn("用户登录失败: username={}, message={}", request.getUsername(), response.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<LoginResponse>error(response.getMessage(), response));
        }
    }

    /**
     * 刷新令牌
     *
     * @param authorization 原令牌（Bearer token）
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用旧令牌刷新获取新令牌")
    public ResponseEntity<ApiResponse> refreshToken(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("无效的授权头"));
        }

        String oldToken = authorization.substring(7);
        log.debug("刷新令牌请求: token={}", oldToken);

        String newToken = authService.refreshToken(oldToken);
        if (newToken != null) {
            return ResponseEntity.ok(ApiResponse.success("令牌刷新成功", newToken));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("令牌刷新失败，请重新登录"));
        }
    }

    /**
     * 用户登出
     *
     * @param authorization 令牌（Bearer token）
     * @return 登出结果
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出，客户端应删除本地令牌")
    public ResponseEntity<ApiResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("无效的授权头"));
        }

        String token = authorization.substring(7);
        log.info("用户登出请求: token={}", token);

        boolean success = authService.logout(token);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("登出成功"));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("登出失败"));
        }
    }

    /**
     * 验证令牌
     *
     * @param authorization 令牌（Bearer token）
     * @return 验证结果
     */
    @GetMapping("/validate")
    @Operation(summary = "验证令牌", description = "验证JWT令牌是否有效")
    public ResponseEntity<ApiResponse> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.ok(ApiResponse.success("令牌无效", false));
        }

        String token = authorization.substring(7);
        boolean valid = authService.validateToken(token);

        if (valid) {
            return ResponseEntity.ok(ApiResponse.success("令牌有效", true));
        } else {
            return ResponseEntity.ok(ApiResponse.success("令牌无效", false));
        }
    }

    /**
     * 获取当前用户信息（通过令牌）
     *
     * @param authorization 令牌（Bearer token）
     * @return 用户信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "通过JWT令牌获取当前登录用户信息")
    public ResponseEntity<ApiResponse> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("未提供授权令牌"));
        }

        String token = authorization.substring(7);
        Long userId = authService.getUserIdFromToken(token);
        String role = authService.getRoleFromToken(token);

        if (userId == null || role == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("令牌无效或已过期"));
        }

        // 这里应返回完整的用户信息，简化返回ID和角色
        return ResponseEntity.ok(ApiResponse.success("获取成功", new Object() {
            public final Long id = userId;
            public final String userRole = role;
        }));
    }
}