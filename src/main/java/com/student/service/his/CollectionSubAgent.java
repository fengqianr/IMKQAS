package com.student.service.his;

/**
 * 采集子Agent接口
 * 负责LLM驱动的自然对话采集，每个问卷类型对应独立的系统提示词和工具集
 *
 * @author 系统
 * @version 1.0
 */
public interface CollectionSubAgent {

    /**
     * 处理用户输入，返回LLM的工具调用结果
     * 调用方负责：安全检测（调用前）、编码校验（调用后）、SSE发送
     *
     * @param session     当前采集会话状态
     * @param userInput   用户原始输入
     * @param questionnaire 问卷模板（用于注入提示词）
     * @return LLM工具调用结果，状态机根据type决定下一步动作
     */
    CollectionToolOutputs.AgentResult processUserInput(
            InterviewSession session,
            String userInput,
            QuestionnaireTemplate questionnaire);

    /**
     * 判断LLM服务是否可用
     */
    boolean isAvailable();
}
