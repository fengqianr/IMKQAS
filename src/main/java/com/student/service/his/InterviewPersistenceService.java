package com.student.service.his;

import com.student.service.his.InterviewSession;
import com.student.service.his.AnalysisResult;
import com.student.service.his.TransformationPipeline;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 访谈数据持久化服务接口
 * 协调 interview_sessions / interview_messages / analysis_reports 三张表的读写
 *
 * @author 系统
 * @version 1.0
 */
public interface InterviewPersistenceService {

    // ==================== Session 持久化 ====================

    /**
     * 保存/更新访谈会话（upsert: session_id存在则更新，否则插入）
     */
    void saveSession(InterviewSession session);

    /**
     * 从MySQL加载会话（反序列化JSON字段）
     */
    Optional<InterviewSession> loadSession(String sessionId);

    /**
     * 逻辑删除会话
     */
    void deleteSession(String sessionId);

    /**
     * 查询用户的所有访谈会话
     */
    List<InterviewSession> listSessionsByUser(Long userId);

    /**
     * 查询对话下的所有访谈会话
     */
    List<InterviewSession> listSessionsByConversation(Long conversationId);

    // ==================== Message 持久化 ====================

    /**
     * 保存一条访谈消息（对应SSE事件数据）
     */
    void saveInterviewMessage(Long conversationId, String sessionId, Long userId,
                              String messageType, String questionnaireId,
                              String questionnaireTitle, Map<String, Object> messageData);

    /**
     * 获取会话的所有访谈消息（按序号排序）
     */
    List<Map<String, Object>> getInterviewMessagesBySession(String sessionId);

    /**
     * 获取对话下的所有访谈消息
     */
    List<Map<String, Object>> getInterviewMessagesByConversation(Long conversationId);

    // ==================== Analysis 持久化 ====================

    /**
     * 保存完整的AI分析报告
     */
    void saveAnalysisReport(AnalysisResult result, InterviewSession session,
                            TransformationPipeline.PipelineResult pipelineResult);

    /**
     * 根据sessionId获取分析报告
     */
    Optional<AnalysisResult> getAnalysisReportBySessionId(String sessionId);

    /**
     * 根据analysisId获取分析报告
     */
    Optional<AnalysisResult> getAnalysisReportByAnalysisId(String analysisId);

    // ==================== FHIR 资源持久化 ====================

    /**
     * 保存FHIR DiagnosticReport资源
     */
    void saveFhirDiagnosticReport(com.student.entity.his.FhirDiagnosticReportCache report);

    /**
     * 保存FHIR RiskAssessment资源
     */
    void saveFhirRiskAssessment(com.student.entity.his.FhirRiskAssessmentCache risk);

    /**
     * 根据sessionId获取FHIR DiagnosticReport
     */
    Optional<com.student.entity.his.FhirDiagnosticReportCache> getFhirDiagnosticReportBySession(String sessionId);

    /**
     * 根据sessionId获取FHIR RiskAssessment
     */
    Optional<com.student.entity.his.FhirRiskAssessmentCache> getFhirRiskAssessmentBySession(String sessionId);
}
