package com.student.controller.his;

import com.student.dto.ApiResponse;
import com.student.entity.his.FhirDiagnosticReportCache;
import com.student.entity.his.FhirRiskAssessmentCache;
import com.student.mapper.his.FhirDiagnosticReportCacheMapper;
import com.student.mapper.his.FhirRiskAssessmentCacheMapper;
import com.student.service.his.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * AI填表助手控制器
 * 按需触发、用户主导：AI建议 → 用户确认 → 进入问答
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/his/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewEngine engine;
    private final QuestionnaireRepository questionnaireRepository;
    private final CollectionSseEmitter sseEmitter;
    private final FhirDiagnosticReportCacheMapper diagnosticReportMapper;
    private final FhirRiskAssessmentCacheMapper riskAssessmentMapper;

    /**
     * AI问卷建议 — 根据用户描述匹配推荐问卷
     */
    @PostMapping("/suggest")
    @SuppressWarnings("unchecked")
    public ApiResponse<InterviewSuggestion> suggest(@RequestBody Map<String, String> body) {
        String userInput = body.get("userInput");
        if (userInput == null || userInput.isBlank()) {
            return (ApiResponse<InterviewSuggestion>) (Object) ApiResponse.error("用户输入不能为空");
        }
        InterviewSuggestion suggestion = engine.suggestQuestionnaire(userInput);
        return ApiResponse.success(suggestion);
    }

    /**
     * 用户确认后，开始填表
     */
    @PostMapping("/start")
    @SuppressWarnings("unchecked")
    public ApiResponse<InterviewSession> start(@RequestBody Map<String, String> body) {
        String questionnaireId = body.get("questionnaireId");
        Long userId = parseLong(body.get("userId"));
        Long conversationId = parseLong(body.get("conversationId"));

        if (questionnaireId == null || questionnaireId.isBlank()) {
            return (ApiResponse<InterviewSession>) (Object) ApiResponse.error("问卷ID不能为空");
        }

        InterviewSession session = engine.startInterview(questionnaireId, userId, conversationId);
        return ApiResponse.success(session);
    }

    /**
     * 提交答案 — 返回下一题或完成结果
     */
    @PostMapping("/answer")
    @SuppressWarnings("unchecked")
    public ApiResponse<InterviewResponse> answer(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String answer = body.get("answer");

        if (sessionId == null || sessionId.isBlank()) {
            return (ApiResponse<InterviewResponse>) (Object) ApiResponse.error("会话ID不能为空");
        }
        if (answer == null || answer.isBlank()) {
            return (ApiResponse<InterviewResponse>) (Object) ApiResponse.error("答案不能为空");
        }

        InterviewResponse response = engine.answerQuestion(sessionId, answer);
        return ApiResponse.success(response);
    }

    /**
     * LLM驱动填表：启动会话并返回首题（SSE流式）
     */
    @PostMapping(value = "/start-llm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startLlm(@RequestBody Map<String, String> body) {
        String questionnaireId = body.get("questionnaireId");
        log.info("收到LLM启动请求: questionnaireId={}, userId={}, conversationId={}",
                questionnaireId, body.get("userId"), body.get("conversationId"));
        Long userId = parseLong(body.get("userId"));
        Long conversationId = parseLong(body.get("conversationId"));

        if (questionnaireId == null || questionnaireId.isBlank()) {
            SseEmitter errEmitter = new SseEmitter();
            try {
                errEmitter.send(SseEmitter.event().id("error")
                        .data(Map.of("type", "error", "message", "问卷ID不能为空")));
                errEmitter.complete();
            } catch (IOException ignored) {}
            return errEmitter;
        }

        SseEmitter emitter = sseEmitter.createEmitter(180_000L);

        CompletableFuture.runAsync(() -> {
            try {
                InterviewSession session = engine.startLlmInterview(
                        questionnaireId, userId, conversationId);

                QuestionnaireTemplate template = questionnaireRepository
                        .findById(questionnaireId).orElse(null);
                if (template == null || template.getItems().isEmpty()) {
                    sseEmitter.sendError(emitter, "问卷模板无效");
                    sseEmitter.complete(emitter);
                    return;
                }

                // 发送首题
                var firstItem = template.getItems().get(0);
                sseEmitter.sendQuestion(emitter,
                        CollectionToolOutputs.AskQuestionOutput.builder()
                                .linkId(firstItem.getLinkId())
                                .text(firstItem.getText())
                                .currentIndex(0)
                                .totalQuestions(template.getItems().size())
                                .options(firstItem.getOptions())
                                .build(),
                        session.getSessionId());
                sseEmitter.sendDone(emitter);
                sseEmitter.complete(emitter);
            } catch (Exception e) {
                log.error("启动LLM填表失败", e);
                sseEmitter.completeWithError(emitter, e);
            }
        });

        return emitter;
    }

    /**
     * LLM驱动填表：处理用户自然语言输入（SSE流式）
     */
    @PostMapping(value = "/llm-answer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter llmAnswer(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String userInput = body.get("userInput");
        log.info("收到LLM回答请求: sessionId={}, inputLen={}", sessionId,
                userInput != null ? userInput.length() : 0);

        if (sessionId == null || sessionId.isBlank()) {
            SseEmitter errEmitter = new SseEmitter();
            try {
                errEmitter.send(SseEmitter.event().id("error")
                        .data(Map.of("type", "error", "message", "会话ID不能为空")));
                errEmitter.complete();
            } catch (IOException ignored) {}
            return errEmitter;
        }

        SseEmitter emitter = sseEmitter.createEmitter(180_000L);

        CompletableFuture.runAsync(() -> {
            try {
                CollectionToolOutputs.AgentResult result = engine.processLlmTurn(sessionId, userInput);
                emitAgentResult(emitter, result, sessionId);
                sseEmitter.sendDone(emitter);
                sseEmitter.complete(emitter);
            } catch (Exception e) {
                log.error("LLM填表处理失败: sessionId={}", sessionId, e);
                sseEmitter.completeWithError(emitter, e);
            }
        });

        return emitter;
    }

    /**
     * 将AgentResult转换为SSE事件发送
     */
    private void emitAgentResult(SseEmitter emitter, CollectionToolOutputs.AgentResult result,
                                  String sessionId) {
        // 先发送降级通知（如果已降级）
        if (result.getDegradationLevel() != null
                && !"llm".equals(result.getDegradationLevel())) {
            String reason = switch (result.getDegradationLevel()) {
                case "rule_parser" -> "LLM暂时不可用，已切换至规则解析器模式";
                case "manual_form" -> "自动理解失败，已切换至手动选择模式，请直接输入数字编号";
                default -> "已降级至" + result.getDegradationLevel();
            };
            sseEmitter.sendDegradationNotice(emitter, result.getDegradationLevel(), reason);
        }

        switch (result.getType()) {
            case ASK_QUESTION -> sseEmitter.sendQuestion(emitter, result.getAskQuestion(), sessionId);
            case RECORD_ANSWER -> {
                if (result.getRecordAnswer() != null) {
                    sseEmitter.sendProgress(emitter,
                            result.getRecordAnswer().getLinkId().hashCode() & 0x7FFFFFFF, 0);
                }
            }
            case CLARIFY -> sseEmitter.sendClarify(emitter, result.getClarify(), sessionId);
            case COMPLETE -> {
                var c = result.getComplete();
                sseEmitter.sendComplete(emitter, c,
                        c.getTotalScore(), c.getMaxScore(),
                        c.getSeverity(), c.getInterpretation(),
                        c.getAnalysisSummary());
            }
            case EMERGENCY_INTERRUPT -> sseEmitter.sendSafetyAlert(emitter, result.getEmergencyInterrupt());
        }
    }

    /**
     * 生成FHIR QuestionnaireResponse
     */
    @GetMapping("/{sessionId}/fhir")
    public ApiResponse<QuestionnaireResponse> generateFhir(@PathVariable String sessionId) {
        QuestionnaireResponse qr = engine.generateResponse(sessionId);
        return ApiResponse.success(qr);
    }

    /**
     * 恢复中断的填表会话
     */
    @PostMapping("/{sessionId}/resume")
    public ApiResponse<InterviewSession> resume(@PathVariable String sessionId) {
        InterviewSession session = engine.resumeInterview(sessionId);
        return ApiResponse.success(session);
    }

    /**
     * 取消填表
     */
    @PostMapping("/{sessionId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String sessionId) {
        engine.cancelInterview(sessionId);
        return ApiResponse.success();
    }

    /**
     * 查询历史填写记录
     */
    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> getHistory(@RequestParam Long userId,
                                                              @RequestParam(required = false) String questionnaireId) {
        return ApiResponse.success(engine.getHistory(userId, questionnaireId));
    }

    /**
     * 获取评分趋势数据（用于图表展示）
     */
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> getTrend(@RequestParam Long userId,
                                                            @RequestParam String questionnaireId) {
        return ApiResponse.success(engine.getTrend(userId, questionnaireId));
    }

    /**
     * 获取所有可用问卷列表
     */
    @GetMapping("/questionnaires")
    public ApiResponse<List<QuestionnaireTemplate>> listQuestionnaires() {
        return ApiResponse.success(questionnaireRepository.findAll());
    }

    /**
     * 获取指定问卷详情
     */
    @GetMapping("/questionnaires/{id}")
    @SuppressWarnings("unchecked")
    public ApiResponse<QuestionnaireTemplate> getQuestionnaire(@PathVariable String id) {
        return questionnaireRepository.findById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<QuestionnaireTemplate>) (Object) ApiResponse.error("问卷不存在: " + id));
    }

    /**
     * 获取会话的访谈消息列表（供前端重建问卷卡片）
     */
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<Map<String, Object>>> getSessionMessages(@PathVariable String sessionId) {
        return ApiResponse.success(engine.getInterviewMessages(sessionId));
    }

    /**
     * 获取完整AI分析报告
     */
    @GetMapping("/{sessionId}/analysis")
    @SuppressWarnings("unchecked")
    public ApiResponse<AnalysisResult> getAnalysisReport(@PathVariable String sessionId) {
        Optional<AnalysisResult> report = engine.getAnalysisReport(sessionId);
        return report.map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<AnalysisResult>) (Object) ApiResponse.error("分析报告不存在"));
    }

    /**
     * 获取对话下的所有访谈记录
     */
    @GetMapping("/by-conversation/{conversationId}")
    public ApiResponse<List<Map<String, Object>>> getInterviewsByConversation(
            @PathVariable Long conversationId) {
        return ApiResponse.success(engine.getInterviewsByConversation(conversationId));
    }

    /**
     * 获取会话的FHIR DiagnosticReport（JSON格式）
     */
    @GetMapping("/{sessionId}/fhir/diagnostic-report")
    @SuppressWarnings("unchecked")
    public ApiResponse<String> getFhirDiagnosticReport(@PathVariable String sessionId) {
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FhirDiagnosticReportCache>();
        query.eq(FhirDiagnosticReportCache::getSessionId, sessionId)
                .orderByDesc(FhirDiagnosticReportCache::getCreatedAt)
                .last("LIMIT 1");
        FhirDiagnosticReportCache entity = diagnosticReportMapper.selectOne(query);
        if (entity == null) {
            return (ApiResponse<String>) (Object) ApiResponse.error("FHIR诊断报告不存在");
        }
        return ApiResponse.success(entity.getResourceJson());
    }

    /**
     * 获取会话的FHIR RiskAssessment（JSON格式）
     */
    @GetMapping("/{sessionId}/fhir/risk-assessment")
    @SuppressWarnings("unchecked")
    public ApiResponse<String> getFhirRiskAssessment(@PathVariable String sessionId) {
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FhirRiskAssessmentCache>();
        query.eq(FhirRiskAssessmentCache::getSessionId, sessionId)
                .orderByDesc(FhirRiskAssessmentCache::getCreatedAt)
                .last("LIMIT 1");
        FhirRiskAssessmentCache entity = riskAssessmentMapper.selectOne(query);
        if (entity == null) {
            return (ApiResponse<String>) (Object) ApiResponse.error("FHIR风险评估不存在");
        }
        return ApiResponse.success(entity.getResourceJson());
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
