package com.student.service.his;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 填表引擎接口
 * 按需触发、用户主导：AI建议 → 用户确认 → 进入问答
 *
 * @author 系统
 * @version 1.0
 */
public interface InterviewEngine {

    /**
     * 根据用户描述匹配并建议问卷
     *
     * @param userInput 用户输入（症状描述等）
     * @return 匹配建议（可能为空，表示无可匹配问卷）
     */
    InterviewSuggestion suggestQuestionnaire(String userInput);

    /**
     * 用户确认后，开始填表会话
     *
     * @param questionnaireId 问卷标识（如PHQ-9）
     * @param userId          用户ID
     * @param conversationId  对话ID
     * @return 会话ID + 第一题
     */
    InterviewSession startInterview(String questionnaireId, Long userId, Long conversationId);

    /**
     * 提交单个答案（手动填表模式），返回下一题或完成结果
     *
     * @param sessionId 会话ID
     * @param answer    用户的回答（选项编码）
     * @return 下一题信息 或 完成摘要
     */
    InterviewResponse answerQuestion(String sessionId, String answer);

    /**
     * 统一答案记录 —— 同时支持手动填表和LLM驱动两种模式
     *
     * @param sessionId  会话ID
     * @param linkId     题目linkId
     * @param code       选项编码
     * @param display    选项显示文本
     * @param value      选项分值
     * @param rawInput   用户原始表述（手动模式为空）
     * @param source     答案来源：llm / rule_parser / manual
     * @param confidence LLM置信度（手动模式为1.0）
     * @return 下一题信息 或 完成摘要
     */
    InterviewResponse recordAnswer(String sessionId, String linkId, String code,
                                   String display, int value, String rawInput,
                                   String source, double confidence, String contextSummary);

    /**
     * LLM驱动采集：启动会话并返回首题信息
     */
    InterviewSession startLlmInterview(String questionnaireId, Long userId, Long conversationId);

    /**
     * LLM驱动采集：处理一轮用户自然语言输入
     * 调用CollectionSubAgent进行语义理解，返回AgentResult供调用方通过SSE发送
     *
     * @param sessionId 会话ID
     * @param userInput 用户原始自然语言输入
     * @return LLM工具调用结果
     */
    CollectionToolOutputs.AgentResult processLlmTurn(String sessionId, String userInput);

    /**
     * 生成FHIR QuestionnaireResponse资源
     */
    org.hl7.fhir.r4.model.QuestionnaireResponse generateResponse(String sessionId);

    /**
     * 恢复中断的会话
     */
    InterviewSession resumeInterview(String sessionId);

    /**
     * 取消当前填表
     */
    void cancelInterview(String sessionId);

    /**
     * 查询历史填写记录，含评分趋势
     */
    List<Map<String, Object>> getHistory(Long userId, String questionnaireId);

    /**
     * 获取评分趋势数据（用于图表展示）
     */
    List<Map<String, Object>> getTrend(Long userId, String questionnaireId);

    /**
     * 获取会话的访谈消息列表（用于前端重建问卷卡片）
     */
    List<Map<String, Object>> getInterviewMessages(String sessionId);

    /**
     * 获取完整AI分析报告
     */
    Optional<AnalysisResult> getAnalysisReport(String sessionId);

    /**
     * 获取对话下的所有访谈记录摘要
     */
    List<Map<String, Object>> getInterviewsByConversation(Long conversationId);
}
