package com.student.service.rag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.student.dto.qa.RetrievalPathDto;
import com.student.service.his.InterviewSuggestion;
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
    static class QaResponse {
        private final String query;
        private final String answer;
        private final List<String> retrievedContext; // 检索到的上下文（摘要）
        private final double confidence; // 置信度（0-1）
        private final long processingTime; // 处理时间（毫秒）
        private final String modelUsed; // 使用的LLM模型
        private final String intentType; // 意图分类结果
        private final InterviewSuggestion questionnaireSuggestion; // 问卷建议（DATA_COLLECTION/MIXED时）

        @JsonCreator
        public QaResponse(@JsonProperty("query") String query,
                         @JsonProperty("answer") String answer,
                         @JsonProperty("retrievedContext") List<String> retrievedContext,
                         @JsonProperty("confidence") double confidence,
                         @JsonProperty("processingTime") long processingTime,
                         @JsonProperty("modelUsed") String modelUsed,
                         @JsonProperty("intentType") String intentType,
                         @JsonProperty("questionnaireSuggestion") InterviewSuggestion questionnaireSuggestion) {
            this.query = query;
            this.answer = answer;
            this.retrievedContext = retrievedContext;
            this.confidence = confidence;
            this.processingTime = processingTime;
            this.modelUsed = modelUsed;
            this.intentType = intentType;
            this.questionnaireSuggestion = questionnaireSuggestion;
        }

        /** 兼容旧调用方（不包含意图路由） */
        public QaResponse(String query, String answer, List<String> retrievedContext,
                         double confidence, long processingTime, String modelUsed) {
            this(query, answer, retrievedContext, confidence, processingTime, modelUsed, null, null);
        }

        public String getQuery() { return query; }
        public String getAnswer() { return answer; }
        public List<String> getRetrievedContext() { return retrievedContext; }
        public double getConfidence() { return confidence; }
        public long getProcessingTime() { return processingTime; }
        public String getModelUsed() { return modelUsed; }
        public String getIntentType() { return intentType; }
        public InterviewSuggestion getQuestionnaireSuggestion() { return questionnaireSuggestion; }
    }

    /**
     * 带来源的问答响应
     */
    static class QaResponseWithSources extends QaResponse {
        private final List<SourceCitation> citations;
        private final RetrievalPathDto retrievalPath;

        @JsonCreator
        public QaResponseWithSources(@JsonProperty("query") String query,
                                    @JsonProperty("answer") String answer,
                                    @JsonProperty("retrievedContext") List<String> retrievedContext,
                                    @JsonProperty("confidence") double confidence,
                                    @JsonProperty("processingTime") long processingTime,
                                    @JsonProperty("modelUsed") String modelUsed,
                                    @JsonProperty("citations") List<SourceCitation> citations,
                                    @JsonProperty("intentType") String intentType,
                                    @JsonProperty("questionnaireSuggestion") InterviewSuggestion questionnaireSuggestion,
                                    @JsonProperty("retrievalPath") RetrievalPathDto retrievalPath) {
            super(query, answer, retrievedContext, confidence, processingTime, modelUsed, intentType, questionnaireSuggestion);
            this.citations = citations;
            this.retrievalPath = retrievalPath;
        }

        /** 兼容旧调用方（无intent/retrievalPath） */
        public QaResponseWithSources(String query, String answer, List<String> retrievedContext,
                                    double confidence, long processingTime, String modelUsed,
                                    List<SourceCitation> citations) {
            this(query, answer, retrievedContext, confidence, processingTime, modelUsed, citations, null, null, null);
        }

        /** 兼容旧调用方（有intent，无retrievalPath） */
        public QaResponseWithSources(String query, String answer, List<String> retrievedContext,
                                    double confidence, long processingTime, String modelUsed,
                                    List<SourceCitation> citations,
                                    String intentType, InterviewSuggestion questionnaireSuggestion) {
            this(query, answer, retrievedContext, confidence, processingTime, modelUsed, citations, intentType, questionnaireSuggestion, null);
        }

        public List<SourceCitation> getCitations() {
            return citations;
        }

        public RetrievalPathDto getRetrievalPath() {
            return retrievalPath;
        }
    }

    /**
     * 来源引用
     */
    static class SourceCitation {
        private final String documentId;
        private final String chunkId;
        private final String title;
        private final String snippet; // 引用的原文片段
        private final double relevanceScore; // 相关性分数
        private final int positionInAnswer; // 在回答中的位置

        @JsonCreator
        public SourceCitation(@JsonProperty("documentId") String documentId,
                             @JsonProperty("chunkId") String chunkId,
                             @JsonProperty("title") String title,
                             @JsonProperty("snippet") String snippet,
                             @JsonProperty("relevanceScore") double relevanceScore,
                             @JsonProperty("positionInAnswer") int positionInAnswer) {
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
    static class QaStats {
        private final int totalQueries;
        private final int successfulQueries;
        private final int failedQueries;
        private final double averageProcessingTime; // 毫秒
        private final double successRate;
        private final int totalRetrievedDocuments;
        private final int totalGeneratedTokens;

        @JsonCreator
        public QaStats(@JsonProperty("totalQueries") int totalQueries,
                      @JsonProperty("successfulQueries") int successfulQueries,
                      @JsonProperty("failedQueries") int failedQueries,
                      @JsonProperty("averageProcessingTime") double averageProcessingTime,
                      @JsonProperty("successRate") double successRate,
                      @JsonProperty("totalRetrievedDocuments") int totalRetrievedDocuments,
                      @JsonProperty("totalGeneratedTokens") int totalGeneratedTokens) {
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