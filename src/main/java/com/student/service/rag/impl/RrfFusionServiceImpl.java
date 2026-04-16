package com.student.service.rag.impl;

import com.student.config.RagConfig;
import com.student.service.rag.MultiRetrievalService;
import com.student.service.rag.RrfFusionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * RRF融合服务实现类
 * 实现Reciprocal Rank Fusion算法，支持多路检索结果融合
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RrfFusionServiceImpl implements RrfFusionService {

    private final RagConfig ragConfig;

    // RRF算法k参数（从配置读取，默认60）
    private int rrfK;

    /**
     * 初始化方法，从配置读取RRF参数
     */
    @PostConstruct
    public void init() {
        this.rrfK = ragConfig.getRetrieval().getRrfK();
        log.info("RRF融合服务初始化完成，rrfK={}", this.rrfK);
    }

    // 统计信息
    private final AtomicInteger totalFusions = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger totalInputResults = new AtomicInteger(0);
    private final AtomicInteger totalOutputResults = new AtomicInteger(0);

    @Override
    public List<MultiRetrievalService.RetrievalResult> fuse(
            List<List<MultiRetrievalService.RetrievalResult>> resultsList,
            List<Double> weights,
            int topK
    ) {
        return fuse(resultsList, weights, topK, rrfK);
    }

    @Override
    public List<MultiRetrievalService.RetrievalResult> fuse(
            List<List<MultiRetrievalService.RetrievalResult>> resultsList,
            List<Double> weights,
            int topK,
            int rrfK
    ) {
        long startTime = System.currentTimeMillis();
        totalFusions.incrementAndGet();

        try {
            // 参数校验
            if (resultsList == null || resultsList.isEmpty()) {
                log.warn("RRF融合: 输入结果列表为空");
                return Collections.emptyList();
            }

            // 如果权重为空或长度不匹配，使用等权重
            List<Double> normalizedWeights = normalizeWeights(weights, resultsList.size());

            // 应用RRF融合算法
            List<MultiRetrievalService.RetrievalResult> fusedResults = applyRrfFusion(
                    resultsList, normalizedWeights, topK, rrfK
            );

            // 更新统计信息
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            int inputCount = resultsList.stream().mapToInt(List::size).sum();
            totalInputResults.addAndGet(inputCount);
            totalOutputResults.addAndGet(fusedResults.size());

            log.debug("RRF融合完成: inputLists={}, inputResults={}, outputResults={}, time={}ms, k={}",
                    resultsList.size(), inputCount, fusedResults.size(), processingTime, rrfK);

            return fusedResults;

        } catch (Exception e) {
            log.error("RRF融合异常", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<MultiRetrievalService.RetrievalResult> fuseVectorAndKeyword(
            List<MultiRetrievalService.RetrievalResult> vectorResults,
            List<MultiRetrievalService.RetrievalResult> keywordResults,
            double vectorWeight,
            double keywordWeight,
            int topK
    ) {
        List<List<MultiRetrievalService.RetrievalResult>> resultsList = new ArrayList<>();
        resultsList.add(vectorResults != null ? vectorResults : Collections.emptyList());
        resultsList.add(keywordResults != null ? keywordResults : Collections.emptyList());

        List<Double> weights = Arrays.asList(vectorWeight, keywordWeight);

        return fuse(resultsList, weights, topK);
    }

    @Override
    public int getDefaultRrfK() {
        return rrfK;
    }

    @Override
    public void setRrfK(int rrfK) {
        if (rrfK <= 0) {
            throw new IllegalArgumentException("RRF k参数必须大于0");
        }
        this.rrfK = rrfK;
        log.info("RRF k参数已更新为: {}", rrfK);
    }

    @Override
    public FusionStats getStats() {
        int totalFusionsCount = totalFusions.get();
        double avgInput = totalFusionsCount > 0 ?
                (double) totalInputResults.get() / totalFusionsCount : 0.0;
        double avgOutput = totalFusionsCount > 0 ?
                (double) totalOutputResults.get() / totalFusionsCount : 0.0;
        double avgTime = totalFusionsCount > 0 ?
                (double) totalProcessingTime.get() / totalFusionsCount : 0.0;

        return new FusionStats(totalFusionsCount, avgInput, avgOutput, avgTime);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 标准化权重列表
     */
    private List<Double> normalizeWeights(List<Double> weights, int expectedSize) {
        if (weights == null || weights.size() != expectedSize) {
            // 创建等权重列表
            List<Double> equalWeights = new ArrayList<>();
            double equalWeight = 1.0 / expectedSize;
            for (int i = 0; i < expectedSize; i++) {
                equalWeights.add(equalWeight);
            }
            return equalWeights;
        }

        // 确保权重和为1
        double sum = weights.stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 0.0001) {
            return weights.stream()
                    .map(w -> w / sum)
                    .collect(Collectors.toList());
        }

        return weights;
    }

    /**
     * 应用RRF融合算法
     */
    private List<MultiRetrievalService.RetrievalResult> applyRrfFusion(
            List<List<MultiRetrievalService.RetrievalResult>> resultsList,
            List<Double> weights,
            int topK,
            int rrfK
    ) {
        // 创建结果映射，用于去重和分数累积
        Map<Long, RrfFusedResult> fusedMap = new HashMap<>();

        // 处理每个结果列表
        for (int listIndex = 0; listIndex < resultsList.size(); listIndex++) {
            List<MultiRetrievalService.RetrievalResult> results = resultsList.get(listIndex);
            double weight = weights.get(listIndex);

            if (results == null || results.isEmpty()) {
                continue;
            }

            // 为当前列表中的每个结果计算RRF分数
            for (int rank = 0; rank < results.size(); rank++) {
                MultiRetrievalService.RetrievalResult result = results.get(rank);
                Long chunkId = result.getChunkId();

                // 计算RRF分数: 1 / (k + rank)
                // rank从0开始，所以实际排名是rank+1
                double rrfScore = 1.0 / (rrfK + (rank + 1));
                double weightedScore = rrfScore * weight;

                // 累积分数
                RrfFusedResult fusedResult = fusedMap.get(chunkId);
                if (fusedResult == null) {
                    fusedResult = new RrfFusedResult(result);
                    fusedResult.addScore(weightedScore);
                    fusedMap.put(chunkId, fusedResult);
                } else {
                    fusedResult.addScore(weightedScore);
                }

                // 记录原始分数（用于调试）
                if (listIndex == 0) {
                    fusedResult.vectorScore = result.getVectorScore();
                } else if (listIndex == 1) {
                    fusedResult.keywordScore = result.getKeywordScore();
                }
            }
        }

        // 转换为列表并排序
        List<RrfFusedResult> fusedResults = new ArrayList<>(fusedMap.values());
        fusedResults.sort((a, b) -> Double.compare(b.rrfScore, a.rrfScore));

        // 转换为RetrievalResult格式
        List<MultiRetrievalService.RetrievalResult> finalResults = new ArrayList<>();
        for (RrfFusedResult fusedResult : fusedResults) {
            // 创建融合结果
            MultiRetrievalService.RetrievalResult result = new MultiRetrievalService.RetrievalResult(
                    fusedResult.chunkId,
                    fusedResult.documentId,
                    fusedResult.rrfScore, // 使用RRF分数作为最终分数
                    fusedResult.content,
                    MultiRetrievalService.RetrievalSource.HYBRID,
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
     * RRF融合结果内部类
     */
    private static class RrfFusedResult {
        Long chunkId;
        Long documentId;
        String content;
        double rrfScore = 0.0;
        double vectorScore = 0.0;
        double keywordScore = 0.0;

        RrfFusedResult(MultiRetrievalService.RetrievalResult result) {
            this.chunkId = result.getChunkId();
            this.documentId = result.getDocumentId();
            this.content = result.getContent();
        }

        void addScore(double score) {
            this.rrfScore += score;
        }
    }
}