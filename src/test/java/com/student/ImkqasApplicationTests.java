package com.student;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * IMKQAS应用上下文加载测试
 * 验证Spring Boot应用是否能正常启动
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class ImkqasApplicationTests {

    /**
     * 上下文加载测试
     * 验证Spring应用上下文是否能成功加载
     */
    @Test
    void contextLoads() {
        // 如果Spring上下文能成功加载，测试通过
        // 不需要额外断言，@SpringBootTest注解会确保上下文加载
    }
}