package com.imkqas.service.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * 医疗问答 ReAct Agent 接口
 * 基于 LangChain4j AiServices 构建，系统提示词定义行为规则与工具调用时机
 *
 * @author 系统
 * @version 1.0
 */
public interface MedicalAgent {

    /**
     * 回答用户问题（可能触发多轮 Thought → Action → Observation 循环）
     *
     * @param query 用户原始问题
     * @return 最终回答
     */
    @SystemMessage(fromResource = "/prompts/medical_agent_system.txt")
    String answer(String query);
}
