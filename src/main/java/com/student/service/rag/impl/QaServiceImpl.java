package com.student.service.rag.impl;

import com.student.config.RagConfig;
import com.student.service.*;
import com.student.service.rag.CrossEncoderRerankService;
import com.student.service.rag.MultiRetrievalService;
import com.student.service.rag.QaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 问答服务实现类
 * 实现完整的RAG问答流程：检索 -> 重排序 -> 生成回答
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QaServiceImpl implements QaService {

    private final MultiRetrievalService multiRetrievalService;
    private final CrossEncoderRerankService rerankService;
    private final LlmService llmService;
    private final RedisService redisService;
    private final RagConfig ragConfig;

    // 统计信息
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger successfulQueries = new AtomicInteger(0);
    private final AtomicInteger failedQueries = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger totalRetrievedDocuments = new AtomicInteger(0);
    private final AtomicInteger totalGeneratedTokens = new AtomicInteger(0);

    // 缓存：查询 -> 问答结果（可选）
    private final Map<String, QaResponse> responseCache = new ConcurrentHashMap<>();

    @Override
    public QaResponse answer(String query, Long userId, Long conversationId) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();

        // 检查缓存
        if (ragConfig.getCache().getQuery().isEnabled()) {
            String cacheKey = generateCacheKey(query, userId, conversationId);
            Object cached = redisService.get(cacheKey);
            if (cached instanceof QaResponse) {
                log.debug("问答缓存命中: query={}, userId={}, conversationId={}",
                        query.substring(0, Math.min(query.length(), 50)), userId, conversationId);
                return (QaResponse) cached;
            }
        }

        try {
            // 1. 检索文档
            List<MultiRetrievalService.RetrievalResult> retrievalResults = retrieveDocuments(query);
            totalRetrievedDocuments.addAndGet(retrievalResults.size());

            // 2. 重排序（如果启用）
            List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankDocuments(query, retrievalResults);

            // 3. 提取上下文内容
            List<String> context = extractContext(rerankedResults);

            // 4. 生成回答
            String answer = generateAnswer(query, context);

            // 5. 计算置信度
            double confidence = calculateConfidence(rerankedResults, answer);

            // 6. 构建响应
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            successfulQueries.incrementAndGet();

            QaResponse response = new QaResponse(
                    query,
                    answer,
                    context.stream().limit(3).collect(Collectors.toList()), // 只返回前3个上下文摘要
                    confidence,
                    processingTime,
                    llmService.getModelInfo().getName()
            );

            // 7. 缓存结果
            if (ragConfig.getCache().getQuery().isEnabled()) {
                String cacheKey = generateCacheKey(query, userId, conversationId);
                redisService.set(cacheKey, response, (long) ragConfig.getCache().getQuery().getTtl());
                log.debug("问答缓存设置: query={}, userId={}, conversationId={}",
                        query.substring(0, Math.min(query.length(), 50)), userId, conversationId);
            }

            log.info("问答完成: query={}, contextCount={}, answerLength={}, confidence={}, time={}ms",
                    query, context.size(), answer.length(), confidence, processingTime);

            return response;

        } catch (Exception e) {
            log.error("问答异常: query={}, userId={}, conversationId={}", query, userId, conversationId, e);
            failedQueries.incrementAndGet();
            return getFallbackResponse(query, startTime);
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

        try {
            // 1. 检索文档
            List<MultiRetrievalService.RetrievalResult> retrievalResults = retrieveDocuments(query);
            totalRetrievedDocuments.addAndGet(retrievalResults.size());

            // 2. 重排序（如果启用）
            List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankDocuments(query, retrievalResults);

            // 3. 构建带来源的上下文
            List<LlmService.ContextWithSource> contextWithSources = buildContextWithSources(rerankedResults);

            // 4. 生成带引用的回答
            LlmService.AnswerWithCitations answerWithCitations =
                    llmService.generateAnswerWithCitations(query, contextWithSources);

            // 5. 构建来源引用信息
            List<SourceCitation> citations = buildSourceCitations(
                    rerankedResults, answerWithCitations.getCitations());

            // 6. 计算置信度
            double confidence = calculateConfidence(rerankedResults, answerWithCitations.getAnswer());

            // 7. 构建响应
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            successfulQueries.incrementAndGet();

            List<String> contextSummary = contextWithSources.stream()
                    .map(LlmService.ContextWithSource::getContent)
                    .limit(3)
                    .collect(Collectors.toList());

            QaResponseWithSources response = new QaResponseWithSources(
                    query,
                    answerWithCitations.getAnswer(),
                    contextSummary,
                    confidence,
                    processingTime,
                    llmService.getModelInfo().getName(),
                    citations
            );

            log.info("带来源问答完成: query={}, sources={}, answerLength={}, confidence={}, time={}ms",
                    query, citations.size(), answerWithCitations.getAnswer().length(),
                    confidence, processingTime);

            return response;

        } catch (Exception e) {
            log.error("带来源问答异常: query={}, userId={}, conversationId={}",
                    query, userId, conversationId, e);
            failedQueries.incrementAndGet();
            // 降级为普通问答
            QaResponse fallback = getFallbackResponse(query, startTime);
            return new QaResponseWithSources(
                    fallback.getQuery(),
                    fallback.getAnswer(),
                    fallback.getRetrievedContext(),
                    fallback.getConfidence(),
                    fallback.getProcessingTime(),
                    fallback.getModelUsed(),
                    Collections.emptyList()
            );
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
        return multiRetrievalService != null && rerankService != null && llmService != null &&
                multiRetrievalService.getCurrentMode() != null &&
                rerankService.isAvailable() &&
                llmService.isAvailable();
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
        return results != null ? results : Collections.emptyList();
    }

    /**
     * 重排序文档
     */
    private List<MultiRetrievalService.RetrievalResult> rerankDocuments(
            String query,
            List<MultiRetrievalService.RetrievalResult> results
    ) {
        if (results.isEmpty() || !rerankService.isAvailable()) {
            return results;
        }

        // 使用重排序服务
        List<MultiRetrievalService.RetrievalResult> rerankedResults =
                rerankService.rerank(query, results, ragConfig.getRetrieval().getRerankTopK());

        log.debug("重排序完成: query={}, input={}, output={}",
                query, results.size(), rerankedResults.size());
        return rerankedResults != null ? rerankedResults : results;
    }

    /**
     * 提取上下文内容
     */
    private List<String> extractContext(List<MultiRetrievalService.RetrievalResult> results) {
        return results.stream()
                .map(MultiRetrievalService.RetrievalResult::getContent)
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
                    citations.add(new SourceCitation(
                            String.valueOf(result.getDocumentId()),
                            String.valueOf(result.getChunkId()),
                            "文档片段",
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
     * 生成缓存键
     */
    private String generateCacheKey(String query, Long userId, Long conversationId) {
        String keyContent = String.format("%s:%s:%s", query, userId, conversationId);
        // 使用MD5哈希作为缓存键
        byte[] bytes = keyContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = org.springframework.util.DigestUtils.md5DigestAsHex(bytes);
        return String.format("qa:response:%s", hash);
    }

    /**
     * 降级响应（当问答失败时）
     */
    private QaResponse getFallbackResponse(String query, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        String fallbackAnswer = "抱歉，当前无法处理您的查询。请检查网络连接或稍后重试。";

        return new QaResponse(
                query,
                fallbackAnswer,
                Collections.emptyList(),
                0.1, // 低置信度
                processingTime,
                "fallback"
        );
    }
}