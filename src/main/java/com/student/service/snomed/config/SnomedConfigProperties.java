package com.student.service.snomed.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SNOMED CT 服务配置属性
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "imkqas.snomed")
public class SnomedConfigProperties {

    /**
     * 是否启用SNOMED CT服务
     */
    private boolean enabled = true;

    /**
     * Snowstorm服务基础URL
     */
    private String baseUrl = "http://localhost:8082";

    /**
     * FHIR API路径
     */
    private String fhirPath = "/fhir";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 10000;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 缓存过期时间（小时）
     */
    private int cacheExpirationHours = 24;

    /**
     * 是否启用本地降级映射
     */
    private boolean fallbackEnabled = true;

    /**
     * 批量查询最大数量
     */
    private int batchMaxSize = 100;
}