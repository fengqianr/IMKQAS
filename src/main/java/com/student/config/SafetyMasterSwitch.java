package com.student.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安全机制统一总开关
 * 控制所有安全机制的启用/禁用：急症预检、置信度门控、回答净化、禁忌检测、矛盾检测、安全提示等
 * 用于 A/B 对比测试，可一键关闭所有安全机制
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "safety")
@Data
public class SafetyMasterSwitch {

    /** 安全机制总开关，关闭后所有安全机制均不生效 */
    private boolean enabled = true;
}
