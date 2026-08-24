package com.imkqas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * ReAct Agent 配置类
 * 管理 Agent 模型的调用端点、超时与工具参数
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "imkqas.agent")
@Data
@Validated
public class AgentProperties {

    /** 是否启用 Agent 链路（二期接入生产路由时使用） */
    private boolean enabled = true;

    /** 模型名称（需支持 function calling，qwen-plus/qwen-max） */
    private String model = "qwen-plus";

    /** 百炼 OpenAI 兼容 chat 端点 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** 温度参数 */
    private double temperature = 0.1;

    /** 最大 token 数 */
    private int maxTokens = 2000;

    /** searchKnowledge 工具返回的检索条数 */
    private int topK = 5;

    /** Agent 整体超时时间（秒） */
    private long timeoutSeconds = 15;
}
