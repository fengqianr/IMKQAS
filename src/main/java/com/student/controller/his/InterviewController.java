package com.student.controller.his;

import com.student.dto.ApiResponse;
import com.student.service.his.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
