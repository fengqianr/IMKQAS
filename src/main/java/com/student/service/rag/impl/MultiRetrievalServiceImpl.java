package com.student.service.rag.impl;

import com.student.config.RagConfig;
import com.student.service.dataBase.MilvusService;
import com.student.service.rag.EmbeddingService;
import com.student.service.rag.KeywordRetrievalService;
import com.student.service.rag.MultiRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 多路检索服务实现类
 * 整合向量检索和关键词检索，实现混合检索和结果融合
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiRetrievalServiceImpl implements MultiRetrievalService {

    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final KeywordRetrievalService keywordRetrievalService;
    private final RagConfig ragConfig;

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
        // 使用配置中的默认权重
        RagConfig.RetrievalConfig.WeightsConfig weights = ragConfig.getRetrieval().getWeights();
        return hybridRetrieval(query, topK, weights.getVector(), weights.getKeyword());
    }

    @Override
    public List<RetrievalResult> hybridRetrieval(String query, int topK, double vectorWeight, double keywordWeight) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();
        hybridQueries.incrementAndGet();

        // 确保权重和为1
        double totalWeight = vectorWeight + keywordWeight;
        if (totalWeight != 1.0) {
            vectorWeight = vectorWeight / totalWeight;
            keywordWeight = keywordWeight / totalWeight;
        }

        try {
            // 1. 并行执行向量检索和关键词检索
            CompletableFuture<List<RetrievalResult>> vectorFuture = CompletableFuture.supplyAsync(
                    () -> vectorRetrieval(query, topK * 2), // 获取更多结果用于融合
                    executorService
            );

            CompletableFuture<List<RetrievalResult>> keywordFuture = CompletableFuture.supplyAsync(
                    () -> keywordRetrieval(query, topK * 2), // 获取更多结果用于融合
                    executorService
            );

            // 2. 等待两个检索完成
            CompletableFuture.allOf(vectorFuture, keywordFuture).get(
                    ragConfig.getRetrieval().getTimeout(),
                    TimeUnit.MILLISECONDS
            );

            List<RetrievalResult> vectorResults = vectorFuture.get();
            List<RetrievalResult> keywordResults = keywordFuture.get();

            // 3. 融合结果
            List<RetrievalResult> fusedResults = fuseResults(
                    vectorResults, keywordResults,
                    vectorWeight, keywordWeight,
                    topK
            );

            // 4. 更新统计信息
            long responseTime = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(responseTime);
            hybridSuccessCount.incrementAndGet();

            log.info("混合检索完成: query={}, vectorResults={}, keywordResults={}, fusedResults={}, time={}ms, weights=[vector={}, keyword={}]",
                    query, vectorResults.size(), keywordResults.size(), fusedResults.size(),
                    responseTime, vectorWeight, keywordWeight);

            return fusedResults;

        } catch (TimeoutException e) {
            log.error("混合检索超时: query={}, timeout={}ms", query, ragConfig.getRetrieval().getTimeout());
            // 超时后尝试降级检索
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
     * 融合向量检索和关键词检索结果
     */
    private List<RetrievalResult> fuseResults(
            List<RetrievalResult> vectorResults,
            List<RetrievalResult> keywordResults,
            double vectorWeight,
            double keywordWeight,
            int topK
    ) {
        if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
            return Collections.emptyList();
        }

        if (vectorResults.isEmpty()) {
            return keywordResults.stream()
                    .limit(topK)
                    .collect(Collectors.toList());
        }

        if (keywordResults.isEmpty()) {
            return vectorResults.stream()
                    .limit(topK)
                    .collect(Collectors.toList());
        }

        // 创建结果映射，用于去重和分数融合
        Map<Long, FusedResult> fusedMap = new HashMap<>();

        // 处理向量检索结果
        for (int i = 0; i < vectorResults.size(); i++) {
            RetrievalResult result = vectorResults.get(i);
            double normalizedScore = 1.0 / (i + 1); // 使用排名的倒数作为标准化分数
            double weightedScore = normalizedScore * vectorWeight;

            FusedResult fusedResult = new FusedResult(result);
            fusedResult.vectorScore = result.getVectorScore();
            fusedResult.weightedScore = weightedScore;
            fusedMap.put(result.getChunkId(), fusedResult);
        }

        // 处理关键词检索结果
        for (int i = 0; i < keywordResults.size(); i++) {
            RetrievalResult result = keywordResults.get(i);
            double normalizedScore = 1.0 / (i + 1); // 使用排名的倒数作为标准化分数
            double weightedScore = normalizedScore * keywordWeight;

            FusedResult fusedResult = fusedMap.get(result.getChunkId());
            if (fusedResult == null) {
                fusedResult = new FusedResult(result);
                fusedResult.keywordScore = result.getKeywordScore();
                fusedResult.weightedScore = weightedScore;
                fusedMap.put(result.getChunkId(), fusedResult);
            } else {
                fusedResult.keywordScore = result.getKeywordScore();
                fusedResult.weightedScore += weightedScore;
            }
        }

        // 转换为最终结果并排序
        List<FusedResult> fusedResults = new ArrayList<>(fusedMap.values());
        fusedResults.sort((a, b) -> Double.compare(b.weightedScore, a.weightedScore));

        // 转换为RetrievalResult
        List<RetrievalResult> finalResults = new ArrayList<>();
        for (FusedResult fusedResult : fusedResults) {
            // 计算最终分数（加权平均）
            double finalScore = fusedResult.weightedScore;

            RetrievalResult result = new RetrievalResult(
                    fusedResult.chunkId,
                    fusedResult.documentId,
                    finalScore,
                    fusedResult.content,
                    RetrievalSource.HYBRID,
                    fusedResult.vectorScore,
                    fusedResult.keywordScore
            );
            finalResults.add(result);

            if (finalResults.size() >= topK) {
                break;
            }
        }

        return finalResults;
    }

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
     * 融合结果内部类
     */
    private static class FusedResult {
        Long chunkId;
        Long documentId;
        String content;
        double vectorScore = 0.0;
        double keywordScore = 0.0;
        double weightedScore = 0.0;

        FusedResult(RetrievalResult result) {
            this.chunkId = result.getChunkId();
            this.documentId = result.getDocumentId();
            this.content = result.getContent();
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
}