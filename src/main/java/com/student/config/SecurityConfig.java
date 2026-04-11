package com.student.config;

import com.student.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security配置类
 * 配置JWT认证、授权、CORS等安全相关设置
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 安全过滤器链配置
     *
     * @param http HttpSecurity对象
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（使用JWT不需要CSRF保护）
                .csrf(AbstractHttpConfigurer::disable)
                // 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 配置会话管理为无状态（使用JWT）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置请求授权
                .authorizeHttpRequests(auth -> auth
                        // 公开访问的端点
                        .requestMatchers(
                                // 认证相关端点
                                "/api/auth/**",
                                // API文档端点
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                // 健康检查
                                "/actuator/health",
                                // 静态资源
                                "/favicon.ico",
                                "/error"
                        ).permitAll()
                        // 需要认证的端点
                        .anyRequest().authenticated())
                // 添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 配置CORS
     *
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*")); // 生产环境应配置具体域名
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(false); // 如果允许凭证，则origins不能为"*"
        configuration.setMaxAge(3600L); // 预检请求缓存时间（秒）

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 密码编码器
     * 支持多种密码编码格式，兼容现有MD5哈希和新用户BCrypt哈希
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 创建自定义密码编码器，兼容现有MD5哈希和新BCrypt哈希
        return new PasswordEncoder() {
            private final BCryptPasswordEncoder bcryptEncoder = new BCryptPasswordEncoder();
            private final MessageDigestPasswordEncoder md5Encoder = new MessageDigestPasswordEncoder("MD5");

            @Override
            public String encode(CharSequence rawPassword) {
                // 新密码使用BCrypt编码
                return bcryptEncoder.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if (encodedPassword == null || encodedPassword.isEmpty()) {
                    return false;
                }

                // 如果encodedPassword以{bcrypt}开头，使用BCrypt验证
                if (encodedPassword.startsWith("{bcrypt}")) {
                    return bcryptEncoder.matches(rawPassword, encodedPassword.substring(8));
                }

                // 如果encodedPassword以{md5}开头，使用MD5验证
                if (encodedPassword.startsWith("{md5}")) {
                    return md5Encoder.matches(rawPassword, encodedPassword.substring(5));
                }

                // 如果是32位十六进制字符串（MD5哈希），使用MD5验证
                if (encodedPassword.matches("^[a-fA-F0-9]{32}$")) {
                    return md5Encoder.matches(rawPassword, encodedPassword);
                }

                // 否则尝试BCrypt验证（可能是不带前缀的BCrypt哈希）
                try {
                    return bcryptEncoder.matches(rawPassword, encodedPassword);
                } catch (Exception e) {
                    // 如果不是BCrypt格式，尝试MD5
                    return md5Encoder.matches(rawPassword, encodedPassword);
                }
            }
        };
    }

    /**
     * 认证管理器
     *
     * @param authenticationConfiguration 认证配置
     * @return AuthenticationManager
     * @throws Exception 配置异常
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}