package com.student.service.rag.impl;

import com.student.config.RagConfig;
import com.student.service.dataBase.MilvusService;
import com.student.service.rag.EmbeddingService;
import com.student.service.rag.KeywordRetrievalService;
import com.student.service.rag.MultiRetrievalService;
import com.student.service.rag.RrfFusionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多路检索服务实现类
 * 整合向量检索和关键词检索，使用标准RRF融合算法进行混合检索
 * 双路并行召回（各10条），通过RRF融合去重排序后输出候选集
 *
 * @author 系统
 * @version 2.0
 */
@Service
@Slf4j
public class MultiRetrievalServiceImpl implements MultiRetrievalService {

    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final KeywordRetrievalService keywordRetrievalService;
    private final RrfFusionService rrfFusionService;
    private final RagConfig ragConfig;

    public MultiRetrievalServiceImpl(EmbeddingService embeddingService,
                                     MilvusService milvusService,
                                     KeywordRetrievalService keywordRetrievalService,
                                     RrfFusionService rrfFusionService,
                                     RagConfig ragConfig) {
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
        this.keywordRetrievalService = keywordRetrievalService;
        this.rrfFusionService = rrfFusionService;
        this.ragConfig = ragConfig;
    }

    // 检索模式
    private volatile RetrievalMode currentMode = RetrievalMode.HYBRID;

    // 统计信息
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger vectorQueries = new AtomicInteger(0);
    private final AtomicInteger keywordQueries = new AtomicInteger(0);
    private final AtomicInteger hybridQueries = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicInteger vectorSuccessCount = new AtomicInteger(0);
    private final AtomicInteger keywordSuccessCount = new AtomicInteger(0);
    private final AtomicInteger hybridSuccessCount = new AtomicInteger(0);

    // 线程池用于并行检索
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    @Override
    public List<RetrievalResult> vectorRetrieval(String query, int topK) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();
        vectorQueries.incrementAndGet();

        try {
            // 1. 将查询文本向量化
            List<Float> queryVector = embeddingService.embed(query);
            if (queryVector == null || !embeddingService.validateVector(queryVector)) {
                log.error("查询向量化失败: query={}", query);
                return Collections.emptyList();
            }

            // 2. 在Milvus中进行向量检索
            List<MilvusService.SearchResult> milvusResults = milvusService.searchSimilarVectors(queryVector, topK);
            if (milvusResults == null || milvusResults.isEmpty()) {
                log.warn("向量检索无结果: query={}", query);
                return Collections.emptyList();
            }

            // 3. 转换为RetrievalResult格式
            List<RetrievalResult> results = new ArrayList<>();
            for (MilvusService.SearchResult milvusResult : milvusResults) {
                RetrievalResult result = new RetrievalResult(
                        milvusResult.getChunkId(),
                        milvusResult.getDocumentId(),
                        milvusResult.getScore().doubleValue(), // Float转Double
                        milvusResult.getContent(),
                        RetrievalSource.VECTOR,
                        milvusResult.getScore().doubleValue(), // 向量分数，Float转Double
                        0.0 // 关键词分数
                );
                results.add(result);
            }

            // 4. 更新统计信息
            long responseTime = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(responseTime);
            vectorSuccessCount.incrementAndGet();

            log.debug("向量检索完成: query={}, results={}, time={}ms", query, results.size(), responseTime);
            return results;

        } catch (Exception e) {
            log.error("向量检索异常: query={}", query, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<RetrievalResult> keywordRetrieval(String query, int topK) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();
        keywordQueries.incrementAndGet();

        try {
            // 1. 进行关键词检索（带同义词扩展）
            List<KeywordRetrievalService.SearchResult> keywordResults =
                    keywordRetrievalService.searchWithSynonyms(query, true, topK);

            if (keywordResults == null || keywordResults.isEmpty()) {
                log.warn("关键词检索无结果: query={}", query);
                return Collections.emptyList();
            }

            // 2. 转换为RetrievalResult格式
            List<RetrievalResult> results = new ArrayList<>();
            for (KeywordRetrievalService.SearchResult keywordResult : keywordResults) {
                RetrievalResult result = new RetrievalResult(
                        keywordResult.getChunkId(),
                        keywordResult.getDocumentId(),
                        keywordResult.getScore(),
                        keywordResult.getContent(),
                        RetrievalSource.KEYWORD,
                        0.0, // 向量分数
                        keywordResult.getScore() // 关键词分数
                );
                results.add(result);
            }

            // 3. 更新统计信息
            long responseTime = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(responseTime);
            keywordSuccessCount.incrementAndGet();

            log.debug("关键词检索完成: query={}, results={}, time={}ms", query, results.size(), responseTime);
            return results;

        } catch (Exception e) {
            log.error("关键词检索异常: query={}", query, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<RetrievalResult> hybridRetrieval(String query, int topK) {
        RagConfig.RetrievalConfig.WeightsConfig weights = ragConfig.getRetrieval().getWeights();
        return hybridRetrieval(query, topK, weights.getVector(), weights.getKeyword());
    }

    @Override
    public List<RetrievalResult> hybridRetrieval(String query, int topK, double vectorWeight, double keywordWeight) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();
        hybridQueries.incrementAndGet();

        // 归一化权重
        double totalWeight = vectorWeight + keywordWeight;
        if (totalWeight != 1.0) {
            vectorWeight = vectorWeight / totalWeight;
            keywordWeight = keywordWeight / totalWeight;
        }

        // 双路各召回 K=10，为RRF融合提供足够候选
        int perSideK = ragConfig.getRetrieval().getInitialTopK();

        try {
            // 1. 并行执行向量检索和关键词检索（各 K=10）
            CompletableFuture<List<RetrievalResult>> vectorFuture = CompletableFuture.supplyAsync(
                    () -> vectorRetrieval(query, perSideK),
                    executorService
            );

            CompletableFuture<List<RetrievalResult>> keywordFuture = CompletableFuture.supplyAsync(
                    () -> keywordRetrieval(query, perSideK),
                    executorService
            );

            // 2. 等待双路检索完成
            CompletableFuture.allOf(vectorFuture, keywordFuture).get(
                    ragConfig.getRetrieval().getTimeout(),
                    TimeUnit.MILLISECONDS
            );

            List<RetrievalResult> vectorResults = vectorFuture.get();
            List<RetrievalResult> keywordResults = keywordFuture.get();

            // 3. 使用标准RRF融合算法
            List<RetrievalResult> fusedResults = rrfFusionService.fuseVectorAndKeyword(
                    vectorResults, keywordResults,
                    vectorWeight, keywordWeight,
                    topK
            );

            // 4. 更新统计信息
            long responseTime = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(responseTime);
            hybridSuccessCount.incrementAndGet();

            log.info("混合检索(RRF)完成: query={}, vector={}, keyword={}, fused={}, time={}ms, k={}",
                    truncate(query), vectorResults.size(), keywordResults.size(),
                    fusedResults.size(), responseTime, ragConfig.getRetrieval().getRrfK());

            return fusedResults;

        } catch (TimeoutException e) {
            log.error("混合检索超时: query={}, timeout={}ms", query, ragConfig.getRetrieval().getTimeout());
            return fallbackRetrieval(query, topK, vectorWeight > keywordWeight);
        } catch (Exception e) {
            log.error("混合检索异常: query={}", query, e);
            return fallbackRetrieval(query, topK, vectorWeight > keywordWeight);
        }
    }

    @Override
    public RetrievalMode getCurrentMode() {
        return currentMode;
    }

    @Override
    public void setMode(RetrievalMode mode) {
        this.currentMode = mode;
        log.info("检索模式已切换为: {}", mode);
    }

    @Override
    public RetrievalStats getStats() {
        int total = totalQueries.get();
        double avgTime = total > 0 ? (double) totalResponseTime.get() / total : 0.0;

        double vectorSuccessRate = vectorQueries.get() > 0 ?
                (double) vectorSuccessCount.get() / vectorQueries.get() : 0.0;
        double keywordSuccessRate = keywordQueries.get() > 0 ?
                (double) keywordSuccessCount.get() / keywordQueries.get() : 0.0;
        double hybridSuccessRate = hybridQueries.get() > 0 ?
                (double) hybridSuccessCount.get() / hybridQueries.get() : 0.0;

        return new RetrievalStats(
                total,
                vectorQueries.get(),
                keywordQueries.get(),
                hybridQueries.get(),
                avgTime,
                vectorSuccessRate,
                keywordSuccessRate,
                hybridSuccessRate
        );
    }

    // ========== 私有辅助方法 ==========

    /**
     * 降级检索（当混合检索失败时）
     */
    private List<RetrievalResult> fallbackRetrieval(String query, int topK, boolean preferVector) {
        log.warn("混合检索失败，尝试降级检索: query={}, preferVector={}", query, preferVector);

        if (preferVector) {
            // 优先使用向量检索
            List<RetrievalResult> vectorResults = vectorRetrieval(query, topK);
            if (!vectorResults.isEmpty()) {
                return vectorResults;
            }
            // 向量检索失败，尝试关键词检索
            return keywordRetrieval(query, topK);
        } else {
            // 优先使用关键词检索
            List<RetrievalResult> keywordResults = keywordRetrieval(query, topK);
            if (!keywordResults.isEmpty()) {
                return keywordResults;
            }
            // 关键词检索失败，尝试向量检索
            return vectorRetrieval(query, topK);
        }
    }

    /**
     * 服务销毁时清理资源
     */
    public void destroy() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            log.info("多路检索服务资源清理完成");
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("多路检索服务资源清理被中断", e);
        }
    }

    private static String truncate(String s) {
        return s != null && s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}