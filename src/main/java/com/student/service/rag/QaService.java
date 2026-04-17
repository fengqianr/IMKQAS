package com.student.service.rag;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 问答服务接口
 * 整合检索、重排序和LLM生成，实现完整的RAG问答流程
 *
 * @author 系统
 * @version 1.0
 */
public interface QaService {

    /**
     * 问答流程：检索 -> 重排序 -> 生成回答
     *
     * @param query 用户查询
     * @param userId 用户ID（用于个性化）
     * @param conversationId 对话ID（用于上下文）
     * @return 生成的回答
     */
    QaResponse answer(String query, Long userId, Long conversationId);

    /**
     * 异步问答
     *
     * @param query 用户查询
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @return 异步任务，返回问答响应
     */
    CompletableFuture<QaResponse> answerAsync(String query, Long userId, Long conversationId);

    /**
     * 批量问答
     *
     * @param queries 查询列表
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @return 问答响应列表
     */
    List<QaResponse> answerBatch(List<String> queries, Long userId, Long conversationId);

    /**
     * 带来源的问答（返回引用信息）
     *
     * @param query 用户查询
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @return 带引用标注的问答响应
     */
    QaResponseWithSources answerWithSources(String query, Long userId, Long conversationId);

    /**
     * 获取问答统计信息
     *
     * @return 统计信息
     */
    QaStats getStats();

    /**
     * 检查服务是否可用
     *
     * @return 服务状态
     */
    boolean isAvailable();

    // ========== 内部数据类型 ==========

    /**
     * 问答响应
     */
    class QaResponse {
        private final String query;
        private final String answer;
        private final List<String> retrievedContext; // 检索到的上下文（摘要）
        private final double confidence; // 置信度（0-1）
        private final long processingTime; // 处理时间（毫秒）
        private final String modelUsed; // 使用的LLM模型

        public QaResponse(String query, String answer, List<String> retrievedContext,
                         double confidence, long processingTime, String modelUsed) {
            this.query = query;
            this.answer = answer;
            this.retrievedContext = retrievedContext;
            this.confidence = confidence;
            this.processingTime = processingTime;
            this.modelUsed = modelUsed;
        }

        public String getQuery() {
            return query;
        }

        public String getAnswer() {
            return answer;
        }

        public List<String> getRetrievedContext() {
            return retrievedContext;
        }

        public double getConfidence() {
            return confidence;
        }

        public long getProcessingTime() {
            return processingTime;
        }

        public String getModelUsed() {
            return modelUsed;
        }
    }

    /**
     * 带来源的问答响应
     */
    class QaResponseWithSources extends QaResponse {
        private final List<SourceCitation> citations;

        public QaResponseWithSources(String query, String answer, List<String> retrievedContext,
                                    double confidence, long processingTime, String modelUsed,
                                    List<SourceCitation> citations) {
            super(query, answer, retrievedContext, confidence, processingTime, modelUsed);
            this.citations = citations;
        }

        public List<SourceCitation> getCitations() {
            return citations;
        }
    }

    /**
     * 来源引用
     */
    class SourceCitation {
        private final String documentId;
        private final String chunkId;
        private final String title;
        private final String snippet; // 引用的原文片段
        private final double relevanceScore; // 相关性分数
        private final int positionInAnswer; // 在回答中的位置

        public SourceCitation(String documentId, String chunkId, String title,
                             String snippet, double relevanceScore, int positionInAnswer) {
            this.documentId = documentId;
            this.chunkId = chunkId;
            this.title = title;
            this.snippet = snippet;
            this.relevanceScore = relevanceScore;
            this.positionInAnswer = positionInAnswer;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getChunkId() {
            return chunkId;
        }

        public String getTitle() {
            return title;
        }

        public String getSnippet() {
            return snippet;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }

        public int getPositionInAnswer() {
            return positionInAnswer;
        }
    }

    /**
     * 问答统计信息
     */
    class QaStats {
        private final int totalQueries;
        private final int successfulQueries;
        private final int failedQueries;
        private final double averageProcessingTime; // 毫秒
        private final double successRate;
        private final int totalRetrievedDocuments;
        private final int totalGeneratedTokens;

        public QaStats(int totalQueries, int successfulQueries, int failedQueries,
                      double averageProcessingTime, double successRate,
                      int totalRetrievedDocuments, int totalGeneratedTokens) {
            this.totalQueries = totalQueries;
            this.successfulQueries = successfulQueries;
            this.failedQueries = failedQueries;
            this.averageProcessingTime = averageProcessingTime;
            this.successRate = successRate;
            this.totalRetrievedDocuments = totalRetrievedDocuments;
            this.totalGeneratedTokens = totalGeneratedTokens;
        }

        public int getTotalQueries() {
            return totalQueries;
        }

        public int getSuccessfulQueries() {
            return successfulQueries;
        }

        public int getFailedQueries() {
            return failedQueries;
        }

        public double getAverageProcessingTime() {
            return averageProcessingTime;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public int getTotalRetrievedDocuments() {
            return totalRetrievedDocuments;
        }

        public int getTotalGeneratedTokens() {
            return totalGeneratedTokens;
        }
    }
}