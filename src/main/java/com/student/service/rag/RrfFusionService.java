package com.student.service.rag;

import java.util.List;

/**
 * RRF (Reciprocal Rank Fusion) 融合服务接口
 * 实现多路检索结果的RRF融合算法，支持可配置权重
 *
 * @author 系统
 * @version 1.0
 */
public interface RrfFusionService {

    /**
     * RRF融合算法实现
     * 公式: score = Σ(1 / (k + rank_i))
     *
     * @param resultsList 多个检索结果列表，每个列表已按相关性排序
     * @param weights 各列表的权重（可选，默认等权重）
     * @param topK 返回结果数量
     * @return 融合后的结果列表，按RRF分数降序排列
     */
    List<MultiRetrievalService.RetrievalResult> fuse(
            List<List<MultiRetrievalService.RetrievalResult>> resultsList,
            List<Double> weights,
            int topK
    );

    /**
     * RRF融合算法实现（带RRF参数k）
     *
     * @param resultsList 多个检索结果列表
     * @param weights 各列表的权重
     * @param topK 返回结果数量
     * @param rrfK RRF算法中的k参数（通常为60）
     * @return 融合后的结果列表
     */
    List<MultiRetrievalService.RetrievalResult> fuse(
            List<List<MultiRetrievalService.RetrievalResult>> resultsList,
            List<Double> weights,
            int topK,
            int rrfK
    );

    /**
     * 融合向量检索和关键词检索结果（便捷方法）
     *
     * @param vectorResults 向量检索结果
     * @param keywordResults 关键词检索结果
     * @param vectorWeight 向量检索权重
     * @param keywordWeight 关键词检索权重
     * @param topK 返回结果数量
     * @return 融合后的结果列表
     */
    List<MultiRetrievalService.RetrievalResult> fuseVectorAndKeyword(
            List<MultiRetrievalService.RetrievalResult> vectorResults,
            List<MultiRetrievalService.RetrievalResult> keywordResults,
            double vectorWeight,
            double keywordWeight,
            int topK
    );

    /**
     * 获取默认RRF k参数
     *
     * @return 默认的k值
     */
    int getDefaultRrfK();

    /**
     * 设置RRF k参数
     *
     * @param rrfK RRF算法k参数
     */
    void setRrfK(int rrfK);

    /**
     * 获取融合统计信息
     *
     * @return 融合统计信息
     */
    FusionStats getStats();

    /**
     * 融合统计信息类
     */
    class FusionStats {
        private final int totalFusions;
        private final double averageInputResults;
        private final double averageOutputResults;
        private final double averageProcessingTimeMs;

        public FusionStats(int totalFusions, double averageInputResults,
                          double averageOutputResults, double averageProcessingTimeMs) {
            this.totalFusions = totalFusions;
            this.averageInputResults = averageInputResults;
            this.averageOutputResults = averageOutputResults;
            this.averageProcessingTimeMs = averageProcessingTimeMs;
        }

        public int getTotalFusions() {
            return totalFusions;
        }

        public double getAverageInputResults() {
            return averageInputResults;
        }

        public double getAverageOutputResults() {
            return averageOutputResults;
        }

        public double getAverageProcessingTimeMs() {
            return averageProcessingTimeMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "FusionStats{total=%d, avgInput=%.1f, avgOutput=%.1f, avgTime=%.2fms}",
                    totalFusions, averageInputResults, averageOutputResults, averageProcessingTimeMs
            );
        }
    }
}