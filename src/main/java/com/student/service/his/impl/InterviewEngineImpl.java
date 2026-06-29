package com.student.service.his.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CompletableFuture;
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
    private final CollectionSubAgent collectionSubAgent;
    private final TransformationPipeline transformationPipeline;
    private final AnalysisAgent analysisAgent;
    private final InterviewPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

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
        return startInterviewInternal(questionnaireId, userId, conversationId, "manual_form");
    }

    @Override
    public InterviewSession startLlmInterview(String questionnaireId, Long userId, Long conversationId) {
        return startInterviewInternal(questionnaireId, userId, conversationId, "llm_driven");
    }

    private InterviewSession startInterviewInternal(String questionnaireId, Long userId,
                                                     Long conversationId, String collectionMode) {
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
                .rawInputs(new LinkedHashMap<>())
                .safetyFlags(new java.util.ArrayList<>())
                .currentScore(0)
                .completed(false)
                .collectionMode(collectionMode)
                .degradationLevel("llm")
                .consecutiveFailures(0)
                .contextSummary("")
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();

        saveSession(session);
        stateManager.setPendingInterviewSessionId(conversationId, session.getSessionId());
        stateManager.transition(conversationId, ConversationState.QUESTIONNAIRE);

        // 持久化第一道题，确保会话恢复时首题可渲染
        if (!template.getItems().isEmpty()) {
            var firstItem = template.getItems().get(0);
            persistInterviewMessage(session, "question", buildQuestionMsgData(
                    firstItem.getLinkId(), firstItem.getText(), 0,
                    template.getItems().size(), firstItem.getOptions()));
        }

        log.info("开始填表: sessionId={}, questionnaire={}, userId={}, mode={}",
                session.getSessionId(), questionnaireId, userId, collectionMode);
        return session;
    }

    @Override
    public InterviewResponse answerQuestion(String sessionId, String answer) {
        log.info("手动答题: sessionId={}, answer={}", sessionId, answer);
        InterviewSession session = loadSession(sessionId);
        if (session == null) {
            throw new IllegalStateException("会话已过期或不存在: " + sessionId);
        }
        if (session.isCompleted()) {
            throw new IllegalStateException("该问卷已完成");
        }

        QuestionnaireTemplate template = questionnaireRepository
                .findById(session.getQuestionnaireId())
                .orElseThrow(() -> new IllegalStateException("问卷模板已移除"));

        var currentItem = template.getItems().get(session.getCurrentQuestionIndex());
        var option = currentItem.getOptions().stream()
                .filter(o -> o.getCode().equals(answer))
                .findAny();

        // 持久化用户选择（异步）
        Map<String, Object> userMsgData = new LinkedHashMap<>();
        userMsgData.put("text", answer);
        userMsgData.put("display", option.map(QuestionnaireTemplate.AnswerOption::getDisplay).orElse(""));
        userMsgData.put("linkId", currentItem.getLinkId());
        userMsgData.put("questionText", currentItem.getText());
        userMsgData.put("degradationLevel", "manual_form");
        userMsgData.put("progress", (session.getCurrentQuestionIndex() + 1) + "/" + session.getTotalQuestions());
        persistInterviewMessage(session, "user_message", userMsgData);

        return recordAnswer(sessionId,
                currentItem.getLinkId(),
                answer,
                option.map(QuestionnaireTemplate.AnswerOption::getDisplay).orElse(""),
                option.map(QuestionnaireTemplate.AnswerOption::getScore).orElse(0),
                "",
                "manual",
                1.0,
                "");
    }

    @Override
    public InterviewResponse recordAnswer(String sessionId, String linkId, String code,
                                           String display, int value, String rawInput,
                                           String source, double confidence, String contextSummary) {
        log.info("记录答案: sessionId={}, linkId={}, code={}, value={}, source={}, confidence={}",
                sessionId, linkId, code, value, source, confidence);
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
        session.getAnswers().put(linkId, code);
        session.setCurrentScore(session.getCurrentScore() + value);

        // 记录原始输入（LLM模式）
        if (rawInput != null && !rawInput.isEmpty()) {
            session.getRawInputs().put(linkId, rawInput);
        }

        // 记录溯源信息（source 归一化：manual → direct_input）
        if (source != null && !source.isEmpty()) {
            String normalizedSource = "manual".equals(source) ? "direct_input" : source;
            session.getProvenance().put(linkId, InterviewSession.Provenance.builder()
                    .source(normalizedSource)
                    .confidence(confidence)
                    .build());
        }

        // T1: 增量更新上下文摘要（每完成一题后执行）
        appendContextSummary(session, linkId, display, value, source, contextSummary);

        // 推进题号
        session.setCurrentQuestionIndex(session.getCurrentQuestionIndex() + 1);

        // 检查是否完成
        if (session.getCurrentQuestionIndex() >= session.getTotalQuestions()) {
            session.setCompleted(true);
            log.info("问卷全部答完，开始构建完成响应: sessionId={}", sessionId);
            saveSession(session);
            return buildCompletionResponse(session, template);
        }

        saveSession(session);

        // 返回下一题
        var nextItem = template.getItems().get(session.getCurrentQuestionIndex());
        log.info("下一题: sessionId={}, linkId={}, text={}",
                sessionId, nextItem.getLinkId(), nextItem.getText());
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
    public CollectionToolOutputs.AgentResult processLlmTurn(String sessionId, String userInput) {
        InterviewSession session = loadSession(sessionId);
        if (session == null) {
            throw new IllegalStateException("会话已过期或不存在: " + sessionId);
        }
        if (session.isCompleted()) {
            return CollectionToolOutputs.AgentResult.complete(
                    CollectionToolOutputs.CompleteOutput.builder()
                            .message("该问卷已完成")
                            .build());
        }

        session.setLastActiveAt(LocalDateTime.now());

        QuestionnaireTemplate template = questionnaireRepository
                .findById(session.getQuestionnaireId())
                .orElseThrow(() -> new IllegalStateException("问卷模板已移除"));

        // 持久化用户消息（异步，不阻塞主流程）
        persistUserMessage(session, template, userInput);

        log.info("LLM轮次处理: sessionId={}, progress={}/{}, degradationLevel={}, userInput={}",
                sessionId, session.getCurrentQuestionIndex() + 1, session.getTotalQuestions(),
                session.getDegradationLevel(),
                userInput.length() > 80 ? userInput.substring(0, 80) + "..." : userInput);

        // 三级纯表单模式：用户输入数字编号，直接映射
        CollectionToolOutputs.AgentResult result;
        if ("manual_form".equals(session.getDegradationLevel())) {
            CollectionToolOutputs.AgentResult formResult = handleManualFormInput(
                    session, userInput, template);
            if (formResult != null) {
                formResult.setDegradationLevel("manual_form");
                result = formResult;
                log.info("纯表单模式处理: sessionId={}, type={}", sessionId, result.getType());
            } else {
                // 纯表单也无法解析，回退到正常流程
                result = collectionSubAgent.processUserInput(session, userInput, template);
            }
        } else {
            // 调用CollectionSubAgent进行LLM语义理解
            result = collectionSubAgent.processUserInput(session, userInput, template);
        }

        log.info("LLM语义理解结果: sessionId={}, type={}, latency={}ms, degradationLevel={}",
                sessionId, result.getType(), result.getLatencyMs(), session.getDegradationLevel());

        // 传播降级层级到AgentResult（供SSE层发送降级通知）
        if (result.getDegradationLevel() == null) {
            result.setDegradationLevel(session.getDegradationLevel());
        }

        // 检测降级
        if (session.getDegradationLevel() != null && !"llm".equals(session.getDegradationLevel())) {
            log.warn("采集已降级: sessionId={}, level={}", sessionId, session.getDegradationLevel());
        }

        // record_answer类型：自动持久化并推进到下一题
        if (result.getType() == CollectionToolOutputs.OutputType.RECORD_ANSWER) {
            log.info("LLM识别为答案记录，开始持久化: sessionId={}, linkId={}, code={}",
                    sessionId, result.getRecordAnswer().getLinkId(),
                    result.getRecordAnswer().getCode());
            var record = result.getRecordAnswer();
            try {
                InterviewResponse resp = recordAnswer(sessionId,
                        record.getLinkId(),
                        record.getCode(),
                        record.getDisplay(),
                        record.getValue(),
                        record.getRawInput() != null ? record.getRawInput() : userInput,
                        record.getSource() != null ? record.getSource() : "llm",
                        record.getConfidence(),
                        record.getContextSummary() != null ? record.getContextSummary() : "");

                // 优先从本地缓存重新加载（recordAnswer已同步更新本地缓存），
                // 避免Redis异步写入竞态导致loadSession从Redis读到旧状态
                session = localSessions.get(sessionId);
                if (session == null) {
                    session = loadSession(sessionId);
                }

                // 如果纯表单模式成功记录了答案，尝试恢复LLM模式（断路器可能已恢复）
                if ("manual_form".equals(session.getDegradationLevel())
                        && "manual_form".equals(record.getSource())) {
                    session.setDegradationLevel("llm");
                    session.setConsecutiveFailures(0);
                    log.info("纯表单模式成功回答，恢复LLM模式: sessionId={}", sessionId);
                }

                if (resp.isCompleted() && resp.getSummary() != null) {
                    var summary = resp.getSummary();
                    result = CollectionToolOutputs.AgentResult.complete(
                            CollectionToolOutputs.CompleteOutput.builder()
                                    .message(String.format("评估完成，总分%d/%d，严重程度：%s",
                                            summary.getTotalScore(), summary.getMaxScore(),
                                            summary.getSeverity()))
                                    .totalScore(summary.getTotalScore())
                                    .maxScore(summary.getMaxScore())
                                    .severity(summary.getSeverity())
                                    .interpretation(summary.getInterpretation() != null
                                            ? summary.getInterpretation() : "")
                                    .analysisSummary(summary.getAnalysisSummary())
                                    .analysisId(summary.getAnalysisId())
                                    .build());
                    // 持久化完成消息
                    persistInterviewMessage(session, "completion", buildCompletionMsgData(
                            summary.getTotalScore(), summary.getMaxScore(),
                            summary.getSeverity(), summary.getInterpretation(),
                            summary.getAnalysisSummary(), summary.getAnalysisId()));
                } else if (!resp.isCompleted() && resp.getNextQuestion() != null) {
                    var next = resp.getNextQuestion();
                    result = CollectionToolOutputs.AgentResult.askQuestion(
                            CollectionToolOutputs.AskQuestionOutput.builder()
                                    .linkId(next.getLinkId())
                                    .text(next.getText())
                                    .currentIndex(session.getCurrentQuestionIndex())
                                    .totalQuestions(session.getTotalQuestions())
                                    .options(next.getOptions())
                                    .build());
                    // 题目卡片由 persistAgentResultMessage 统一持久化，此处不重复调用
                }
            } catch (Exception e) {
                log.error("持久化答案失败: sessionId={}, linkId={}", sessionId, record.getLinkId(), e);
            }
        }

        // 持久化非record_answer类型的LLM结果消息
        persistAgentResultMessage(session, result);

        saveSession(session);
        return result;
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

        // 优先使用管道完整组装
        TransformationPipeline.PipelineResult result = transformationPipeline.execute(session, template);
        if (result.isSuccess() && result.getQuestionnaireResponse() != null) {
            return result.getQuestionnaireResponse();
        }

        // 降级：基础组装
        log.warn("管道组装失败，使用基础降级组装: sessionId={}", sessionId);
        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setId("qr-" + sessionId);
        qr.setQuestionnaire(session.getQuestionnaireId());
        qr.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED);
        qr.setAuthored(new Date());
        if (session.getUserId() != null) {
            qr.setSubject(new Reference("Patient/pat-" + session.getUserId()));
        }

        for (var templateItem : template.getItems()) {
            String answerCode = session.getAnswers().get(templateItem.getLinkId());
            QuestionnaireResponse.QuestionnaireResponseItemComponent item = qr.addItem();
            item.setLinkId(templateItem.getLinkId());
            item.setText(templateItem.getText());
            if (answerCode != null) {
                var opt = templateItem.getOptions().stream()
                        .filter(o -> o.getCode().equals(answerCode)).findAny();
                String codeSystem = template.getCodeSystem() != null
                        ? template.getCodeSystem()
                        : "http://imkqas.org/fhir/CodeSystem/" + template.getId();
                item.addAnswer().setValue(new Coding()
                        .setSystem(codeSystem)
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

        // 异步逻辑删除MySQL中的记录
        CompletableFuture.runAsync(() -> {
            try {
                persistenceService.deleteSession(sessionId);
            } catch (Exception e) {
                log.warn("MySQL删除会话失败: sessionId={}", sessionId, e);
            }
        });

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

    // ==================== T1: 增量摘要 ====================

    /** 上下文摘要最大字符数，防止无限膨胀 */
    private static final int CONTEXT_SUMMARY_MAX_CHARS = 500;

    /**
     * T1 增量摘要 —— 每完成一题后生成摘要行追加到 contextSummary
     * <p>
     * 优先级：LLM 生成的 contextSummary > 结构化自动生成
     * 限制：总长度超过 {@link #CONTEXT_SUMMARY_MAX_CHARS} 时停止追加，末尾标记 "..."
     */
    private void appendContextSummary(InterviewSession session, String linkId,
                                      String display, int value, String source,
                                      String llmContextSummary) {
        if (session.getContextSummary() == null) {
            session.setContextSummary("");
        }

        // 已达上限，不再追加
        if (session.getContextSummary().length() >= CONTEXT_SUMMARY_MAX_CHARS) {
            log.debug("T1 摘要已达上限，跳过追加: sessionId={}, currentLen={}, maxLen={}",
                    session.getSessionId(), session.getContextSummary().length(),
                    CONTEXT_SUMMARY_MAX_CHARS);
            return;
        }

        String summaryLine;
        boolean fromLlm = llmContextSummary != null && !llmContextSummary.isEmpty();
        if (fromLlm) {
            summaryLine = llmContextSummary.trim();
            if (!summaryLine.endsWith("。") && !summaryLine.endsWith("；")
                    && !summaryLine.endsWith(";")) {
                summaryLine += "；";
            }
        } else {
            String sourceTag = switch (source) {
                case "llm" -> "LLM";
                case "rule_parser" -> "规则";
                default -> "手动";
            };
            summaryLine = String.format("[%s][%s] 得分:%d → %s；",
                    sourceTag, linkId, value, display);
        }

        // 长度保护：若追加后超限，截断并加省略标记
        int currentLen = session.getContextSummary().length();
        int newLen = currentLen + summaryLine.length();
        boolean truncated = false;
        if (newLen > CONTEXT_SUMMARY_MAX_CHARS) {
            int remaining = CONTEXT_SUMMARY_MAX_CHARS - currentLen - 3;
            if (remaining > 0) {
                session.setContextSummary(session.getContextSummary()
                        + summaryLine.substring(0, remaining) + "...");
            } else {
                session.setContextSummary(session.getContextSummary() + "...");
            }
            truncated = true;
        } else {
            session.setContextSummary(session.getContextSummary() + summaryLine);
        }

        log.info("T1 增量摘要: sessionId={}, linkId={}, source={}, fromLlm={}, "
                + "summaryLen={}, ctxBefore={}, ctxAfter={}, capped={}",
                session.getSessionId(), linkId, source, fromLlm,
                summaryLine.length(), currentLen,
                session.getContextSummary().length(), truncated);
    }

    // ==================== 私有工具方法 ====================

    private InterviewResponse buildCompletionResponse(InterviewSession session,
                                                       QuestionnaireTemplate template) {
        log.info("构建完成响应: sessionId={}, totalScore={}, answers={}/{}",
                session.getSessionId(), session.getCurrentScore(),
                session.getAnswers().size(), session.getTotalQuestions());

        // 执行转换管道
        TransformationPipeline.PipelineResult pipelineResult =
                transformationPipeline.execute(session, template);

        if (!pipelineResult.isSuccess()) {
            log.error("转换管道失败，使用降级逻辑: sessionId={}, errors={}",
                    session.getSessionId(), pipelineResult.getValidationErrors());
            return buildFallbackCompletionResponse(session, template);
        }

        log.info("管道计算完成: riskLevel={}, totalScore={}/{}, comboFlags={}, safetyFlags={}",
                pipelineResult.getRiskLevel(), pipelineResult.getTotalScore(),
                pipelineResult.getMaxScore(),
                pipelineResult.getComboFlags() != null ? pipelineResult.getComboFlags().size() : 0,
                pipelineResult.getSafetyFlags() != null ? pipelineResult.getSafetyFlags().size() : 0);

        // 检查历史记录计算趋势
        List<Map<String, Object>> history = getHistory(session.getUserId(),
                session.getQuestionnaireId());
        boolean hasHistory = history.size() > 1;
        String trendDesc = null;
        if (hasHistory && history.get(1).get("score") != null) {
            double prevScore = ((Number) history.get(1).get("score")).doubleValue();
            int totalScore = pipelineResult.getTotalScore();
            if (totalScore < prevScore) {
                trendDesc = "相比上次评分有改善（下降" + String.format("%.1f", prevScore - totalScore) + "分）";
            } else if (totalScore > prevScore) {
                trendDesc = "相比上次评分有所升高（上升" + String.format("%.1f", totalScore - prevScore) + "分）";
            } else {
                trendDesc = "与上次评分持平";
            }
        }

        // 同步执行分析层
        String analysisSummary = runSyncAnalysis(session, template, pipelineResult);

        // 异步持久化FHIR资源（不阻塞响应返回）
        if (pipelineResult.getQuestionnaireResponse() != null) {
            var qr = pipelineResult.getQuestionnaireResponse();
            var uid = session.getUserId();
            var qid = session.getQuestionnaireId();
            var title = template.getTitle();
            var totalScore = (double) pipelineResult.getTotalScore();
            var riskLevel = pipelineResult.getRiskLevel();
            var convId = session.getConversationId();
            var sid = session.getSessionId();
            CompletableFuture.runAsync(() -> {
                try {
                    var cache = converter.toQuestionnaireResponseCache(qr, "collection-agent");
                    cache.setLocalUserId(uid);
                    cache.setQuestionnaireId(qid);
                    cache.setQuestionnaireTitle(title);
                    cache.setTotalScore(totalScore);
                    cache.setScoreInterpretation(riskLevel);
                    cache.setConversationId(convId);
                    cache.setStatus("completed");
                    qrMapper.insert(cache);
                    log.info("FHIR资源已持久化: sessionId={}, fhirId={}", sid, cache.getFhirId());
                } catch (Exception e) {
                    log.error("持久化FHIR资源失败: sessionId={}", sid, e);
                }
            });
        }

        return InterviewResponse.builder()
                .sessionId(session.getSessionId())
                .completed(true)
                .progress(session.getTotalQuestions() + "/" + session.getTotalQuestions())
                .summary(InterviewResponse.CompletionSummary.builder()
                        .totalScore(pipelineResult.getTotalScore())
                        .maxScore(pipelineResult.getMaxScore())
                        .severity(pipelineResult.getRiskLevel())
                        .interpretation(pipelineResult.getInterpretation())
                        .hasHistory(hasHistory)
                        .trendDescription(trendDesc)
                        .analysisSummary(analysisSummary)
                        .analysisId("analysis-" + session.getSessionId())
                        .build())
                .build();
    }

    /**
     * 同步执行分析层 —— 构建AnalysisInput并调用AnalysisAgent
     * 返回分析摘要文本，失败返回 null
     */
    private String runSyncAnalysis(InterviewSession session, QuestionnaireTemplate template,
                                     TransformationPipeline.PipelineResult pipelineResult) {
        log.info("同步执行分析: sessionId={}", session.getSessionId());

        if (!analysisAgent.isAvailable()) {
            log.warn("分析Agent不可用，跳过分析: sessionId={}", session.getSessionId());
            return null;
        }

        try {
            log.info("构建分析输入: sessionId={}, rawInputs={}, hasTrend={}",
                    session.getSessionId(),
                    session.getRawInputs() != null ? session.getRawInputs().size() : 0,
                    getHistory(session.getUserId(), session.getQuestionnaireId()).size() > 1);
            // 构建规则结果
            var flags = new java.util.ArrayList<AnalysisInput.FlagItem>();
            if (pipelineResult.getComboFlags() != null) {
                for (var flag : pipelineResult.getComboFlags()) {
                    flags.add(AnalysisInput.FlagItem.builder()
                            .type(flag.getType())
                            .label(flag.getLabel())
                            .condition(flag.getRuleId())
                            .build());
                }
            }

            // 构建安全状态
            boolean hasEmergency = pipelineResult.hasSafetyFlags();

            // 构建原始表述列表（携带溯源信息）
            var rawInputs = new java.util.ArrayList<AnalysisInput.RawInputItem>();
            if (session.getRawInputs() != null) {
                for (var item : template.getItems()) {
                    String raw = session.getRawInputs().get(item.getLinkId());
                    if (raw != null && !raw.isEmpty()) {
                        // 从 session 读取溯源信息
                        InterviewSession.Provenance prov = session.getProvenance() != null
                                ? session.getProvenance().get(item.getLinkId()) : null;
                        rawInputs.add(AnalysisInput.RawInputItem.builder()
                                .questionText(item.getText())
                                .userSaid(raw)
                                .source(prov != null ? prov.getSource() : null)
                                .confidence(prov != null ? prov.getConfidence() : null)
                                .build());
                    }
                }
            }

            // 构建趋势（简化：从历史记录获取）
            AnalysisInput.TrendBlock trend = null;
            List<Map<String, Object>> history = getHistory(session.getUserId(),
                    session.getQuestionnaireId());
            if (history.size() > 1 && history.get(1).get("score") != null) {
                double prevScore = ((Number) history.get(1).get("score")).doubleValue();
                int delta = pipelineResult.getTotalScore() - (int) prevScore;
                trend = AnalysisInput.TrendBlock.builder()
                        .direction(delta < 0 ? "下降" : delta > 0 ? "上升" : "持平")
                        .delta(Math.abs(delta))
                        .previousScore((int) prevScore)
                        .previousDate(history.get(1).get("authoredDate") != null
                                ? history.get(1).get("authoredDate").toString() : "")
                        .description(delta < 0 ? "较上次有所改善" : delta > 0 ? "较上次有所升高" : "与上次持平")
                        .build();
            }

            AnalysisInput input = AnalysisInput.builder()
                    .meta(AnalysisInput.AnalysisMeta.builder()
                            .questionnaireTitle(template.getTitle())
                            .collectionDate(java.time.LocalDate.now().toString())
                            .analysisId("analysis-" + session.getSessionId())
                            .collectionMode(session.getCollectionMode())
                            .build())
                    .ruleResults(AnalysisInput.RuleResultBlock.builder()
                            .totalScore(pipelineResult.getTotalScore())
                            .maxScore(pipelineResult.getMaxScore())
                            .riskLevel(pipelineResult.getRiskLevel())
                            .riskLabel(pipelineResult.getRiskLevel() + "（" + pipelineResult.getInterpretation() + "）")
                            .flags(flags)
                            .build())
                    .patientContext(AnalysisInput.PatientContextBlock.builder()
                            .ageRange("未知")
                            .gender("未知")
                            .assessmentCount(history.size())
                            .trend(trend)
                            .build())
                    .rawInputs(rawInputs)
                    .safetyStatus(AnalysisInput.SafetyStatusBlock.builder()
                            .hasEmergencyFlag(hasEmergency)
                            .triggeredKeywords(pipelineResult.getSafetyFlags())
                            .wasInterrupted(false)
                            .build())
                    .build();

            log.info("分析Agent调用中: analysisId={}", input.getMeta().getAnalysisId());
            AnalysisResult result = analysisAgent.analyze(input);
            log.info("分析完成: sessionId={}, summary={}",
                    session.getSessionId(),
                    result.getSummary() != null
                            ? result.getSummary().substring(0, Math.min(50,
                                    result.getSummary().length())) : "无");

            // 异步持久化完整分析报告 + FHIR资源
            final AnalysisResult finalResult = result;
            CompletableFuture.runAsync(() -> {
                try {
                    persistenceService.saveAnalysisReport(finalResult, session, pipelineResult);
                    log.info("分析报告已持久化: sessionId={}, analysisId={}",
                            session.getSessionId(), input.getMeta().getAnalysisId());

                    // FHIR资源：DiagnosticReport + RiskAssessment
                    String patientFhirId = session.getUserId() != null
                            ? "pat-" + session.getUserId() : null;
                    String qrReference = null;
                    if (pipelineResult.getQuestionnaireResponse() != null) {
                        qrReference = "QuestionnaireResponse/"
                                + pipelineResult.getQuestionnaireResponse().getIdElement().getIdPart();
                    }

                    // 持久化 DiagnosticReport
                    var drCache = converter.toDiagnosticReportCache(
                            finalResult, patientFhirId, qrReference,
                            session.getSessionId(), session.getUserId(),
                            session.getConversationId());
                    persistenceService.saveFhirDiagnosticReport(drCache);
                    log.info("FHIR诊断报告已持久化: sessionId={}, fhirId={}",
                            session.getSessionId(), drCache.getFhirId());

                    // 持久化 RiskAssessment
                    var raCache = converter.toRiskAssessmentCache(
                            finalResult, patientFhirId, qrReference,
                            session.getSessionId(), session.getUserId(),
                            session.getConversationId());
                    persistenceService.saveFhirRiskAssessment(raCache);
                    log.info("FHIR风险评估已持久化: sessionId={}, fhirId={}",
                            session.getSessionId(), raCache.getFhirId());
                } catch (Exception e) {
                    log.error("持久化分析报告或FHIR资源失败: sessionId={}",
                            session.getSessionId(), e);
                }
            });

            return result.getSummary();

        } catch (Exception e) {
            log.error("同步分析失败: sessionId={}, error={}",
                    session.getSessionId(), e.getMessage());
            return null;
        }
    }

    /**
     * 降级完成响应 —— 当管道校验失败时使用旧逻辑兜底
     */
    private InterviewResponse buildFallbackCompletionResponse(InterviewSession session,
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

        return InterviewResponse.builder()
                .sessionId(session.getSessionId())
                .completed(true)
                .progress(session.getTotalQuestions() + "/" + session.getTotalQuestions())
                .summary(InterviewResponse.CompletionSummary.builder()
                        .totalScore(session.getCurrentScore())
                        .maxScore(rule.getMaxScore())
                        .severity(severity)
                        .interpretation(interpretation)
                        .hasHistory(false)
                        .build())
                .build();
    }

    private void saveSession(InterviewSession session) {
        // 先存本地缓存兜底，确保访谈不因Redis抖动而中断
        localSessions.put(session.getSessionId(), session);
        // 异步写入Redis
        CompletableFuture.runAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(session);
                redisService.set(SESSION_KEY_PREFIX + session.getSessionId(),
                        json, (long) SESSION_TTL_SECONDS);
                log.debug("会话已保存到Redis: sessionId={}", session.getSessionId());
            } catch (Exception e) {
                log.warn("Redis保存会话失败(已降级到本地缓存): sessionId={}, error={}",
                        session.getSessionId(), e.getMessage());
            }
        });
        // 异步持久化到MySQL
        CompletableFuture.runAsync(() -> {
            try {
                persistenceService.saveSession(session);
            } catch (Exception e) {
                log.warn("MySQL持久化会话失败: sessionId={}", session.getSessionId(), e);
            }
        });
    }

    private InterviewSession loadSession(String sessionId) {
        // 第一层：Redis（热数据）
        try {
            Object value = redisService.get(SESSION_KEY_PREFIX + sessionId);
            if (value != null) {
                String json = value.toString();
                return objectMapper.readValue(json, InterviewSession.class);
            }
        } catch (Exception e) {
            log.warn("Redis读取会话失败，尝试本地缓存: sessionId={}, error={}",
                    sessionId, e.getMessage());
        }
        // 第二层：本地缓存
        InterviewSession local = localSessions.get(sessionId);
        if (local != null) {
            log.debug("从本地缓存加载会话: sessionId={}", sessionId);
            return local;
        }
        // 第三层：MySQL回退
        try {
            Optional<InterviewSession> dbSession = persistenceService.loadSession(sessionId);
            if (dbSession.isPresent()) {
                InterviewSession session = dbSession.get();
                localSessions.put(sessionId, session);
                // 异步恢复到Redis
                CompletableFuture.runAsync(() -> {
                    try {
                        String json = objectMapper.writeValueAsString(session);
                        redisService.set(SESSION_KEY_PREFIX + sessionId, json,
                                (long) SESSION_TTL_SECONDS);
                    } catch (Exception e) {
                        log.warn("MySQL恢复Redis缓存失败: sessionId={}", sessionId, e);
                    }
                });
                log.info("从MySQL恢复会话: sessionId={}", sessionId);
                return session;
            }
        } catch (Exception e) {
            log.warn("MySQL加载会话失败: sessionId={}", sessionId, e);
        }
        return null;
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

    // ==================== 新增接口方法 ====================

    @Override
    public List<Map<String, Object>> getInterviewMessages(String sessionId) {
        return persistenceService.getInterviewMessagesBySession(sessionId);
    }

    @Override
    public java.util.Optional<AnalysisResult> getAnalysisReport(String sessionId) {
        return persistenceService.getAnalysisReportBySessionId(sessionId);
    }

    @Override
    public List<Map<String, Object>> getInterviewsByConversation(Long conversationId) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        List<InterviewSession> sessions = persistenceService.listSessionsByConversation(conversationId);
        for (InterviewSession session : sessions) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sessionId", session.getSessionId());
            summary.put("questionnaireId", session.getQuestionnaireId());
            summary.put("questionnaireTitle", session.getQuestionnaireTitle());
            summary.put("collectionMode", session.getCollectionMode());
            summary.put("totalScore", session.getCurrentScore());
            summary.put("completed", session.isCompleted());
            summary.put("createdAt", session.getCreatedAt() != null
                    ? session.getCreatedAt().toString() : null);
            summary.put("lastActiveAt", session.getLastActiveAt() != null
                    ? session.getLastActiveAt().toString() : null);
            // 检查是否有分析报告
            java.util.Optional<AnalysisResult> report =
                    persistenceService.getAnalysisReportBySessionId(session.getSessionId());
            summary.put("hasAnalysis", report.isPresent());
            if (report.isPresent()) {
                summary.put("severity", report.get().getSummary() != null
                        ? extractSeverityFromSummary(report.get().getSummary()) : "未知");
            }
            result.add(summary);
        }
        return result;
    }

    // ==================== 消息持久化辅助方法 ====================

    private void persistInterviewMessage(InterviewSession session, String messageType,
                                         Map<String, Object> messageData) {
        CompletableFuture.runAsync(() -> {
            try {
                persistenceService.saveInterviewMessage(
                        session.getConversationId(),
                        session.getSessionId(),
                        session.getUserId(),
                        messageType,
                        session.getQuestionnaireId(),
                        session.getQuestionnaireTitle(),
                        messageData);
            } catch (Exception e) {
                log.warn("持久化访谈消息失败: sessionId={}, type={}",
                        session.getSessionId(), messageType, e);
            }
        });
    }

    private void persistUserMessage(InterviewSession session, QuestionnaireTemplate template,
                                    String userInput) {
        var currentItem = session.getCurrentQuestionIndex() < template.getItems().size()
                ? template.getItems().get(session.getCurrentQuestionIndex()) : null;
        Map<String, Object> msgData = new LinkedHashMap<>();
        msgData.put("text", userInput);
        msgData.put("sessionId", session.getSessionId());
        if (currentItem != null) {
            msgData.put("linkId", currentItem.getLinkId());
            msgData.put("questionText", currentItem.getText());
        }
        msgData.put("degradationLevel", session.getDegradationLevel());
        msgData.put("progress", (session.getCurrentQuestionIndex() + 1) + "/" + session.getTotalQuestions());
        log.info("持久化用户消息: sessionId={}, conversationId={}, linkId={}, text={}",
                session.getSessionId(), session.getConversationId(),
                currentItem != null ? currentItem.getLinkId() : "N/A",
                userInput.length() > 50 ? userInput.substring(0, 50) + "..." : userInput);
        persistInterviewMessage(session, "user_message", msgData);
    }

    private void persistAgentResultMessage(InterviewSession session,
                                           CollectionToolOutputs.AgentResult result) {
        // record_answer 和 complete 类型已在 processLlmTurn 中单独处理
        if (result.getType() == CollectionToolOutputs.OutputType.RECORD_ANSWER
                || result.getType() == CollectionToolOutputs.OutputType.COMPLETE) {
            return;
        }
        Map<String, Object> msgData = null;
        String msgType = null;

        switch (result.getType()) {
            case ASK_QUESTION -> {
                var q = result.getAskQuestion();
                msgType = "question";
                msgData = buildQuestionMsgData(q.getLinkId(), q.getText(),
                        q.getCurrentIndex(), q.getTotalQuestions(), q.getOptions());
            }
            case CLARIFY -> {
                var c = result.getClarify();
                msgType = "clarify";
                msgData = new LinkedHashMap<>();
                msgData.put("linkId", c.getLinkId());
                msgData.put("text", c.getClarifyingText());
                if (c.getOptions() != null) {
                    msgData.put("options", c.getOptions());
                }
            }
            case EMERGENCY_INTERRUPT -> {
                var e = result.getEmergencyInterrupt();
                msgType = "safety_alert";
                msgData = new LinkedHashMap<>();
                msgData.put("reason", e.getReason());
                msgData.put("message", e.getUserMessage());
            }
        }

        if (msgType != null && msgData != null) {
            msgData.put("sessionId", session.getSessionId());
            persistInterviewMessage(session, msgType, msgData);
        }
    }

    private Map<String, Object> buildQuestionMsgData(String linkId, String text,
                                                      int currentIndex, int totalQuestions,
                                                      java.util.List<QuestionnaireTemplate.AnswerOption> options) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("linkId", linkId);
        data.put("text", text);
        data.put("currentIndex", currentIndex);
        data.put("totalQuestions", totalQuestions);
        data.put("progress", (currentIndex + 1) + "/" + totalQuestions);
        if (options != null) {
            data.put("options", options);
        }
        return data;
    }

    private Map<String, Object> buildCompletionMsgData(int totalScore, int maxScore,
                                                        String severity, String interpretation,
                                                        String analysisSummary, String analysisId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", String.format("评估完成，总分%d/%d，严重程度：%s",
                totalScore, maxScore, severity));
        data.put("totalScore", totalScore);
        data.put("maxScore", maxScore);
        data.put("severity", severity);
        data.put("interpretation", interpretation != null ? interpretation : "");
        if (analysisSummary != null) {
            data.put("analysisSummary", analysisSummary);
        }
        if (analysisId != null) {
            data.put("analysisId", analysisId);
        }
        return data;
    }

    private String extractSeverityFromSummary(String summary) {
        if (summary == null) return "未知";
        if (summary.contains("轻度")) return "轻度";
        if (summary.contains("中度")) return "中度";
        if (summary.contains("重度")) return "重度";
        if (summary.contains("正常") || summary.contains("无")) return "正常";
        return "未知";
    }

    /**
     * 三级纯表单模式：用户输入数字编号直接映射到选项
     * 返回 null 表示纯表单也无法解析，需回退到正常流程
     */
    private CollectionToolOutputs.AgentResult handleManualFormInput(
            InterviewSession session, String userInput, QuestionnaireTemplate template) {
        if (session.getCurrentQuestionIndex() >= template.getItems().size()) {
            return null;
        }
        var currentItem = template.getItems().get(session.getCurrentQuestionIndex());
        String trimmed = userInput.trim();

        // 尝试解析数字编号
        try {
            int num = Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
            if (num >= 1 && num <= currentItem.getOptions().size()) {
                var opt = currentItem.getOptions().get(num - 1);
                log.info("纯表单模式→数字映射: num={}, linkId={}, code={}, display={}",
                        num, currentItem.getLinkId(), opt.getCode(), opt.getDisplay());
                return CollectionToolOutputs.AgentResult.recordAnswer(
                        CollectionToolOutputs.RecordAnswerOutput.builder()
                                .linkId(currentItem.getLinkId())
                                .code(opt.getCode())
                                .display(opt.getDisplay())
                                .value(opt.getScore())
                                .rawInput(userInput)
                                .confidence(1.0)
                                .contextSummary(String.format("Q%s: %s", currentItem.getLinkId(), opt.getDisplay()))
                                .source("manual_form")
                                .build());
            }
        } catch (NumberFormatException ignored) {}

        // 如果输入了选项文本关键词，尝试模糊匹配
        String lower = userInput.toLowerCase().trim();
        for (var opt : currentItem.getOptions()) {
            if (lower.contains(opt.getDisplay().toLowerCase())) {
                log.info("纯表单模式→文本匹配: display={}, code={}", opt.getDisplay(), opt.getCode());
                return CollectionToolOutputs.AgentResult.recordAnswer(
                        CollectionToolOutputs.RecordAnswerOutput.builder()
                                .linkId(currentItem.getLinkId())
                                .code(opt.getCode())
                                .display(opt.getDisplay())
                                .value(opt.getScore())
                                .rawInput(userInput)
                                .confidence(0.9)
                                .contextSummary(String.format("Q%s: %s", currentItem.getLinkId(), opt.getDisplay()))
                                .source("manual_form")
                                .build());
            }
        }

        // 仍然无法解析，返回 null 让调用方回退
        log.info("纯表单模式也无法解析: sessionId={}, userInput={}", session.getSessionId(), userInput);
        return null;
    }
}
