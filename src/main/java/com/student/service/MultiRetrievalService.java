package com.student.service;

import java.util.List;

/**
 * 多路检索服务接口
 * 协调向量检索、关键词检索和混合检索，实现多路检索融合
 *
 * @author 系统
 * @version 1.0
 */
public interface MultiRetrievalService {

    /**
     * 向量检索
     *
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    List<RetrievalResult> vectorRetrieval(String query, int topK);

    /**
     * 关键词检索
     *
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    List<RetrievalResult> keywordRetrieval(String query, int topK);

    /**
     * 混合检索（向量 + 关键词）
     *
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    List<RetrievalResult> hybridRetrieval(String query, int topK);

    /**
     * 混合检索（可配置权重）
     *
     * @param query 查询文本
     * @param topK 返回结果数量
     * @param vectorWeight 向量检索权重
     * @param keywordWeight 关键词检索权重
     * @return 检索结果列表
     */
    List<RetrievalResult> hybridRetrieval(String query, int topK, double vectorWeight, double keywordWeight);

    /**
     * 获取检索模式
     *
     * @return 当前检索模式
     */
    RetrievalMode getCurrentMode();

    /**
     * 设置检索模式
     *
     * @param mode 检索模式
     */
    void setMode(RetrievalMode mode);

    /**
     * 获取检索统计信息
     *
     * @return 检索统计信息
     */
    RetrievalStats getStats();

    /**
     * 检索模式枚举
     */
    enum RetrievalMode {
        VECTOR,     // 向量检索
        KEYWORD,    // 关键词检索
        HYBRID      // 混合检索
    }

    /**
     * 检索结果类
     */
    class RetrievalResult {
        private final Long chunkId;
        private final Long documentId;
        private final Double score;
        private final String content;
        private final RetrievalSource source; // 来源：向量/关键词/混合
        private final Double vectorScore;     // 向量检索分数
        private final Double keywordScore;    // 关键词检索分数

        public RetrievalResult(Long chunkId, Long documentId, Double score, String content,
                               RetrievalSource source, Double vectorScore, Double keywordScore) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.score = score;
            this.content = content;
            this.source = source;
            this.vectorScore = vectorScore;
            this.keywordScore = keywordScore;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public Double getScore() {
            return score;
        }

        public String getContent() {
            return content;
        }

        public RetrievalSource getSource() {
            return source;
        }

        public Double getVectorScore() {
            return vectorScore;
        }

        public Double getKeywordScore() {
            return keywordScore;
        }

        @Override
        public String toString() {
            return String.format("RetrievalResult{chunkId=%d, documentId=%d, score=%.4f, source=%s}",
                    chunkId, documentId, score, source);
        }
    }

    /**
     * 检索来源枚举
     */
    enum RetrievalSource {
        VECTOR("向量检索"),
        KEYWORD("关键词检索"),
        HYBRID("混合检索");

        private final String description;

        RetrievalSource(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 检索统计信息类
     */
    class RetrievalStats {
        private final int totalQueries;
        private final int vectorQueries;
        private final int keywordQueries;
        private final int hybridQueries;
        private final double averageResponseTime;
        private final double vectorSuccessRate;
        private final double keywordSuccessRate;
        private final double hybridSuccessRate;

        public RetrievalStats(int totalQueries, int vectorQueries, int keywordQueries, int hybridQueries,
                              double averageResponseTime, double vectorSuccessRate,
                              double keywordSuccessRate, double hybridSuccessRate) {
            this.totalQueries = totalQueries;
            this.vectorQueries = vectorQueries;
            this.keywordQueries = keywordQueries;
            this.hybridQueries = hybridQueries;
            this.averageResponseTime = averageResponseTime;
            this.vectorSuccessRate = vectorSuccessRate;
            this.keywordSuccessRate = keywordSuccessRate;
            this.hybridSuccessRate = hybridSuccessRate;
        }

        public int getTotalQueries() {
            return totalQueries;
        }

        public int getVectorQueries() {
            return vectorQueries;
        }

        public int getKeywordQueries() {
            return keywordQueries;
        }

        public int getHybridQueries() {
            return hybridQueries;
        }

        public double getAverageResponseTime() {
            return averageResponseTime;
        }

        public double getVectorSuccessRate() {
            return vectorSuccessRate;
        }

        public double getKeywordSuccessRate() {
            return keywordSuccessRate;
        }

        public double getHybridSuccessRate() {
            return hybridSuccessRate;
        }

        @Override
        public String toString() {
            return String.format(
                    "RetrievalStats{total=%d, vector=%d(%.1f%%), keyword=%d(%.1f%%), hybrid=%d(%.1f%%), avgTime=%.2fms}",
                    totalQueries,
                    vectorQueries, vectorSuccessRate * 100,
                    keywordQueries, keywordSuccessRate * 100,
                    hybridQueries, hybridSuccessRate * 100,
                    averageResponseTime
            );
        }
    }
}