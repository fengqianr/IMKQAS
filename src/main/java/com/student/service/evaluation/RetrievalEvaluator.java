package com.student.service.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检索质量评估器接口
 * 评估RAG管线[4]双路召回和[5]RRF融合的检索质量
 *
 * @author 系统
 * @version 1.0
 */
public interface RetrievalEvaluator {

    /**
     * 检索评估结果
     */
    class RetrievalEvalResult {
        /** Recall@K 值 */
        public double recallAt1;
        public double recallAt5;
        public double recallAt10;
        public double recallAt20;
        /** Precision@K 值 */
        public double precisionAt5;
        public double precisionAt10;
        /** MRR */
        public double mrr;
        /** NDCG@10 */
        public double ndcgAt10;
        /** Hit Rate@5 */
        public double hitRateAt5;
        /** 总体命中率 */
        public double overallHitRate;
        /** 评估查询数 */
        public int evaluatedQueries;
        /** 详细逐查询结果（firstRelevantRank列表） */
        public List<Integer> firstRelevantRanks;
    }

    /**
     * 评估检索质量
     *
     * @param allRetrievedChunkIds 每个查询的检索结果（有序chunkId列表）
     * @param groundTruthChunkIds  每个查询的ground truth chunkId集合
     * @param relevanceLabels      每个查询的片段级相关性标注
     * @param kValues              K值列表
     * @return 检索评估结果
     */
    RetrievalEvalResult evaluate(
            List<List<Long>> allRetrievedChunkIds,
            List<Set<Long>> groundTruthChunkIds,
            List<Map<Long, Double>> relevanceLabels,
            List<Integer> kValues);
}
