package com.student.filter;

import com.student.exception.AuthenticationException;
import com.student.exception.ErrorCode;
import com.student.service.common.UserDetailsServiceImpl;
import com.student.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 拦截请求，验证JWT令牌，设置认证信息到SecurityContext
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * JWT令牌在请求头中的键名
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * JWT令牌前缀
     */
    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 过滤请求，验证JWT令牌
     *
     * @param request HTTP请求
     * @param response HTTP响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet异常
     * @throws IOException IO异常
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // 从请求头中提取JWT令牌
            String token = extractJwtFromRequest(request);

            // 如果令牌存在且有效
            if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
                // 从令牌中解析用户名
                String username = jwtUtil.getUsernameFromToken(token);

                // 如果SecurityContext中还没有认证信息
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // 加载用户详情
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // 验证令牌中的用户信息与数据库中的是否一致
                    if (jwtUtil.getUserIdFromToken(token).equals(
                            ((com.student.entity.User) userDetails).getId())) {

                        // 创建认证令牌
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        // 设置认证详情
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );

                        // 设置认证信息到SecurityContext
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("用户认证成功: username={}", username);
                    } else {
                        log.warn("令牌中的用户信息与数据库不匹配: tokenUsername={}", username);
                        throw new AuthenticationException(ErrorCode.TOKEN_INVALID.getCode(),
                                "令牌无效或已过期");
                    }
                }
            }
        } catch (Exception e) {
            log.error("JWT认证失败", e);
            // 认证失败，继续执行过滤器链，由后续的授权机制处理
            // 如果请求需要认证，Spring Security会返回401
        }

        // 继续执行过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 从HTTP请求中提取JWT令牌
     *
     * @param request HTTP请求
     * @return JWT令牌，如果没有则返回null
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }

        // 也尝试从查询参数中获取（用于WebSocket等场景）
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }

        return null;
    }
}