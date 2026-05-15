package com.student.service.evaluation.impl;

import com.student.service.evaluation.FusionEvaluator;
import com.student.utils.evaluation.MetricsCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 融合质量评估器实现
 * 对比向量检索、关键词检索、RRF融合后的质量差异
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
public class FusionEvaluatorImpl implements FusionEvaluator {

    @Override
    public FusionEvalResult evaluate(
            List<List<Long>> vectorChunkIdsList,
            List<List<Long>> keywordChunkIdsList,
            List<List<Long>> fusedChunkIdsList,
            List<Set<Long>> groundTruthChunkIds) {

        FusionEvalResult result = new FusionEvalResult();

        if (vectorChunkIdsList == null || vectorChunkIdsList.isEmpty()) {
            log.warn("检索结果为空，无法评估融合质量");
            return result;
        }

        int n = vectorChunkIdsList.size();

        // 计算三组结果的MRR
        List<Integer> vectorRanks = new ArrayList<>();
        List<Integer> keywordRanks = new ArrayList<>();
        List<Integer> fusedRanks = new ArrayList<>();

        // 互补性和贡献率累计
        List<Double> complementarityList = new ArrayList<>();
        List<Double> vectorContribList = new ArrayList<>();
        List<Double> keywordContribList = new ArrayList<>();
        List<Double> overlapList = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            List<Long> vecIds = vectorChunkIdsList.get(i);
            List<Long> kwIds = i < keywordChunkIdsList.size() ? keywordChunkIdsList.get(i) : Collections.emptyList();
            List<Long> fusedIds = i < fusedChunkIdsList.size() ? fusedChunkIdsList.get(i) : Collections.emptyList();
            Set<Long> groundTruth = i < groundTruthChunkIds.size() ? groundTruthChunkIds.get(i) : Collections.emptySet();

            // 各路的first relevant rank
            vectorRanks.add(MetricsCalculator.findFirstRelevantRank(vecIds, groundTruth));
            keywordRanks.add(MetricsCalculator.findFirstRelevantRank(kwIds, groundTruth));
            fusedRanks.add(MetricsCalculator.findFirstRelevantRank(fusedIds, groundTruth));

            // 集合转换用于互补分析
            Set<Long> vecSet = new HashSet<>(vecIds);
            Set<Long> kwSet = new HashSet<>(kwIds);

            complementarityList.add(MetricsCalculator.complementarity(vecSet, kwSet));
            vectorContribList.add(MetricsCalculator.vectorUniqueContribution(vecSet, kwSet, groundTruth));
            keywordContribList.add(MetricsCalculator.keywordUniqueContribution(vecSet, kwSet, groundTruth));
            overlapList.add(MetricsCalculator.overlapRate(vecSet, kwSet));
        }

        // 汇总
        result.mrrVectorOnly = MetricsCalculator.mrr(vectorRanks);
        result.mrrKeywordOnly = MetricsCalculator.mrr(keywordRanks);
        result.mrrFused = MetricsCalculator.mrr(fusedRanks);
        result.complementarityScore = MetricsCalculator.average(complementarityList);
        result.vectorUniqueContribution = MetricsCalculator.average(vectorContribList);
        result.keywordUniqueContribution = MetricsCalculator.average(keywordContribList);
        result.overlapRate = MetricsCalculator.average(overlapList);

        // MRR提升幅度
        double maxSingle = Math.max(result.mrrVectorOnly, result.mrrKeywordOnly);
        result.mrrImprovement = maxSingle > 0 ? (result.mrrFused - maxSingle) / maxSingle : 0.0;

        log.info("融合评估完成: MRR提升={:+.1%}, 互补度={:.2f}, 向量独有贡献={:.2%}, 关键词独有贡献={:.2%}",
                result.mrrImprovement, result.complementarityScore,
                result.vectorUniqueContribution, result.keywordUniqueContribution);

        return result;
    }
}
