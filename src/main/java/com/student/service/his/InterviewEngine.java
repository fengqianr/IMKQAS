package com.student.service.his;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
     * 提交单个答案，返回下一题或完成结果
     *
     * @param sessionId 会话ID
     * @param answer    用户的回答
     * @return 下一题信息 或 完成摘要
     */
    InterviewResponse answerQuestion(String sessionId, String answer);

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
}
