package com.student.service.evaluation;

import java.util.List;
import java.util.Set;

/**
 * 融合质量评估器接口
 * 评估RRF融合前后对比、双路召回互补性
 *
 * @author 系统
 * @version 1.0
 */
public interface FusionEvaluator {

    /**
     * 融合评估结果
     */
    class FusionEvalResult {
        /** RRF融合后MRR */
        public double mrrFused;
        /** 仅向量检索MRR */
        public double mrrVectorOnly;
        /** 仅关键词检索MRR */
        public double mrrKeywordOnly;
        /** 互补度 */
        public double complementarityScore;
        /** 向量独有贡献率 */
        public double vectorUniqueContribution;
        /** 关键词独有贡献率 */
        public double keywordUniqueContribution;
        /** 双路重叠率 */
        public double overlapRate;
        /** MRR提升幅度 */
        public double mrrImprovement;
    }

    /**
     * 评估融合质量
     *
     * @param vectorChunkIdsList   每个查询的向量检索结果
     * @param keywordChunkIdsList  每个查询的关键词检索结果
     * @param fusedChunkIdsList    每个查询的RRF融合结果
     * @param groundTruthChunkIds  每个查询的ground truth
     * @return 融合评估结果
     */
    FusionEvalResult evaluate(
            List<List<Long>> vectorChunkIdsList,
            List<List<Long>> keywordChunkIdsList,
            List<List<Long>> fusedChunkIdsList,
            List<Set<Long>> groundTruthChunkIds);
}
