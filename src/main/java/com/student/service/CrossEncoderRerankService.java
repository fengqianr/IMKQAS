package com.student.service;

import java.util.List;

/**
 * 交叉编码器重排序服务接口
 * 集成预训练交叉编码器模型（如阿里云gte-rerank）对检索结果进行语义重排序
 *
 * @author 系统
 * @version 1.0
 */
public interface CrossEncoderRerankService {

    /**
     * 对检索结果进行重排序
     *
     * @param query 查询文本
     * @param retrievalResults 检索结果列表
     * @param topK 返回结果数量
     * @return 重排序后的结果列表，按相关性分数降序排列
     */
    List<MultiRetrievalService.RetrievalResult> rerank(
            String query,
            List<MultiRetrievalService.RetrievalResult> retrievalResults,
            int topK
    );

    /**
     * 对检索结果进行重排序（使用配置中的默认topK）
     *
     * @param query 查询文本
     * @param retrievalResults 检索结果列表
     * @return 重排序后的结果列表
     */
    List<MultiRetrievalService.RetrievalResult> rerank(
            String query,
            List<MultiRetrievalService.RetrievalResult> retrievalResults
    );

    /**
     * 批量重排序（高效处理多个查询）
     *
     * @param queries 查询文本列表
     * @param retrievalResultsList 对应的检索结果列表列表
     * @param topK 每个查询返回结果数量
     * @return 重排序后的结果列表列表
     */
    List<List<MultiRetrievalService.RetrievalResult>> batchRerank(
            List<String> queries,
            List<List<MultiRetrievalService.RetrievalResult>> retrievalResultsList,
            int topK
    );

    /**
     * 获取默认重排序topK
     *
     * @return 默认的重排序结果数量
     */
    int getDefaultTopK();

    /**
     * 检查重排序服务是否可用
     *
     * @return 服务是否可用
     */
    boolean isAvailable();

    /**
     * 获取重排序统计信息
     *
     * @return 统计信息
     */
    RerankStats getStats();

    /**
     * 重排序统计信息类
     */
    class RerankStats {
        private final int totalReranks;
        private final int totalInputResults;
        private final int totalOutputResults;
        private final double averageProcessingTimeMs;
        private final double successRate;

        public RerankStats(int totalReranks, int totalInputResults, int totalOutputResults,
                          double averageProcessingTimeMs, double successRate) {
            this.totalReranks = totalReranks;
            this.totalInputResults = totalInputResults;
            this.totalOutputResults = totalOutputResults;
            this.averageProcessingTimeMs = averageProcessingTimeMs;
            this.successRate = successRate;
        }

        public int getTotalReranks() {
            return totalReranks;
        }

        public int getTotalInputResults() {
            return totalInputResults;
        }

        public int getTotalOutputResults() {
            return totalOutputResults;
        }

        public double getAverageProcessingTimeMs() {
            return averageProcessingTimeMs;
        }

        public double getSuccessRate() {
            return successRate;
        }

        @Override
        public String toString() {
            return String.format(
                    "RerankStats{total=%d, input=%d, output=%d, avgTime=%.2fms, success=%.2f%%}",
                    totalReranks, totalInputResults, totalOutputResults,
                    averageProcessingTimeMs, successRate * 100
            );
        }
    }
}