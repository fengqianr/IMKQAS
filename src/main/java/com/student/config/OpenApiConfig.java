package com.student.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 配置类
 * 配置Swagger UI和OpenAPI文档生成
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:IMKQAS}")
    private String applicationName;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    /**
     * 配置OpenAPI文档
     *
     * @return OpenAPI配置对象
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // API信息
                .info(new Info()
                        .title("IMKQAS医疗知识问答系统 API文档")
                        .description("""
                                IMKQAS (Intelligent Medical Knowledge Q&A System) 医疗知识问答系统 API接口文档

                                ## 系统概述
                                IMKQAS是一个基于RAG（检索增强生成）架构的医疗知识问答系统，提供：
                                - 用户认证与授权管理
                                - 医学文档上传与管理
                                - 智能问答对话
                                - 向量检索与语义搜索

                                ## 技术栈
                                - 后端: Spring Boot 3.2.5 + MyBatis Plus
                                - 数据库: MySQL + Redis
                                - 向量数据库: Milvus
                                - 对象存储: MinIO
                                - 安全: Spring Security + JWT
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("IMKQAS开发团队")
                                .email("support@imkqas.com")
                                .url("https://imkqas.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                // 服务器配置
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080" + contextPath)
                                .description("本地开发环境"),
                        new Server()
                                .url("https://api.imkqas.com" + contextPath)
                                .description("生产环境")
                ))
                // 安全配置（JWT）
                .addSecurityItem(new SecurityRequirement().addList("JWT"))
                .components(new Components()
                        .addSecuritySchemes("JWT", new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        输入JWT令牌进行认证。
                                        格式: Bearer <token>
                                        示例: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
                                        """)));
    }
}