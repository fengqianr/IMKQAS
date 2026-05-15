package com.student.service.his.impl;

import com.student.dto.his.FhirConverter;
import com.student.entity.his.FhirQuestionnaireResponseCache;
import com.student.mapper.his.FhirQuestionnaireResponseCacheMapper;
import com.student.service.RedisService;
import com.student.service.his.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 填表引擎实现
 * 按需触发、用户主导：AI建议 → 用户确认 → 进入问答
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewEngineImpl implements InterviewEngine {

    private final QuestionnaireRepository questionnaireRepository;
    private final ConversationStateManagerImpl stateManager;
    private final FhirQuestionnaireResponseCacheMapper qrMapper;
    private final FhirConverter converter;
    private final RedisService redisService;

    private static final String SESSION_KEY_PREFIX = "his:interview:";
    private static final int SESSION_TTL_SECONDS = 1800;

    // 本地缓存（Redis降级时使用）
    private final Map<String, InterviewSession> localSessions = new ConcurrentHashMap<>();

    @Override
    public InterviewSuggestion suggestQuestionnaire(String userInput) {
        List<QuestionnaireTemplate> matches = questionnaireRepository.matchByKeywords(userInput);

        if (matches.isEmpty()) {
            return InterviewSuggestion.noMatch();
        }

        QuestionnaireTemplate best = matches.get(0);
        String suggestionText = String.format(
                "根据您的描述，我推荐您填写「%s」（%s，共%d题，约需%d分钟）。是否需要现在填写？",
                best.getTitle(), best.getDescription(),
                best.getItems().size(), best.getItems().size());

        return InterviewSuggestion.of(best, suggestionText, 0.85);
    }

    @Override
    public InterviewSession startInterview(String questionnaireId, Long userId, Long conversationId) {
        QuestionnaireTemplate template = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new IllegalArgumentException("问卷不存在: " + questionnaireId));

        InterviewSession session = InterviewSession.builder()
                .sessionId(UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .questionnaireId(questionnaireId)
                .questionnaireTitle(template.getTitle())
                .userId(userId)
                .conversationId(conversationId)
                .currentQuestionIndex(0)
                .totalQuestions(template.getItems().size())
                .answers(new LinkedHashMap<>())
                .currentScore(0)
                .completed(false)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();

        saveSession(session);
        stateManager.setPendingInterviewSessionId(conversationId, session.getSessionId());
        stateManager.transition(conversationId, ConversationState.QUESTIONNAIRE);

        log.info("开始填表: sessionId={}, questionnaire={}, userId={}",
                session.getSessionId(), questionnaireId, userId);
        return session;
    }

    @Override
    public InterviewResponse answerQuestion(String sessionId, String answer) {
        InterviewSession session = loadSession(sessionId);
        if (session == null) {
            throw new IllegalStateException("会话已过期或不存在: " + sessionId);
        }
        if (session.isCompleted()) {
            throw new IllegalStateException("该问卷已完成");
        }

        session.setLastActiveAt(LocalDateTime.now());

        QuestionnaireTemplate template = questionnaireRepository
                .findById(session.getQuestionnaireId())
                .orElseThrow(() -> new IllegalStateException("问卷模板已移除"));

        // 记录答案
        var currentItem = template.getItems().get(session.getCurrentQuestionIndex());
        session.getAnswers().put(currentItem.getLinkId(), answer);

        // 计算分数
        var option = currentItem.getOptions().stream()
                .filter(o -> o.getCode().equals(answer))
                .findAny();
        session.setCurrentScore(session.getCurrentScore()
                + option.map(QuestionnaireTemplate.AnswerOption::getScore).orElse(0));

        // 推进
        session.setCurrentQuestionIndex(session.getCurrentQuestionIndex() + 1);
        saveSession(session);

        // 是否完成
        if (session.getCurrentQuestionIndex() >= session.getTotalQuestions()) {
            session.setCompleted(true);
            saveSession(session);
            return buildCompletionResponse(session, template);
        }

        // 返回下一题
        var nextItem = template.getItems().get(session.getCurrentQuestionIndex());
        return InterviewResponse.builder()
                .sessionId(sessionId)
                .completed(false)
                .progress((session.getCurrentQuestionIndex()) + "/" + session.getTotalQuestions())
                .nextQuestion(InterviewResponse.QuestionInfo.builder()
                        .index(nextItem.getIndex())
                        .linkId(nextItem.getLinkId())
                        .text(nextItem.getText())
                        .options(nextItem.getOptions())
                        .build())
                .build();
    }

    @Override
    public org.hl7.fhir.r4.model.QuestionnaireResponse generateResponse(String sessionId) {
        InterviewSession session = loadSession(sessionId);
        if (session == null) {
            throw new IllegalStateException("会话已过期或不存在");
        }

        QuestionnaireTemplate template = questionnaireRepository
                .findById(session.getQuestionnaireId())
                .orElseThrow(() -> new IllegalStateException("问卷模板已移除"));

        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setId("qr-" + sessionId);
        qr.setQuestionnaire(session.getQuestionnaireId());
        qr.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED);
        qr.setAuthored(new Date());
        if (session.getUserId() != null) {
            qr.setSubject(new Reference("Patient/pat-" + session.getUserId()));
        }

        // 构建 item 树
        for (var templateItem : template.getItems()) {
            String answerCode = session.getAnswers().get(templateItem.getLinkId());
            QuestionnaireResponse.QuestionnaireResponseItemComponent item =
                    qr.addItem();
            item.setLinkId(templateItem.getLinkId());
            item.setText(templateItem.getText());
            if (answerCode != null) {
                var opt = templateItem.getOptions().stream()
                        .filter(o -> o.getCode().equals(answerCode)).findAny();
                item.addAnswer().setValue(new Coding()
                        .setCode(answerCode)
                        .setDisplay(opt.map(QuestionnaireTemplate.AnswerOption::getDisplay).orElse("")));
            }
        }

        return qr;
    }

    @Override
    public InterviewSession resumeInterview(String sessionId) {
        InterviewSession session = loadSession(sessionId);
        if (session == null) {
            throw new IllegalStateException("会话已过期或不存在");
        }
        if (session.isCompleted()) {
            throw new IllegalStateException("该问卷已完成");
        }
        session.setLastActiveAt(LocalDateTime.now());
        saveSession(session);

        if (session.getConversationId() != null) {
            stateManager.transition(session.getConversationId(), ConversationState.QUESTIONNAIRE);
        }

        log.info("恢复填表: sessionId={}, progress={}/{}",
                sessionId, session.getCurrentQuestionIndex(), session.getTotalQuestions());
        return session;
    }

    @Override
    public void cancelInterview(String sessionId) {
        InterviewSession session = loadSession(sessionId);
        if (session == null) return;

        if (session.getConversationId() != null) {
            stateManager.clear(session.getConversationId());
        }
        redisService.delete(SESSION_KEY_PREFIX + sessionId);
        localSessions.remove(sessionId);

        log.info("取消填表: sessionId={}", sessionId);
    }

    @Override
    public List<Map<String, Object>> getHistory(Long userId, String questionnaireId) {
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FhirQuestionnaireResponseCache>();
        query.eq(FhirQuestionnaireResponseCache::getLocalUserId, userId);
        if (questionnaireId != null) {
            query.eq(FhirQuestionnaireResponseCache::getQuestionnaireId, questionnaireId);
        }
        query.orderByDesc(FhirQuestionnaireResponseCache::getAuthoredDate);

        return qrMapper.selectList(query).stream()
                .map(this::toHistoryMap)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getTrend(Long userId, String questionnaireId) {
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FhirQuestionnaireResponseCache>();
        query.eq(FhirQuestionnaireResponseCache::getLocalUserId, userId)
                .eq(FhirQuestionnaireResponseCache::getQuestionnaireId, questionnaireId)
                .orderByAsc(FhirQuestionnaireResponseCache::getAuthoredDate);

        return qrMapper.selectList(query).stream()
                .map(r -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", r.getAuthoredDate() != null
                            ? r.getAuthoredDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
                    point.put("score", r.getTotalScore());
                    point.put("severity", r.getScoreInterpretation());
                    return point;
                })
                .collect(Collectors.toList());
    }

    // ==================== 私有工具方法 ====================

    private InterviewResponse buildCompletionResponse(InterviewSession session,
                                                       QuestionnaireTemplate template) {
        var rule = template.getScoringRule();
        String severity = "未知";
        String interpretation = "";
        for (var range : rule.getRanges()) {
            if (session.getCurrentScore() >= range.getMinScore()
                    && session.getCurrentScore() <= range.getMaxScore()) {
                severity = range.getSeverity();
                interpretation = range.getInterpretation();
                break;
            }
        }

        // 检查是否有历史记录
        List<Map<String, Object>> history = getHistory(session.getUserId(),
                session.getQuestionnaireId());
        boolean hasHistory = history.size() > 1;
        String trendDesc = null;
        if (hasHistory && history.get(1).get("score") != null) {
            double prevScore = ((Number) history.get(1).get("score")).doubleValue();
            if (session.getCurrentScore() < prevScore) {
                trendDesc = "相比上次评分有改善（下降" + String.format("%.1f", prevScore - session.getCurrentScore()) + "分）";
            } else if (session.getCurrentScore() > prevScore) {
                trendDesc = "相比上次评分有所升高（上升" + String.format("%.1f", session.getCurrentScore() - prevScore) + "分）";
            } else {
                trendDesc = "与上次评分持平";
            }
        }

        return InterviewResponse.builder()
                .sessionId(session.getSessionId())
                .completed(true)
                .progress(session.getTotalQuestions() + "/" + session.getTotalQuestions())
                .summary(InterviewResponse.CompletionSummary.builder()
                        .totalScore(session.getCurrentScore())
                        .maxScore(rule.getMaxScore())
                        .severity(severity)
                        .interpretation(interpretation)
                        .hasHistory(hasHistory)
                        .trendDescription(trendDesc)
                        .build())
                .build();
    }

    private void saveSession(InterviewSession session) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(session);
            redisService.set(SESSION_KEY_PREFIX + session.getSessionId(),
                    json, (long) SESSION_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("Redis保存会话失败，使用本地缓存: {}", e.getMessage());
            localSessions.put(session.getSessionId(), session);
        }
    }

    private InterviewSession loadSession(String sessionId) {
        try {
            Object value = redisService.get(SESSION_KEY_PREFIX + sessionId);
            if (value != null) {
                String json = value.toString();
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(json, InterviewSession.class);
            }
        } catch (Exception e) {
            log.warn("Redis读取会话失败，尝试本地缓存: {}", e.getMessage());
        }
        return localSessions.get(sessionId);
    }

    private Map<String, Object> toHistoryMap(FhirQuestionnaireResponseCache r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fhirId", r.getFhirId());
        map.put("questionnaireId", r.getQuestionnaireId());
        map.put("questionnaireTitle", r.getQuestionnaireTitle());
        map.put("score", r.getTotalScore());
        map.put("severity", r.getScoreInterpretation());
        map.put("authoredDate", r.getAuthoredDate());
        map.put("status", r.getStatus());
        return map;
    }
}
