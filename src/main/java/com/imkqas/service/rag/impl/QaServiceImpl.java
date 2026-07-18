package com.imkqas.service.rag.impl;

import com.imkqas.config.RagConfig;
import com.imkqas.dto.qa.RetrievalPathDto;
import com.imkqas.dto.qa.RetrievalStepDto;
import com.imkqas.entity.Document;
import com.imkqas.service.*;
import com.imkqas.service.document.DocumentService;
import com.imkqas.service.his.*;
import com.imkqas.service.rag.*;
import com.imkqas.utils.evaluation.PipelineTraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 问答服务实现类
 * 完整的RAG问答管线：查询预处理 → 双路召回(RRF融合) → 质量过滤 → 矛盾检测 → 多因子重排序 → LLM生成
 *
 * @author 系统
 * @version 3.0
 */
@Service
@Slf4j
public class QaServiceImpl implements QaService {

    private final MultiRetrievalService multiRetrievalService;
    private final MultiFactorRerankService multiFactorRerankService;
    private final QualityFilterService qualityFilterService;
    private final LlmService llmService;
    private final RedisService redisService;
    private final RagConfig ragConfig;
    private final DocumentService documentService;
    private final SafetyGuardService safetyGuardService;
    private final QueryRewriteService queryRewriteService;
    private final SemanticCacheService semanticCacheService;
    private final IntentRouter intentRouter;
    private final InterviewEngine interviewEngine;
    private final ConversationStateManager conversationStateManager;
    private final ContraindicationDetectionService contraindicationDetectionService;

    public QaServiceImpl(MultiRetrievalService multiRetrievalService,
                         MultiFactorRerankService multiFactorRerankService,
                         QualityFilterService qualityFilterService,
                         LlmService llmService,
                         RedisService redisService,
                         RagConfig ragConfig,
                         DocumentService documentService,
                         SafetyGuardService safetyGuardService,
                         QueryRewriteService queryRewriteService,
                         SemanticCacheService semanticCacheService,
                         IntentRouter intentRouter,
                         InterviewEngine interviewEngine,
                         ConversationStateManager conversationStateManager,
                         ContraindicationDetectionService contraindicationDetectionService) {
        this.multiRetrievalService = multiRetrievalService;
        this.multiFactorRerankService = multiFactorRerankService;
        this.qualityFilterService = qualityFilterService;
        this.llmService = llmService;
        this.redisService = redisService;
        this.ragConfig = ragConfig;
        this.documentService = documentService;
        this.safetyGuardService = safetyGuardService;
        this.queryRewriteService = queryRewriteService;
        this.semanticCacheService = semanticCacheService;
        this.intentRouter = intentRouter;
        this.interviewEngine = interviewEngine;
        this.conversationStateManager = conversationStateManager;
        this.contraindicationDetectionService = contraindicationDetectionService;
    }

    // 文档标题缓存（避免重复查询）
    private final Map<Long, String> documentTitleCache = new ConcurrentHashMap<>();

    // 统计信息
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger successfulQueries = new AtomicInteger(0);
    private final AtomicInteger failedQueries = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger totalRetrievedDocuments = new AtomicInteger(0);
    private final AtomicInteger totalGeneratedTokens = new AtomicInteger(0);

    @Override
    public QaResponse answer(String query, Long userId, Long conversationId) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();
        PipelineTraceContext.start();

        try {
            // ═══ [1] 意图路由：LLM分类 + 关键词兜底 ═══
            IntentType intent = intentRouter.classify(query);
            InterviewSuggestion suggestion = null;
            if (intent == IntentType.DATA_COLLECTION || intent == IntentType.MIXED) {
                suggestion = interviewEngine.suggestQuestionnaire(query);
            }
            log.info("意图路由: query={}, intent={}, hasSuggestion={}",
                    truncate(query, 50), intent, suggestion != null && suggestion.isMatched());

            // DATA_COLLECTION → 仅返回问卷建议，不走RAG
            if (intent == IntentType.DATA_COLLECTION) {
                PipelineTraceContext.finish();
                long processingTime = System.currentTimeMillis() - startTime;
                successfulQueries.incrementAndGet();
                String answerText = suggestion.isMatched()
                        ? suggestion.getSuggestionText()
                        : "感谢您的描述。目前暂未匹配到合适的评估问卷，建议您咨询专业医生获取更准确的评估。";
                return new QaResponse(query, answerText, Collections.emptyList(),
                        0.85, processingTime, "intent-router",
                        intent.name(), suggestion);
            }

            // ═══ [2] 查询预处理：分词 + 实体识别 + 同义词扩展 ═══
            long t2 = System.currentTimeMillis();
            String processedQuery = queryRewriteService.rewrite(query, userId, conversationId);
            PipelineTraceContext.recordStep("查询预处理", 2, System.currentTimeMillis() - t2,
                    1, 1, Map.of("已改写", processedQuery != null && !processedQuery.equals(query) ? "是" : "否"));
            if (processedQuery != null && !processedQuery.equals(query)) {
                log.info("查询预处理完成: raw={}, processed={}", query, processedQuery);
            } else {
                log.info("查询改写未命中（三级链均无映射），使用原始Query进行向量检索（语义容错）: query={}", query);
            }

            // ═══ [3] 安全兜底：急症预检 ═══
            long t3 = System.currentTimeMillis();
            SafetyDecision emergencyDecision = safetyGuardService.checkEmergency(processedQuery);
            PipelineTraceContext.recordStep("安全兜底①-急症预检", 3, System.currentTimeMillis() - t3);
            if (emergencyDecision.isBlocked()) {
                PipelineTraceContext.finish();
                return buildEmergencyResponse(query, emergencyDecision, startTime, intent);
            }

            // [4][5] 双路召回 + RRF融合检索
            long t4 = System.currentTimeMillis();
            List<MultiRetrievalService.RetrievalResult> retrievalResults = retrieveDocuments(processedQuery);
            PipelineTraceContext.recordStep("双路召回+RRF融合", 5, System.currentTimeMillis() - t4);
            totalRetrievedDocuments.addAndGet(retrievalResults.size());

            // [6] 质量过滤
            long t6 = System.currentTimeMillis();
            QualityFilterService.FilterResult filterResult = qualityFilterService.filter(retrievalResults);
            List<MultiRetrievalService.RetrievalResult> filteredResults = filterResult.getPassed();
            PipelineTraceContext.recordStep("质量过滤", 6, System.currentTimeMillis() - t6);
            log.info("质量过滤: 输入={}, 通过={}, 丢弃={}",
                    retrievalResults.size(), filteredResults.size(), filterResult.getDiscardedCount());

            // [7] 矛盾检测
            long t7 = System.currentTimeMillis();
            QualityFilterService.ContradictionResult contradiction =
                    qualityFilterService.detectContradictions(filteredResults, query);
            String contradictionStepResult;
            QualityFilterService.ContradictionResult.ResolutionType resType =
                    contradiction.getResolutionType();
            if (resType == QualityFilterService.ContradictionResult.ResolutionType.RESOLVED) {
                contradictionStepResult = "已裁决(高权威源为准)";
                log.info("矛盾已裁决: pair={}, 采纳权威性{:.2f}的{}向表述",
                        contradiction.getDrugPopulationPair(),
                        contradiction.getWinnerAuthority(),
                        contradiction.isWinnerDirectionPositive() ? "正" : "负");
            } else if (resType == QualityFilterService.ContradictionResult.ResolutionType.UNRESOLVED) {
                contradictionStepResult = "未裁决(交LLM综合判断)";
                log.warn("矛盾未裁决: pair={}, 权威性相当, 交LLM综合判断并附加冲突说明",
                        contradiction.getDrugPopulationPair());
            } else {
                contradictionStepResult = "无矛盾";
            }
            PipelineTraceContext.recordStep("矛盾检测", 7, System.currentTimeMillis() - t7,
                    1, 1, Map.of("结果", contradictionStepResult));
            // 矛盾信息由 contradiction 变量持有，后续在安全提示环节处理

            // [8] 多因子重排序
            long t8 = System.currentTimeMillis();
            List<MultiRetrievalService.RetrievalResult> rerankedResults =
                    rerankDocuments(processedQuery, filteredResults);
            PipelineTraceContext.recordStep("多因子重排序", 8, System.currentTimeMillis() - t8);

            // ═══ [9] 安全兜底：置信度门控 ═══
            long t9 = System.currentTimeMillis();
            ConfidenceDecision confidenceDecision = safetyGuardService.assessConfidence(rerankedResults);
            PipelineTraceContext.recordStep("安全兜底②-置信度门控", 9, System.currentTimeMillis() - t9);
            if (confidenceDecision.isBlocked()) {
                PipelineTraceContext.finish();
                return buildLowConfidenceResponse(query, confidenceDecision, startTime, intent);
            }

            List<String> context = extractContext(rerankedResults);

            // ═══ [10][11] 语义化缓存链 → LLM生成 ═══
            String answer;
            String modelUsed = llmService.getModelInfo().getName();
            // 直接使用查询预处理阶段的结果，避免对SNOMED CT的重复调用
            String normalizedQuery = processedQuery != null ? processedQuery : query;
            List<Long> sortedFragmentIds = extractSortedFragmentIds(rerankedResults);

            long t10 = System.currentTimeMillis();
            boolean cacheHit = false;
            SemanticCacheService.CachedAnswer cached = semanticCacheService.get(normalizedQuery, sortedFragmentIds);
            if (cached != null) {
                cacheHit = true;
                PipelineTraceContext.recordStep("语义缓存(命中)", 10, System.currentTimeMillis() - t10);
                answer = safetyGuardService.sanitizeAnswer(cached.getAnswer(), 0.8);
                log.info("语义缓存命中: query={}, version={}, latency=0ms(L2)",
                        truncate(query, 50), cached.getVersion());
            } else {
                PipelineTraceContext.recordStep("语义缓存(未命中)", 10, System.currentTimeMillis() - t10);
                boolean lockAcquired = semanticCacheService instanceof SemanticCacheServiceImpl
                        && ((SemanticCacheServiceImpl) semanticCacheService)
                                .tryAcquireRebuildLock(normalizedQuery, sortedFragmentIds);

                if (lockAcquired) {
                    try {
                        long t11 = System.currentTimeMillis();
                        answer = generateAnswer(processedQuery, context);
                        PipelineTraceContext.recordStep("LLM生成", 11, System.currentTimeMillis() - t11);

                        long t12 = System.currentTimeMillis();
                        answer = safetyGuardService.sanitizeAnswer(answer,
                                calculateConfidence(rerankedResults, answer));
                        PipelineTraceContext.recordStep("安全兜底③-答案净化", 12, System.currentTimeMillis() - t12);

                        List<String> sources = context.stream().limit(3).collect(Collectors.toList());
                        semanticCacheService.put(normalizedQuery, sortedFragmentIds, answer, sources);
                        log.debug("语义缓存写入完成: query={}", truncate(query, 50));
                    } finally {
                        ((SemanticCacheServiceImpl) semanticCacheService)
                                .releaseRebuildLock(normalizedQuery, sortedFragmentIds);
                    }
                } else {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    SemanticCacheService.CachedAnswer retryCached =
                            semanticCacheService.get(normalizedQuery, sortedFragmentIds);
                    if (retryCached != null) {
                        cacheHit = true;
                        answer = safetyGuardService.sanitizeAnswer(retryCached.getAnswer(), 0.8);
                        log.info("语义缓存重试命中: query={}", truncate(query, 50));
                    } else {
                        long t11 = System.currentTimeMillis();
                        answer = generateAnswer(processedQuery, context);
                        PipelineTraceContext.recordStep("LLM生成", 11, System.currentTimeMillis() - t11);

                        long t12 = System.currentTimeMillis();
                        answer = safetyGuardService.sanitizeAnswer(answer,
                                calculateConfidence(rerankedResults, answer));
                        PipelineTraceContext.recordStep("安全兜底③-答案净化", 12, System.currentTimeMillis() - t12);

                        log.debug("语义缓存未命中且未获取锁，直接调用LLM: query={}", truncate(query, 50));
                    }
                }
            }

            // MIXED：在RAG回答末尾附加问卷推荐
            if (intent == IntentType.MIXED && suggestion != null && suggestion.isMatched()) {
                answer = answer + "\n\n---\n\n" + suggestion.getSuggestionText();
            }

            // 兜底安全提示：查询涉及药物+人群但禁忌规则表未覆盖
            String safetyNote = contraindicationDetectionService.buildSafetyNote(query);
            if (safetyNote != null) {
                answer = answer + "\n\n---\n" + safetyNote;
            }

            // 矛盾裁决安全提示
            if (contradiction.isHasContradiction()) {
                String contradictionNote = buildContradictionNote(contradiction);
                if (contradictionNote != null) {
                    answer = answer + "\n\n---\n" + contradictionNote;
                }
            }

            double confidence = calculateConfidence(rerankedResults, answer);

            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            successfulQueries.incrementAndGet();

            QaResponse response = new QaResponse(
                    query, answer,
                    context.stream().limit(3).collect(Collectors.toList()),
                    confidence, processingTime, modelUsed,
                    intent.name(), suggestion);

            log.info("问答完成: query={}, intent={}, contextCount={}, answerLength={}, confidence={}, time={}ms",
                    truncate(query, 50), intent, context.size(),
                    answer.length(), confidence, processingTime);

            PipelineTraceContext.get().putMetadata("confidence", confidence);
            PipelineTraceContext.get().putMetadata("cacheHit", cacheHit);
            PipelineTraceContext.get().putMetadata("intentType", intent.name());
            PipelineTraceContext.get().putMetadata("totalTimeMs", processingTime);
            PipelineTraceContext.finish();

            return response;

        } catch (Exception e) {
            log.error("问答异常: query={}, userId={}, conversationId={}", query, userId, conversationId, e);
            failedQueries.incrementAndGet();
            return getFallbackResponse(query, startTime);
        } finally {
            PipelineTraceContext.clear();
        }
    }

    @Override
    public CompletableFuture<QaResponse> answerAsync(String query, Long userId, Long conversationId) {
        return CompletableFuture.supplyAsync(() -> answer(query, userId, conversationId));
    }

    @Override
    public List<QaResponse> answerBatch(List<String> queries, Long userId, Long conversationId) {
        // 简化实现：循环调用单个问答
        // 实际可优化为并行处理
        List<QaResponse> responses = new ArrayList<>();

        for (String query : queries) {
            responses.add(answer(query, userId, conversationId));
        }

        return responses;
    }

    @Override
    public QaResponseWithSources answerWithSources(String query, Long userId, Long conversationId) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();
        PipelineTraceContext.start();

        try {
            // 意图路由
            long t1 = System.currentTimeMillis();
            IntentType intent = intentRouter.classify(query);
            InterviewSuggestion suggestion = null;
            if (intent == IntentType.DATA_COLLECTION || intent == IntentType.MIXED) {
                suggestion = interviewEngine.suggestQuestionnaire(query);
            }
            PipelineTraceContext.recordStep("意图路由", 1, System.currentTimeMillis() - t1,
                    1, 1, Map.of("intentType", intent.name()));

            // DATA_COLLECTION → 仅返回问卷建议
            if (intent == IntentType.DATA_COLLECTION) {
                long processingTime = System.currentTimeMillis() - startTime;
                successfulQueries.incrementAndGet();
                String answerText = suggestion.isMatched()
                        ? suggestion.getSuggestionText()
                        : "感谢您的描述。目前暂未匹配到合适的评估问卷，建议您咨询专业医生获取更准确的评估。";
                log.info("[DATA_COLLECTION] 返回问卷建议: matched={}, questionnaireId={}, answerLength={}, processingTime={}ms",
                        suggestion.isMatched(),
                        suggestion.getQuestionnaire() != null ? suggestion.getQuestionnaire().getId() : "null",
                        answerText.length(), processingTime);
                PipelineTraceContext.get().putMetadata("intentType", intent.name());
                PipelineTraceContext.PipelineTrace trace = PipelineTraceContext.finish();
                List<RetrievalStepDto> stepDtos = buildStepDtos(trace);
                RetrievalPathDto retrievalPath = RetrievalPathDto.builder()
                        .steps(stepDtos)
                        .totalDurationMs(trace.getTotalDurationMs())
                        .cacheHit(false)
                        .intentType(intent.name())
                        .build();
                return new QaResponseWithSources(query, answerText, Collections.emptyList(),
                        0.85, processingTime, "intent-router", Collections.emptyList(),
                        intent.name(), suggestion, retrievalPath);
            }

            // ═══ 查询预处理 ═══
            long t2 = System.currentTimeMillis();
            String processedQuery = queryRewriteService.rewrite(query, userId, conversationId);
            boolean wasRewritten = processedQuery != null && !processedQuery.equals(query);
            PipelineTraceContext.recordStep("查询预处理", 2, System.currentTimeMillis() - t2,
                    1, 1, Map.of("已改写", wasRewritten ? "是" : "否"));
            if (wasRewritten) {
                log.info("查询预处理完成(WithSources): raw={}, processed={}", query, processedQuery);
            } else {
                log.info("查询改写未命中(WithSources)（三级链均无映射），使用原始Query进行向量检索（语义容错）: query={}", query);
            }

            // ═══ 安全兜底：急症预检 ═══
            long t3 = System.currentTimeMillis();
            SafetyDecision emergencyDecision = safetyGuardService.checkEmergency(processedQuery);
            PipelineTraceContext.recordStep("安全兜底①-急症预检", 3, System.currentTimeMillis() - t3,
                    1, 1, Map.of("结果", emergencyDecision.isBlocked() ? "触发阻断" : "通过"));
            if (emergencyDecision.isBlocked()) {
                QaResponse er = buildEmergencyResponse(query, emergencyDecision, startTime, intent);
                PipelineTraceContext.get().putMetadata("intentType", intent.name());
                PipelineTraceContext.get().putMetadata("blockedAt", "emergency");
                PipelineTraceContext.PipelineTrace trace = PipelineTraceContext.finish();
                List<RetrievalStepDto> stepDtos = buildStepDtos(trace);
                RetrievalPathDto retrievalPath = RetrievalPathDto.builder()
                        .steps(stepDtos)
                        .totalDurationMs(trace.getTotalDurationMs())
                        .cacheHit(false)
                        .intentType(intent.name())
                        .build();
                return new QaResponseWithSources(
                        er.getQuery(), er.getAnswer(), er.getRetrievedContext(),
                        er.getConfidence(), er.getProcessingTime(), er.getModelUsed(),
                        Collections.emptyList(), er.getIntentType(), er.getQuestionnaireSuggestion(), retrievalPath);
            }

            long t4 = System.currentTimeMillis();
            List<MultiRetrievalService.RetrievalResult> retrievalResults = retrieveDocuments(processedQuery);
            List<String> topDocTitles = retrievalResults.stream()
                    .limit(3)
                    .map(r -> getDocumentTitle(r.getDocumentId()))
                    .collect(Collectors.toList());
            PipelineTraceContext.recordStep("双路召回+RRF融合", 5, System.currentTimeMillis() - t4,
                    0, retrievalResults.size(),
                    Map.of("命中片段", retrievalResults.size(),
                           "示例来源", topDocTitles.isEmpty() ? "无" : String.join("、", topDocTitles)));
            totalRetrievedDocuments.addAndGet(retrievalResults.size());

            long t6 = System.currentTimeMillis();
            QualityFilterService.FilterResult filterResult = qualityFilterService.filter(retrievalResults);
            List<MultiRetrievalService.RetrievalResult> filteredResults = filterResult.getPassed();
            PipelineTraceContext.recordStep("质量过滤", 6, System.currentTimeMillis() - t6,
                    retrievalResults.size(), filteredResults.size(),
                    Map.of("通过", filteredResults.size(),
                           "丢弃", filterResult.getDiscardedCount(),
                           "通过率", retrievalResults.size() > 0
                                ? String.format("%.0f%%", filteredResults.size() * 100.0 / retrievalResults.size())
                                : "0%"));

            long t7 = System.currentTimeMillis();
            QualityFilterService.ContradictionResult contradiction =
                    qualityFilterService.detectContradictions(filteredResults, query);
            String contradictionStepResult;
            QualityFilterService.ContradictionResult.ResolutionType resType =
                    contradiction.getResolutionType();
            if (resType == QualityFilterService.ContradictionResult.ResolutionType.RESOLVED) {
                contradictionStepResult = "已裁决(高权威源为准)";
            } else if (resType == QualityFilterService.ContradictionResult.ResolutionType.UNRESOLVED) {
                contradictionStepResult = "未裁决(交LLM综合判断)";
            } else {
                contradictionStepResult = "无矛盾";
            }
            PipelineTraceContext.recordStep("矛盾检测", 7, System.currentTimeMillis() - t7,
                    1, 1, Map.of("结果", contradictionStepResult));

            long t8 = System.currentTimeMillis();
            List<MultiRetrievalService.RetrievalResult> rerankedResults =
                    rerankDocuments(processedQuery, filteredResults);
            double topScore = rerankedResults.stream()
                    .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                    .max().orElse(0.0);
            PipelineTraceContext.recordStep("多因子重排序", 8, System.currentTimeMillis() - t8,
                    filteredResults.size(), rerankedResults.size(),
                    Map.of("最高分", String.format("%.2f", topScore),
                           "候选数", rerankedResults.size()));

            long t9 = System.currentTimeMillis();
            ConfidenceDecision confidenceDecision = safetyGuardService.assessConfidence(rerankedResults);
            PipelineTraceContext.recordStep("安全兜底②-置信度门控", 9, System.currentTimeMillis() - t9,
                    1, 1, Map.of("结果", confidenceDecision.isBlocked() ? "低于阈值，已阻断" : "通过",
                           "最高置信度", String.format("%.2f", confidenceDecision.getMaxScore())));
            if (confidenceDecision.isBlocked()) {
                QaResponse lr = buildLowConfidenceResponse(query, confidenceDecision, startTime, intent);
                PipelineTraceContext.get().putMetadata("intentType", intent.name());
                PipelineTraceContext.get().putMetadata("blockedAt", "lowConfidence");
                PipelineTraceContext.PipelineTrace trace = PipelineTraceContext.finish();
                List<RetrievalStepDto> stepDtos = buildStepDtos(trace);
                RetrievalPathDto retrievalPath = RetrievalPathDto.builder()
                        .steps(stepDtos)
                        .totalDurationMs(trace.getTotalDurationMs())
                        .cacheHit(false)
                        .intentType(intent.name())
                        .build();
                return new QaResponseWithSources(
                        lr.getQuery(), lr.getAnswer(), lr.getRetrievedContext(),
                        lr.getConfidence(), lr.getProcessingTime(), lr.getModelUsed(),
                        Collections.emptyList(), lr.getIntentType(), lr.getQuestionnaireSuggestion(), retrievalPath);
            }

            long t10 = System.currentTimeMillis();
            List<LlmService.ContextWithSource> contextWithSources = buildContextWithSources(rerankedResults);
            LlmService.AnswerWithCitations answerWithCitations =
                    llmService.generateAnswerWithCitations(query, contextWithSources);
            PipelineTraceContext.recordStep("LLM生成", 11, System.currentTimeMillis() - t10,
                    contextWithSources.size(), 1,
                    Map.of("上下文片段", contextWithSources.size(),
                           "答案长度", answerWithCitations.getAnswer().length() + "字",
                           "引用数", String.valueOf(answerWithCitations.getCitations().size())));
            List<SourceCitation> citations = buildSourceCitations(
                    rerankedResults, answerWithCitations.getCitations());

            double confidence = calculateConfidence(rerankedResults, answerWithCitations.getAnswer());
            long t12 = System.currentTimeMillis();
            String sanitizedAnswer = safetyGuardService.sanitizeAnswer(answerWithCitations.getAnswer(), confidence);
            boolean wasSanitized = !sanitizedAnswer.equals(answerWithCitations.getAnswer());
            PipelineTraceContext.recordStep("安全兜底③-答案净化", 12, System.currentTimeMillis() - t12,
                    1, 1, Map.of("结果", wasSanitized ? "已净化（移除风险内容）" : "无需净化",
                           "置信度", String.format("%.2f", confidence)));

            // MIXED：末尾附加问卷推荐
            if (intent == IntentType.MIXED && suggestion != null && suggestion.isMatched()) {
                sanitizedAnswer = sanitizedAnswer + "\n\n---\n\n" + suggestion.getSuggestionText();
            }

            // 兜底安全提示：查询涉及药物+人群但禁忌规则表未覆盖
            String safetyNote = contraindicationDetectionService.buildSafetyNote(query);
            if (safetyNote != null) {
                sanitizedAnswer = sanitizedAnswer + "\n\n---\n" + safetyNote;
            }

            // 矛盾裁决安全提示
            if (contradiction.isHasContradiction()) {
                String contradictionNote = buildContradictionNote(contradiction);
                if (contradictionNote != null) {
                    sanitizedAnswer = sanitizedAnswer + "\n\n---\n" + contradictionNote;
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            successfulQueries.incrementAndGet();

            List<String> contextSummary = contextWithSources.stream()
                    .map(LlmService.ContextWithSource::getContent)
                    .limit(3)
                    .collect(Collectors.toList());

            // 收集管线追踪数据
            PipelineTraceContext.get().putMetadata("confidence", confidence);
            PipelineTraceContext.get().putMetadata("cacheHit", false);
            PipelineTraceContext.get().putMetadata("intentType", intent.name());
            PipelineTraceContext.get().putMetadata("totalTimeMs", processingTime);
            PipelineTraceContext.PipelineTrace trace = PipelineTraceContext.finish();
            List<RetrievalStepDto> stepDtos = buildStepDtos(trace);
            RetrievalPathDto retrievalPath = RetrievalPathDto.builder()
                    .steps(stepDtos)
                    .totalDurationMs(trace.getTotalDurationMs())
                    .cacheHit(false)
                    .intentType(intent.name())
                    .build();

            QaResponseWithSources response = new QaResponseWithSources(
                    query, sanitizedAnswer, contextSummary,
                    confidence, processingTime, llmService.getModelInfo().getName(),
                    citations, intent.name(), suggestion, retrievalPath);

            log.info("带来源问答完成: query={}, intent={}, sources={}, answerLength={}, confidence={}, time={}ms, traceSteps={}",
                    query, intent, citations.size(), answerWithCitations.getAnswer().length(),
                    confidence, processingTime, stepDtos.size());

            return response;

        } catch (Exception e) {
            log.error("带来源问答异常: query={}, userId={}, conversationId={}",
                    query, userId, conversationId, e);
            failedQueries.incrementAndGet();
            QaResponse fallback = getFallbackResponse(query, startTime);
            return new QaResponseWithSources(
                    fallback.getQuery(), fallback.getAnswer(), fallback.getRetrievedContext(),
                    fallback.getConfidence(), fallback.getProcessingTime(), fallback.getModelUsed(),
                    Collections.emptyList(), fallback.getIntentType(), fallback.getQuestionnaireSuggestion(), null);
        } finally {
            PipelineTraceContext.clear();
        }
    }

    @Override
    public QaStats getStats() {
        int total = totalQueries.get();
        int successful = successfulQueries.get();
        int failed = failedQueries.get();
        double avgTime = total > 0 ? (double) totalProcessingTime.get() / total : 0.0;
        double successRate = total > 0 ? (double) successful / total : 0.0;

        return new QaStats(
                total, successful, failed, avgTime, successRate,
                totalRetrievedDocuments.get(), totalGeneratedTokens.get()
        );
    }

    @Override
    public boolean isAvailable() {
        return multiRetrievalService != null && multiFactorRerankService != null
                && llmService != null && llmService.isAvailable()
                && semanticCacheService != null && semanticCacheService.isAvailable();
    }

    // ========== 私有辅助方法 ==========

    /**
     * 检索文档
     */
    private List<MultiRetrievalService.RetrievalResult> retrieveDocuments(String query) {
        // 使用配置中的初始top-k
        int initialTopK = ragConfig.getRetrieval().getInitialTopK();

        // 根据配置的检索模式调用相应方法
        RagConfig.RetrievalConfig.Mode mode = ragConfig.getRetrieval().getMode();
        List<MultiRetrievalService.RetrievalResult> results;

        switch (mode) {
            case VECTOR:
                results = multiRetrievalService.vectorRetrieval(query, initialTopK);
                break;
            case KEYWORD:
                results = multiRetrievalService.keywordRetrieval(query, initialTopK);
                break;
            case HYBRID:
            default:
                results = multiRetrievalService.hybridRetrieval(query, initialTopK);
                break;
        }

        log.debug("检索完成: query={}, mode={}, results={}", query, mode, results.size());
        if (results != null && !results.isEmpty()) {
            log.info("=== 检索结果 (共{}条) ===", results.size());
            for (int i = 0; i < results.size(); i++) {
                var r = results.get(i);
                String breadcrumb = r.getMetadataString("breadcrumb");
                log.info("[检索][{}] score={}, source={}, breadcrumb={}, content={}",
                        i + 1,
                        String.format("%.4f", r.getScore()),
                        r.getSource(),
                        breadcrumb != null ? breadcrumb : "N/A",
                        truncate(r.getContent(), 120));
            }
        }
        return results != null ? results : Collections.emptyList();
    }

    /**
     * 多因子重排序（权威性 + 时效性 + 语义相似度）
     */
    private List<MultiRetrievalService.RetrievalResult> rerankDocuments(
            String query,
            List<MultiRetrievalService.RetrievalResult> results
    ) {
        if (results.isEmpty()) {
            return results;
        }

        int rerankTopK = ragConfig.getRetrieval().getRerankTopK();
        List<MultiRetrievalService.RetrievalResult> rerankedResults =
                multiFactorRerankService.rerank(query, results, rerankTopK);

        log.debug("多因子重排序完成: query={}, input={}, output={}",
                truncate(query, 50), results.size(), rerankedResults.size());
        if (rerankedResults != null && !rerankedResults.isEmpty()) {
            log.info("=== 重排序结果 (共{}条) ===", rerankedResults.size());
            for (int i = 0; i < rerankedResults.size(); i++) {
                var r = rerankedResults.get(i);
                String breadcrumb = r.getMetadataString("breadcrumb");
                log.info("[重排][{}] score={}, source={}, breadcrumb={}, content={}",
                        i + 1,
                        String.format("%.4f", r.getScore()),
                        r.getSource(),
                        breadcrumb != null ? breadcrumb : "N/A",
                        truncate(r.getContent(), 120));
            }
        }
        return rerankedResults != null ? rerankedResults : results;
    }

    /**
     * 提取上下文内容
     */
    private List<String> extractContext(List<MultiRetrievalService.RetrievalResult> results) {
        return results.stream()
                .map(result -> {
                    String breadcrumb = result.getMetadataString("breadcrumb");
                    if (breadcrumb != null && !breadcrumb.trim().isEmpty()) {
                        return "[来源：" + breadcrumb + "]\n" + result.getContent();
                    }
                    return result.getContent();
                })
                .collect(Collectors.toList());
    }

    /**
     * 生成回答
     */
    private String generateAnswer(String query, List<String> context) {
        // 限制上下文长度，避免超过token限制
        List<String> limitedContext = limitContextLength(context);

        // 调用LLM服务
        String answer = llmService.generateAnswer(query, limitedContext);

        // 记录生成的token数（简化：根据回答长度估算）
        totalGeneratedTokens.addAndGet(answer.length() / 4); // 粗略估算：1个token≈4个字符

        return answer;
    }

    /**
     * 计算置信度
     */
    private double calculateConfidence(
            List<MultiRetrievalService.RetrievalResult> results,
            String answer
    ) {
        if (results.isEmpty()) {
            return 0.0;
        }

        // 基于检索结果的分数计算置信度
        double maxScore = results.stream()
                .mapToDouble(result -> result.getScore() != null ? result.getScore() : 0.0)
                .max()
                .orElse(0.0);

        // 基于回答长度和内容计算额外置信度
        double answerConfidence = calculateAnswerConfidence(answer);

        // 综合置信度
        return (maxScore * 0.7) + (answerConfidence * 0.3);
    }

    /**
     * 基于回答内容计算置信度
     */
    private double calculateAnswerConfidence(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return 0.0;
        }

        double confidence = 0.5; // 基础置信度

        // 检查是否包含无法回答的提示
        if (answer.contains("无法回答") || answer.contains("不知道") ||
                answer.contains("没有相关信息") || answer.contains("根据现有资料")) {
            confidence -= 0.3;
        }

        // 检查回答长度
        if (answer.length() > 50) {
            confidence += 0.2;
        }

        // 限制在0-1之间
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * 构建带来源的上下文
     */
    private List<LlmService.ContextWithSource> buildContextWithSources(
            List<MultiRetrievalService.RetrievalResult> results
    ) {
        List<LlmService.ContextWithSource> contextWithSources = new ArrayList<>();

        for (MultiRetrievalService.RetrievalResult result : results) {
            String source = String.format("文档-%d-片段-%d",
                    result.getDocumentId(), result.getChunkId());
            contextWithSources.add(new LlmService.ContextWithSource(
                    result.getContent(),
                    source,
                    result.getScore() != null ? result.getScore() : 0.0
            ));
        }

        return contextWithSources;
    }

    /**
     * 构建来源引用信息
     */
    private List<SourceCitation> buildSourceCitations(
            List<MultiRetrievalService.RetrievalResult> results,
            List<LlmService.Citation> llmCitations
    ) {
        List<SourceCitation> citations = new ArrayList<>();

        // 简化实现：将LLM的引用映射到具体的文档片段
        for (LlmService.Citation llmCitation : llmCitations) {
            // 查找最匹配的检索结果
            for (MultiRetrievalService.RetrievalResult result : results) {
                if (result.getContent().contains(llmCitation.getQuote())) {
                    String title = getDocumentTitle(result.getDocumentId());
                    citations.add(new SourceCitation(
                            String.valueOf(result.getDocumentId()),
                            String.valueOf(result.getChunkId()),
                            title,
                            llmCitation.getQuote(),
                            result.getScore() != null ? result.getScore() : 0.0,
                            llmCitation.getPosition()
                    ));
                    break;
                }
            }
        }

        return citations;
    }

    /**
     * 获取文档标题（带缓存）
     */
    private String getDocumentTitle(Long documentId) {
        if (documentId == null) {
            return "文档片段";
        }
        // 先从缓存查找
        String cached = documentTitleCache.get(documentId);
        if (cached != null) {
            return cached;
        }
        // 从数据库查询
        try {
            Document document = documentService.getById(documentId);
            if (document != null && document.getTitle() != null && !document.getTitle().trim().isEmpty()) {
                documentTitleCache.put(documentId, document.getTitle());
                return document.getTitle();
            }
        } catch (Exception e) {
            log.warn("获取文档标题失败: documentId={}", documentId, e);
        }
        // 降级：使用默认标题
        String fallback = "文档-" + documentId;
        documentTitleCache.put(documentId, fallback);
        return fallback;
    }

    /**
     * 限制上下文长度
     */
    private List<String> limitContextLength(List<String> context) {
        // 粗略估算token数（1个token≈4个字符）
        final int maxTokens = 4000; // 留出空间给问题和回答
        int currentTokens = 0;
        List<String> limitedContext = new ArrayList<>();

        for (String ctx : context) {
            int ctxTokens = ctx.length() / 4;
            if (currentTokens + ctxTokens > maxTokens) {
                break;
            }
            limitedContext.add(ctx);
            currentTokens += ctxTokens;
        }

        return limitedContext;
    }

    /**
     * 提取排序后的知识片段ID列表（用于语义缓存键生成）
     * 排序确保缓存键不受检索结果顺序影响
     */
    private List<Long> extractSortedFragmentIds(List<MultiRetrievalService.RetrievalResult> results) {
        List<Long> ids = new ArrayList<>();
        for (MultiRetrievalService.RetrievalResult result : results) {
            if (result.getChunkId() != null) {
                ids.add(result.getChunkId());
            }
        }
        Collections.sort(ids);
        return ids;
    }

    /**
     * 构建急症阻断响应
     */
    private QaResponse buildEmergencyResponse(String query, SafetyDecision decision, long startTime,
                                               IntentType intent) {
        long processingTime = System.currentTimeMillis() - startTime;
        return new QaResponse(query, decision.getAdviceMessage(), Collections.emptyList(),
                0.0, processingTime, "safety-guard",
                intent != null ? intent.name() : null, null);
    }

    /**
     * 构建低置信度阻断响应
     */
    private QaResponse buildLowConfidenceResponse(String query, ConfidenceDecision decision, long startTime,
                                                    IntentType intent) {
        long processingTime = System.currentTimeMillis() - startTime;
        return new QaResponse(query, decision.getMessage(), Collections.emptyList(),
                decision.getMaxScore(), processingTime, "safety-guard",
                intent != null ? intent.name() : null, null);
    }

    /**
     * 构建矛盾裁决安全提示（追加在LLM回答末尾，不阻断管线）
     */
    private String buildContradictionNote(QualityFilterService.ContradictionResult contradiction) {
        if (contradiction == null || !contradiction.isHasContradiction()) {
            return null;
        }
        QualityFilterService.ContradictionResult.ResolutionType resType =
                contradiction.getResolutionType();

        if (resType == QualityFilterService.ContradictionResult.ResolutionType.RESOLVED) {
            // 权威性有差异 → 以高权威表述为准
            String preferred = contradiction.isWinnerDirectionPositive() ? "可用" : "不宜使用";
            return String.format(
                    "关于 %s，不同来源存在不同说法。经评估，权威性较高的来源（权威分数 %.2f）"
                            + "倾向于认为%s，以下回答以此为准。",
                    contradiction.getDrugPopulationPair(),
                    contradiction.getWinnerAuthority(),
                    preferred);
        } else if (resType == QualityFilterService.ContradictionResult.ResolutionType.UNRESOLVED) {
            // 权威性相当 → 交LLM判断，附加冲突说明
            return String.format(
                    "关于 %s，不同权威来源存在不同说法（权威性相当），"
                            + "以下回答综合了多来源信息。建议您咨询专业医生，结合具体病情做出判断。",
                    contradiction.getDrugPopulationPair());
        }
        return null;
    }

    /**
     * 降级响应（当问答失败时）
     */
    private QaResponse getFallbackResponse(String query, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        String fallbackAnswer = "抱歉，当前无法处理您的查询。请检查网络连接或稍后重试。";
        return new QaResponse(query, fallbackAnswer, Collections.emptyList(),
                0.1, processingTime, "fallback", null, null);
    }

    /**
     * 将管线追踪记录转换为DTO列表（按stepOrder排序）
     */
    private List<RetrievalStepDto> buildStepDtos(PipelineTraceContext.PipelineTrace trace) {
        return trace.getSteps().stream()
                .map(step -> RetrievalStepDto.builder()
                        .stepName(step.stepName)
                        .stepOrder(step.stepOrder)
                        .durationMs(step.durationMs)
                        .inputCount(step.inputCount)
                        .outputCount(step.outputCount)
                        .intermediateData(step.intermediateData != null
                                ? new HashMap<>(step.intermediateData) : new HashMap<>())
                        .status(step.status)
                        .timestamp(step.timestamp != null ? step.timestamp.toEpochMilli() : null)
                        .build())
                .sorted(Comparator.comparingInt(RetrievalStepDto::getStepOrder))
                .collect(Collectors.toList());
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}