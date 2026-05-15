package com.student.utils.evaluation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评估指标计算工具类
 * 所有指标以纯函数实现，不依赖外部状态
 *
 * @author 系统
 * @version 1.0
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
        // 工具类不可实例化
    }

    // ==================== 检索质量指标 ====================

    /**
     * 计算 Recall@K
     * Recall@K = |R_k ∩ G| / |G|
     *
     * @param retrievedIds 检索返回的Top-K文档ID列表（有序）
     * @param groundTruthIds 标注的相关文档ID集合
     * @param k 截断值
     * @return Recall@K 值 [0, 1]
     */
    public static double recallAtK(List<Long> retrievedIds, Set<Long> groundTruthIds, int k) {
        if (groundTruthIds == null || groundTruthIds.isEmpty()) {
            return 0.0;
        }
        List<Long> topK = retrievedIds.subList(0, Math.min(k, retrievedIds.size()));
        long hitCount = topK.stream().filter(groundTruthIds::contains).count();
        return (double) hitCount / groundTruthIds.size();
    }

    /**
     * 计算 Precision@K
     * Precision@K = |R_k ∩ G| / K
     *
     * @param retrievedIds 检索返回的Top-K文档ID列表
     * @param groundTruthIds 标注的相关文档ID集合
     * @param k 截断值
     * @return Precision@K 值 [0, 1]
     */
    public static double precisionAtK(List<Long> retrievedIds, Set<Long> groundTruthIds, int k) {
        if (retrievedIds.isEmpty()) {
            return 0.0;
        }
        List<Long> topK = retrievedIds.subList(0, Math.min(k, retrievedIds.size()));
        long hitCount = topK.stream().filter(groundTruthIds::contains).count();
        return (double) hitCount / k;
    }

    /**
     * 计算 MRR (Mean Reciprocal Rank)
     * MRR = (1/N) * Σ (1 / rank_i)
     * rank_i 是第一个相关结果在排序列表中的位置（1-based），无命中则该查询贡献0
     *
     * @param firstRelevantRanks 每个查询的第一个相关结果排名列表
     * @return MRR 值 [0, 1]
     */
    public static double mrr(List<Integer> firstRelevantRanks) {
        if (firstRelevantRanks == null || firstRelevantRanks.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Integer rank : firstRelevantRanks) {
            if (rank != null && rank > 0) {
                sum += 1.0 / rank;
            }
        }
        return sum / firstRelevantRanks.size();
    }

    /**
     * 在检索结果中查找第一个相关结果的排名（1-based）
     *
     * @param retrievedIds 检索结果ID列表
     * @param groundTruthIds 标注的相关文档ID集合
     * @return 第一个相关排名，无命中返回 -1
     */
    public static int findFirstRelevantRank(List<Long> retrievedIds, Set<Long> groundTruthIds) {
        if (retrievedIds == null || groundTruthIds == null || groundTruthIds.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (groundTruthIds.contains(retrievedIds.get(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * 计算 DCG@K (Discounted Cumulative Gain)
     * DCG@K = Σ_{j=1}^{K} (2^{rel_j} - 1) / log_2(j + 1)
     *
     * @param relevanceScores 按排名排列的相关性分数列表
     * @param k 截断值
     * @return DCG@K 值
     */
    public static double dcgAtK(List<Double> relevanceScores, int k) {
        double dcg = 0.0;
        int limit = Math.min(k, relevanceScores.size());
        for (int i = 0; i < limit; i++) {
            double rel = relevanceScores.get(i);
            double gain = Math.pow(2, rel) - 1;
            double discount = Math.log(i + 2) / Math.log(2); // log_2(i + 2) 因为i从0开始，位置= i+1
            dcg += gain / discount;
        }
        return dcg;
    }

    /**
     * 计算 NDCG@K (Normalized DCG)
     * NDCG@K = DCG@K / IDCG@K
     *
     * @param retrievedIds 检索结果ID列表
     * @param relevanceLabels 片段级相关性标注 Map<chunkId, score>
     * @param k 截断值
     * @return NDCG@K 值 [0, 1]
     */
    public static double ndcgAtK(List<Long> retrievedIds, Map<Long, Double> relevanceLabels, int k) {
        if (retrievedIds.isEmpty() || relevanceLabels == null || relevanceLabels.isEmpty()) {
            return 0.0;
        }

        // 构建实际DCG
        List<Double> actualScores = new ArrayList<>();
        for (int i = 0; i < Math.min(k, retrievedIds.size()); i++) {
            Double score = relevanceLabels.get(retrievedIds.get(i));
            actualScores.add(score != null ? score : 0.0);
        }

        double dcg = dcgAtK(actualScores, k);

        // 构建理想DCG（按相关性降序排列）
        List<Double> idealScores = relevanceLabels.values().stream()
                .filter(s -> s > 0)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        // 如果标注的相关性分数足够多，填充到k个
        while (idealScores.size() < k) {
            idealScores.add(0.0);
        }

        double idcg = dcgAtK(idealScores, k);
        return idcg > 0 ? dcg / idcg : 0.0;
    }

    /**
     * 计算 Hit Rate@K
     * HitRate@K = (命中查询数) / N
     *
     * @param hitFlags 每个查询是否命中的标记列表
     * @return Hit Rate [0, 1]
     */
    public static double hitRateAtK(List<Boolean> hitFlags) {
        if (hitFlags == null || hitFlags.isEmpty()) {
            return 0.0;
        }
        long hits = hitFlags.stream().filter(Boolean::booleanValue).count();
        return (double) hits / hitFlags.size();
    }

    // ==================== 融合质量指标 ====================

    /**
     * 计算双路召回互补度
     * 互补度 = |R_v ∪ R_k| / max(|R_v|, |R_k|)
     *
     * @param vectorIds 向量检索结果ID集合
     * @param keywordIds 关键词检索结果ID集合
     * @return 互补度 [1.0, 2.0]，1.0表示完全重叠，2.0表示完全不重叠
     */
    public static double complementarity(Set<Long> vectorIds, Set<Long> keywordIds) {
        Set<Long> union = new HashSet<>(vectorIds);
        union.addAll(keywordIds);
        int maxSize = Math.max(vectorIds.size(), keywordIds.size());
        return maxSize > 0 ? (double) union.size() / maxSize : 1.0;
    }

    /**
     * 计算向量检索独有的相关贡献率
     * 独有贡献率_vector = |R_v \ R_k ∩ G| / |G|
     */
    public static double vectorUniqueContribution(Set<Long> vectorIds, Set<Long> keywordIds, Set<Long> groundTruthIds) {
        if (groundTruthIds == null || groundTruthIds.isEmpty()) {
            return 0.0;
        }
        Set<Long> vectorUnique = new HashSet<>(vectorIds);
        vectorUnique.removeAll(keywordIds);
        vectorUnique.retainAll(groundTruthIds);
        return (double) vectorUnique.size() / groundTruthIds.size();
    }

    /**
     * 计算关键词检索独有的相关贡献率
     * 独有贡献率_keyword = |R_k \ R_v ∩ G| / |G|
     */
    public static double keywordUniqueContribution(Set<Long> vectorIds, Set<Long> keywordIds, Set<Long> groundTruthIds) {
        if (groundTruthIds == null || groundTruthIds.isEmpty()) {
            return 0.0;
        }
        Set<Long> keywordUnique = new HashSet<>(keywordIds);
        keywordUnique.removeAll(vectorIds);
        keywordUnique.retainAll(groundTruthIds);
        return (double) keywordUnique.size() / groundTruthIds.size();
    }

    /**
     * 计算双路召回重叠率
     * 重叠率 = |R_v ∩ R_k| / min(|R_v|, |R_k|)
     */
    public static double overlapRate(Set<Long> vectorIds, Set<Long> keywordIds) {
        Set<Long> intersection = new HashSet<>(vectorIds);
        intersection.retainAll(keywordIds);
        int minSize = Math.min(vectorIds.size(), keywordIds.size());
        return minSize > 0 ? (double) intersection.size() / minSize : 0.0;
    }

    // ==================== 过滤质量指标 ====================

    /**
     * 计算过滤准确率
     * 过滤准确率 = |正确丢弃的低质文档| / |所有被丢弃的文档|
     */
    public static double filterPrecision(long correctlyDiscarded, long totalDiscarded) {
        return totalDiscarded > 0 ? (double) correctlyDiscarded / totalDiscarded : 0.0;
    }

    /**
     * 计算过滤召回率
     * 过滤召回率 = |正确丢弃的低质文档| / |所有应被丢弃的文档|
     */
    public static double filterRecall(long correctlyDiscarded, long totalShouldBeDiscarded) {
        return totalShouldBeDiscarded > 0 ? (double) correctlyDiscarded / totalShouldBeDiscarded : 0.0;
    }

    // ==================== 统计工具 ====================

    /**
     * 计算平均值
     */
    public static double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * 计算百分位数
     *
     * @param values 数值列表
     * @param percentile 百分位 (0-100)
     * @return 百分位数值
     */
    public static double percentile(List<Long> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    /**
     * 计算加权平均分
     *
     * @param scores 分数列表
     * @param weights 权重列表（与scores一一对应）
     * @return 加权平均分
     */
    public static double weightedAverage(List<Double> scores, List<Double> weights) {
        if (scores == null || weights == null || scores.size() != weights.size() || scores.isEmpty()) {
            return 0.0;
        }
        double sumWeighted = 0.0;
        double sumWeights = 0.0;
        for (int i = 0; i < scores.size(); i++) {
            sumWeighted += scores.get(i) * weights.get(i);
            sumWeights += weights.get(i);
        }
        return sumWeights > 0 ? sumWeighted / sumWeights : 0.0;
    }

    /**
     * 计算置信度分布（按区间分桶）
     *
     * @param confidences 置信度值列表
     * @return 四区间计数数组：[0-0.35), [0.35-0.6), [0.6-0.8), [0.8-1.0]
     */
    public static int[] confidenceDistribution(List<Double> confidences) {
        int[] buckets = new int[4];
        if (confidences == null) {
            return buckets;
        }
        for (Double c : confidences) {
            if (c == null) continue;
            if (c < 0.35) buckets[0]++;
            else if (c < 0.6) buckets[1]++;
            else if (c < 0.8) buckets[2]++;
            else buckets[3]++;
        }
        return buckets;
    }
}
