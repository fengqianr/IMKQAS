package com.student.service.his.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.entity.his.AnalysisReportEntity;
import com.student.entity.his.InterviewMessageEntity;
import com.student.entity.his.InterviewSessionEntity;
import com.student.entity.his.FhirDiagnosticReportCache;
import com.student.entity.his.FhirRiskAssessmentCache;
import com.student.mapper.his.AnalysisReportMapper;
import com.student.mapper.his.FhirDiagnosticReportCacheMapper;
import com.student.mapper.his.FhirRiskAssessmentCacheMapper;
import com.student.mapper.his.InterviewMessageMapper;
import com.student.mapper.his.InterviewSessionMapper;
import com.student.service.his.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 访谈数据持久化服务实现
 * 所有写操作异步执行，不阻塞主流程；异常仅记录日志
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPersistenceServiceImpl implements InterviewPersistenceService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final AnalysisReportMapper reportMapper;
    private final FhirDiagnosticReportCacheMapper diagnosticReportMapper;
    private final FhirRiskAssessmentCacheMapper riskAssessmentMapper;
    private final ObjectMapper objectMapper;

    // ==================== Session 持久化 ====================

    @Override
    public void saveSession(InterviewSession session) {
        try {
            InterviewSessionEntity entity = toEntity(session);
            InterviewSessionEntity existing = findBySessionId(session.getSessionId());
            if (existing != null) {
                entity.setId(existing.getId());
                sessionMapper.updateById(entity);
                log.debug("MySQL更新会话: sessionId={}", session.getSessionId());
            } else {
                sessionMapper.insert(entity);
                log.debug("MySQL插入会话: sessionId={}", session.getSessionId());
            }
        } catch (Exception e) {
            log.warn("MySQL保存会话失败: sessionId={}", session.getSessionId(), e);
        }
    }

    @Override
    public Optional<InterviewSession> loadSession(String sessionId) {
        try {
            InterviewSessionEntity entity = findBySessionId(sessionId);
            if (entity == null) {
                return Optional.empty();
            }
            return Optional.of(fromEntity(entity));
        } catch (Exception e) {
            log.warn("MySQL加载会话失败: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        try {
            InterviewSessionEntity existing = findBySessionId(sessionId);
            if (existing != null) {
                sessionMapper.deleteById(existing.getId());
                log.debug("MySQL逻辑删除会话: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.warn("MySQL删除会话失败: sessionId={}", sessionId, e);
        }
    }

    @Override
    public List<InterviewSession> listSessionsByUser(Long userId) {
        try {
            LambdaQueryWrapper<InterviewSessionEntity> query = new LambdaQueryWrapper<>();
            query.eq(InterviewSessionEntity::getUserId, userId)
                    .orderByDesc(InterviewSessionEntity::getCreatedAt);
            return sessionMapper.selectList(query).stream()
                    .map(this::fromEntity)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("MySQL查询用户会话列表失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<InterviewSession> listSessionsByConversation(Long conversationId) {
        try {
            LambdaQueryWrapper<InterviewSessionEntity> query = new LambdaQueryWrapper<>();
            query.eq(InterviewSessionEntity::getConversationId, conversationId)
                    .orderByDesc(InterviewSessionEntity::getCreatedAt);
            return sessionMapper.selectList(query).stream()
                    .map(this::fromEntity)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("MySQL查询对话会话列表失败: conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    // ==================== Message 持久化 ====================

    @Override
    public void saveInterviewMessage(Long conversationId, String sessionId, Long userId,
                                     String messageType, String questionnaireId,
                                     String questionnaireTitle, Map<String, Object> messageData) {
        try {
            int seq = getNextSequence(sessionId);
            InterviewMessageEntity entity = InterviewMessageEntity.builder()
                    .conversationId(conversationId)
                    .sessionId(sessionId)
                    .userId(userId)
                    .messageType(messageType)
                    .sequenceNum(seq)
                    .questionnaireId(questionnaireId)
                    .questionnaireTitle(questionnaireTitle)
                    .messageData(objectMapper.writeValueAsString(messageData))
                    .build();
            messageMapper.insert(entity);
            log.debug("MySQL保存访谈消息: sessionId={}, type={}, seq={}", sessionId, messageType, seq);
        } catch (Exception e) {
            log.warn("MySQL保存访谈消息失败: sessionId={}, type={}", sessionId, messageType, e);
        }
    }

    @Override
    public List<Map<String, Object>> getInterviewMessagesBySession(String sessionId) {
        try {
            LambdaQueryWrapper<InterviewMessageEntity> query = new LambdaQueryWrapper<>();
            query.eq(InterviewMessageEntity::getSessionId, sessionId)
                    .orderByAsc(InterviewMessageEntity::getSequenceNum);
            return messageMapper.selectList(query).stream()
                    .map(this::messageToMap)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("MySQL查询会话消息失败: sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> getInterviewMessagesByConversation(Long conversationId) {
        try {
            LambdaQueryWrapper<InterviewMessageEntity> query = new LambdaQueryWrapper<>();
            query.eq(InterviewMessageEntity::getConversationId, conversationId)
                    .orderByAsc(InterviewMessageEntity::getCreatedAt);
            return messageMapper.selectList(query).stream()
                    .map(this::messageToMap)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("MySQL查询对话消息失败: conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    // ==================== Analysis 持久化 ====================

    @Override
    public void saveAnalysisReport(AnalysisResult result, InterviewSession session,
                                   TransformationPipeline.PipelineResult pipelineResult) {
        try {
            String analysisId = "analysis-" + session.getSessionId();
            AnalysisReportEntity entity = AnalysisReportEntity.builder()
                    .analysisId(analysisId)
                    .sessionId(session.getSessionId())
                    .questionnaireId(session.getQuestionnaireId())
                    .questionnaireTitle(session.getQuestionnaireTitle())
                    .userId(session.getUserId())
                    .conversationId(session.getConversationId())
                    .totalScore(pipelineResult.getTotalScore())
                    .maxScore(pipelineResult.getMaxScore())
                    .severity(pipelineResult.getRiskLevel())
                    .interpretation(pipelineResult.getInterpretation())
                    .summary(result.getSummary())
                    .riskAssessment(toJson(result.getRiskAssessment()))
                    .detailAnalysis(toJson(result.getDetailAnalysis()))
                    .recommendations(toJson(result.getRecommendations()))
                    .followUp(toJson(result.getFollowUp()))
                    .disclaimer(result.getDisclaimer())
                    .latencyMs(result.getLatencyMs())
                    .rawLlmResponse(result.getRawLlmResponse())
                    .build();
            reportMapper.insert(entity);
            log.info("MySQL保存分析报告: analysisId={}, sessionId={}", analysisId, session.getSessionId());
        } catch (Exception e) {
            log.warn("MySQL保存分析报告失败: sessionId={}", session.getSessionId(), e);
        }
    }

    @Override
    public Optional<AnalysisResult> getAnalysisReportBySessionId(String sessionId) {
        try {
            LambdaQueryWrapper<AnalysisReportEntity> query = new LambdaQueryWrapper<>();
            query.eq(AnalysisReportEntity::getSessionId, sessionId)
                    .orderByDesc(AnalysisReportEntity::getCreatedAt)
                    .last("LIMIT 1");
            AnalysisReportEntity entity = reportMapper.selectOne(query);
            if (entity == null) {
                return Optional.empty();
            }
            return Optional.of(toAnalysisResult(entity));
        } catch (Exception e) {
            log.warn("MySQL查询分析报告失败: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<AnalysisResult> getAnalysisReportByAnalysisId(String analysisId) {
        try {
            LambdaQueryWrapper<AnalysisReportEntity> query = new LambdaQueryWrapper<>();
            query.eq(AnalysisReportEntity::getAnalysisId, analysisId);
            AnalysisReportEntity entity = reportMapper.selectOne(query);
            if (entity == null) {
                return Optional.empty();
            }
            return Optional.of(toAnalysisResult(entity));
        } catch (Exception e) {
            log.warn("MySQL查询分析报告失败: analysisId={}", analysisId, e);
            return Optional.empty();
        }
    }

    // ==================== 私有工具方法 ====================

    private InterviewSessionEntity findBySessionId(String sessionId) {
        LambdaQueryWrapper<InterviewSessionEntity> query = new LambdaQueryWrapper<>();
        query.eq(InterviewSessionEntity::getSessionId, sessionId);
        return sessionMapper.selectOne(query);
    }

    private int getNextSequence(String sessionId) {
        LambdaQueryWrapper<InterviewMessageEntity> query = new LambdaQueryWrapper<>();
        query.eq(InterviewMessageEntity::getSessionId, sessionId)
                .orderByDesc(InterviewMessageEntity::getSequenceNum)
                .last("LIMIT 1");
        InterviewMessageEntity last = messageMapper.selectOne(query);
        return last != null ? last.getSequenceNum() + 1 : 1;
    }

    private InterviewSessionEntity toEntity(InterviewSession session) throws JsonProcessingException {
        return InterviewSessionEntity.builder()
                .sessionId(session.getSessionId())
                .questionnaireId(session.getQuestionnaireId())
                .questionnaireTitle(session.getQuestionnaireTitle())
                .userId(session.getUserId())
                .conversationId(session.getConversationId())
                .currentQuestionIndex(session.getCurrentQuestionIndex())
                .totalQuestions(session.getTotalQuestions())
                .answers(toJson(session.getAnswers()))
                .currentScore(session.getCurrentScore())
                .completed(session.isCompleted() ? 1 : 0)
                .collectionMode(session.getCollectionMode())
                .degradationLevel(session.getDegradationLevel())
                .rawInputs(toJson(session.getRawInputs()))
                .safetyFlags(toJson(session.getSafetyFlags()))
                .contextSummary(session.getContextSummary())
                .provenance(toJson(session.getProvenance()))
                .consecutiveFailures(session.getConsecutiveFailures())
                .createdAt(session.getCreatedAt())
                .lastActiveAt(session.getLastActiveAt())
                .build();
    }

    private InterviewSession fromEntity(InterviewSessionEntity entity) {
        try {
            return InterviewSession.builder()
                    .sessionId(entity.getSessionId())
                    .questionnaireId(entity.getQuestionnaireId())
                    .questionnaireTitle(entity.getQuestionnaireTitle())
                    .userId(entity.getUserId())
                    .conversationId(entity.getConversationId())
                    .currentQuestionIndex(entity.getCurrentQuestionIndex() != null
                            ? entity.getCurrentQuestionIndex() : 0)
                    .totalQuestions(entity.getTotalQuestions() != null
                            ? entity.getTotalQuestions() : 0)
                    .answers(parseMap(entity.getAnswers()))
                    .currentScore(entity.getCurrentScore() != null
                            ? entity.getCurrentScore() : 0)
                    .completed(entity.getCompleted() != null && entity.getCompleted() == 1)
                    .collectionMode(entity.getCollectionMode() != null
                            ? entity.getCollectionMode() : "manual_form")
                    .degradationLevel(entity.getDegradationLevel() != null
                            ? entity.getDegradationLevel() : "llm")
                    .rawInputs(parseMap(entity.getRawInputs()))
                    .safetyFlags(parseStringList(entity.getSafetyFlags()))
                    .contextSummary(entity.getContextSummary())
                    .provenance(parseProvenanceMap(entity.getProvenance()))
                    .consecutiveFailures(entity.getConsecutiveFailures() != null
                            ? entity.getConsecutiveFailures() : 0)
                    .createdAt(entity.getCreatedAt())
                    .lastActiveAt(entity.getLastActiveAt())
                    .build();
        } catch (Exception e) {
            log.warn("MySQL反序列化会话失败: sessionId={}", entity.getSessionId(), e);
            return null;
        }
    }

    private Map<String, Object> messageToMap(InterviewMessageEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("conversationId", entity.getConversationId());
        map.put("sessionId", entity.getSessionId());
        map.put("userId", entity.getUserId());
        map.put("messageType", entity.getMessageType());
        map.put("sequenceNum", entity.getSequenceNum());
        map.put("questionnaireId", entity.getQuestionnaireId());
        map.put("questionnaireTitle", entity.getQuestionnaireTitle());
        try {
            map.put("messageData", objectMapper.readValue(entity.getMessageData(),
                    new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            map.put("messageData", entity.getMessageData());
        }
        map.put("createdAt", entity.getCreatedAt() != null
                ? entity.getCreatedAt().toString() : null);
        return map;
    }

    private AnalysisResult toAnalysisResult(AnalysisReportEntity entity) {
        try {
            return AnalysisResult.builder()
                    .summary(entity.getSummary())
                    .riskAssessment(entity.getRiskAssessment() != null
                            ? objectMapper.readValue(entity.getRiskAssessment(),
                                    AnalysisResult.RiskAssessmentBlock.class)
                            : null)
                    .detailAnalysis(entity.getDetailAnalysis() != null
                            ? objectMapper.readValue(entity.getDetailAnalysis(),
                                    AnalysisResult.DetailAnalysisBlock.class)
                            : null)
                    .recommendations(entity.getRecommendations() != null
                            ? objectMapper.readValue(entity.getRecommendations(),
                                    AnalysisResult.RecommendationsBlock.class)
                            : null)
                    .followUp(entity.getFollowUp() != null
                            ? objectMapper.readValue(entity.getFollowUp(),
                                    AnalysisResult.FollowUpBlock.class)
                            : null)
                    .disclaimer(entity.getDisclaimer())
                    .latencyMs(entity.getLatencyMs() != null ? entity.getLatencyMs() : 0)
                    .rawLlmResponse(entity.getRawLlmResponse())
                    .build();
        } catch (Exception e) {
            log.warn("MySQL反序列化分析报告失败: analysisId={}", entity.getAnalysisId(), e);
            return AnalysisResult.builder()
                    .summary(entity.getSummary())
                    .build();
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> parseMap(String json) {
        if (json == null || json.isEmpty()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json,
                    new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            log.warn("JSON反序列化Map失败", e);
            return new LinkedHashMap<>();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<ArrayList<String>>() {});
        } catch (Exception e) {
            log.warn("JSON反序列化List失败", e);
            return new ArrayList<>();
        }
    }

    // ==================== FHIR 资源持久化 ====================

    @Override
    public void saveFhirDiagnosticReport(FhirDiagnosticReportCache report) {
        try {
            diagnosticReportMapper.insert(report);
            log.debug("MySQL保存FHIR诊断报告: fhirId={}, sessionId={}",
                    report.getFhirId(), report.getSessionId());
        } catch (Exception e) {
            log.warn("MySQL保存FHIR诊断报告失败: fhirId={}", report.getFhirId(), e);
        }
    }

    @Override
    public void saveFhirRiskAssessment(FhirRiskAssessmentCache risk) {
        try {
            riskAssessmentMapper.insert(risk);
            log.debug("MySQL保存FHIR风险评估: fhirId={}, sessionId={}",
                    risk.getFhirId(), risk.getSessionId());
        } catch (Exception e) {
            log.warn("MySQL保存FHIR风险评估失败: fhirId={}", risk.getFhirId(), e);
        }
    }

    @Override
    public Optional<FhirDiagnosticReportCache> getFhirDiagnosticReportBySession(String sessionId) {
        try {
            LambdaQueryWrapper<FhirDiagnosticReportCache> query = new LambdaQueryWrapper<>();
            query.eq(FhirDiagnosticReportCache::getSessionId, sessionId)
                    .orderByDesc(FhirDiagnosticReportCache::getCreatedAt)
                    .last("LIMIT 1");
            return Optional.ofNullable(diagnosticReportMapper.selectOne(query));
        } catch (Exception e) {
            log.warn("MySQL查询FHIR诊断报告失败: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<FhirRiskAssessmentCache> getFhirRiskAssessmentBySession(String sessionId) {
        try {
            LambdaQueryWrapper<FhirRiskAssessmentCache> query = new LambdaQueryWrapper<>();
            query.eq(FhirRiskAssessmentCache::getSessionId, sessionId)
                    .orderByDesc(FhirRiskAssessmentCache::getCreatedAt)
                    .last("LIMIT 1");
            return Optional.ofNullable(riskAssessmentMapper.selectOne(query));
        } catch (Exception e) {
            log.warn("MySQL查询FHIR风险评估失败: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    private Map<String, InterviewSession.Provenance> parseProvenanceMap(String json) {
        if (json == null || json.isEmpty()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json,
                    new TypeReference<LinkedHashMap<String, InterviewSession.Provenance>>() {});
        } catch (Exception e) {
            log.warn("JSON反序列化Provenance失败", e);
            return new LinkedHashMap<>();
        }
    }
}
