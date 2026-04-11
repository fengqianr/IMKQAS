package com.student.config;

import com.student.service.AuthService;
import com.student.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT认证过滤器
 * 拦截需要认证的请求，验证JWT令牌
 *
 * @author 系统
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AuthService authService;

    // 公开路径（不需要认证）
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/send-code",
            "/api/auth/refresh",
            "/api/auth/validate",
            "/api/public/",
            "/swagger-ui/",
            "/v3/api-docs",
            "/swagger-ui.html",
            "/swagger-resources/",
            "/webjars/"
    );

    /**
     * 判断请求路径是否为公开路径
     *
     * @param requestPath 请求路径
     * @return 是否为公开路径
     */
    private boolean isPublicPath(String requestPath) {
        if (requestPath == null) {
            return false;
        }

        // 去除上下文路径（如果有）
        String path = requestPath;

        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        log.debug("请求路径: {} {}", method, requestPath);

        // 检查是否为公开路径
        if (isPublicPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取Authorization头
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("请求缺少有效的Authorization头: {}", requestPath);
            sendUnauthorizedResponse(response, "缺少授权令牌");
            return;
        }

        // 提取令牌
        String token = authHeader.substring(7);
        if (token.trim().isEmpty()) {
            log.warn("令牌为空: {}", requestPath);
            sendUnauthorizedResponse(response, "令牌为空");
            return;
        }

        // 验证令牌
        if (!authService.validateToken(token)) {
            log.warn("令牌无效或已过期: {}", requestPath);
            sendUnauthorizedResponse(response, "令牌无效或已过期");
            return;
        }

        try {
            // 从令牌中提取用户信息并设置到请求属性中
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);

            // 将用户信息设置到请求属性中，供后续使用
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("role", role);

            log.debug("用户认证成功: userId={}, username={}, role={}", userId, username, role);

            // 继续过滤器链
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("令牌处理异常", e);
            sendUnauthorizedResponse(response, "令牌处理异常");
        }
    }

    /**
     * 发送未授权响应
     *
     * @param response HTTP响应
     * @param message 错误消息
     * @throws IOException IO异常
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"code\": 401, \"message\": \"%s\", \"data\": null}",
                message
        ));
        response.getWriter().flush();
    }

    /**
     * 判断是否应跳过过滤（预检请求等）
     *
     * @param request HTTP请求
     * @return 是否应跳过
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // 跳过预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 跳过静态资源
        if (path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png") ||
            path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".gif") ||
            path.endsWith(".ico") || path.endsWith(".svg")) {
            return true;
        }

        return false;
    }
}