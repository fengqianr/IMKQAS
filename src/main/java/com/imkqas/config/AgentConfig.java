package com.imkqas.config;

import com.imkqas.service.agent.MedicalAgent;
import com.imkqas.service.agent.MedicalTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ReAct Agent 配置类
 * 构建 LangChain4j 的 ChatLanguageModel 与 AiServices 代理
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AgentConfig {

    private final AgentProperties agentProperties;
    private final RagConfig ragConfig;

    /**
     * 构建面向百炼 OpenAI 兼容 chat 端点的聊天模型
     * API 密钥复用 RagConfig 中已从 .env 加载的密钥
     */
    @Bean
    public ChatLanguageModel agentChatLanguageModel() {
        String apiKey = ragConfig.getLlm().getApiKey();
        if (apiKey == null) {
            apiKey = "";
        }
        log.info("Agent ChatLanguageModel 初始化: model={}, baseUrl={}",
                agentProperties.getModel(), agentProperties.getBaseUrl());
        return OpenAiChatModel.builder()
                .baseUrl(agentProperties.getBaseUrl())
                .apiKey(apiKey)
                .modelName(agentProperties.getModel())
                .temperature(agentProperties.getTemperature())
                .maxTokens(agentProperties.getMaxTokens())
                .build();
    }

    /**
     * 构建 MedicalAgent 代理（AiServices）
     * 解耦点：若 qwen 工具调用不兼容，仅替换此 bean 为手写 ReAct 实现即可
     */
    @Bean
    public MedicalAgent medicalAgent(ChatLanguageModel agentChatLanguageModel, MedicalTools medicalTools) {
        return AiServices.builder(MedicalAgent.class)
                .chatLanguageModel(agentChatLanguageModel)
                .tools(medicalTools)
                .build();
    }
}
