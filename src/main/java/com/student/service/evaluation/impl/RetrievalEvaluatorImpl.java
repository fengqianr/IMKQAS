package com.student.service.evaluation.impl;

import com.student.service.evaluation.RetrievalEvaluator;
import com.student.utils.evaluation.MetricsCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 检索质量评估器实现
 * 计算Recall@K、Precision@K、MRR、NDCG@K、HitRate等指标
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
public class RetrievalEvaluatorImpl implements RetrievalEvaluator {

    @Override
    public RetrievalEvalResult evaluate(
            List<List<Long>> allRetrievedChunkIds,
            List<Set<Long>> groundTruthChunkIds,
            List<Map<Long, Double>> relevanceLabels,
            List<Integer> kValues) {

        if (allRetrievedChunkIds == null || allRetrievedChunkIds.isEmpty()) {
            log.warn("检索结果为空，无法评估");
            return new RetrievalEvalResult();
        }

        int n = allRetrievedChunkIds.size();
        RetrievalEvalResult result = new RetrievalEvalResult();
        result.evaluatedQueries = n;

        // 逐查询计算指标
        List<Integer> firstRelevantRanks = new ArrayList<>();
        List<Boolean> hitFlags = new ArrayList<>();
        List<Double> recall1List = new ArrayList<>();
        List<Double> recall5List = new ArrayList<>();
        List<Double> recall10List = new ArrayList<>();
        List<Double> recall20List = new ArrayList<>();
        List<Double> precision5List = new ArrayList<>();
        List<Double> precision10List = new ArrayList<>();
        List<Double> ndcg10List = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            List<Long> retrieved = allRetrievedChunkIds.get(i);
            Set<Long> groundTruth = i < groundTruthChunkIds.size() ? groundTruthChunkIds.get(i) : Collections.emptySet();
            Map<Long, Double> labels = i < relevanceLabels.size() ? relevanceLabels.get(i) : Collections.emptyMap();

            // First relevant rank
            int firstRank = MetricsCalculator.findFirstRelevantRank(retrieved, groundTruth);
            firstRelevantRanks.add(firstRank);

            // Hit flag
            hitFlags.add(firstRank > 0 && firstRank <= 5);

            // Recall@K
            recall1List.add(MetricsCalculator.recallAtK(retrieved, groundTruth, 1));
            recall5List.add(MetricsCalculator.recallAtK(retrieved, groundTruth, 5));
            recall10List.add(MetricsCalculator.recallAtK(retrieved, groundTruth, 10));
            recall20List.add(MetricsCalculator.recallAtK(retrieved, groundTruth, 20));

            // Precision@K
            precision5List.add(MetricsCalculator.precisionAtK(retrieved, groundTruth, 5));
            precision10List.add(MetricsCalculator.precisionAtK(retrieved, groundTruth, 10));

            // NDCG@10
            ndcg10List.add(MetricsCalculator.ndcgAtK(retrieved, labels, 10));
        }

        // 汇总
        result.recallAt1 = MetricsCalculator.average(recall1List);
        result.recallAt5 = MetricsCalculator.average(recall5List);
        result.recallAt10 = MetricsCalculator.average(recall10List);
        result.recallAt20 = MetricsCalculator.average(recall20List);
        result.precisionAt5 = MetricsCalculator.average(precision5List);
        result.precisionAt10 = MetricsCalculator.average(precision10List);
        result.mrr = MetricsCalculator.mrr(firstRelevantRanks);
        result.ndcgAt10 = MetricsCalculator.average(ndcg10List);
        result.hitRateAt5 = MetricsCalculator.hitRateAtK(hitFlags);
        result.overallHitRate = MetricsCalculator.hitRateAtK(
                firstRelevantRanks.stream().map(r -> r > 0).toList());
        result.firstRelevantRanks = firstRelevantRanks;

        log.info("检索评估完成: {} 条查询, Recall@5={:.4f}, MRR={:.4f}, NDCG@10={:.4f}",
                n, result.recallAt5, result.mrr, result.ndcgAt10);

        return result;
    }
}
