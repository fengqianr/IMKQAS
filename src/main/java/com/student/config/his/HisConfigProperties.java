package com.student.config.his;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * HIS/FHIR集成配置属性
 * 为后续对接医院信息系统预留配置项
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "imkqas.his")
public class HisConfigProperties {

    /**
     * 是否启用HIS集成（默认关闭，预留阶段为false）
     */
    private boolean enabled = false;

    /**
     * HIS FHIR服务器基础URL（后续对接时配置）
     */
    private String baseUrl = "http://localhost:8083";

    /**
     * FHIR API路径
     */
    private String fhirPath = "/fhir";

    /**
     * HIS系统名称标识
     */
    private String systemName = "HIS";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 15000;

    /**
     * 最大重试次数
     */
    private int maxRetries = 2;

    /**
     * 本地缓存过期时间（小时）
     */
    private int cacheExpirationHours = 24;

    /**
     * 查询默认分页大小
     */
    private int defaultPageSize = 20;

    /**
     * 查询最大分页大小
     */
    private int maxPageSize = 100;

    /**
     * 问卷会话超时时间（分钟）
     */
    private int interviewSessionTimeoutMinutes = 30;
}
