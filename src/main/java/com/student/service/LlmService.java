package com.student.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 大语言模型服务接口
 * 负责与LLM API交互，生成医疗问答回答，支持流式输出和Prompt工程
 *
 * @author 系统
 * @version 1.0
 */
public interface LlmService {

    /**
     * 生成单个回答（同步）
     *
     * @param query 用户查询
     * @param context 检索到的上下文文档
     * @return 生成的回答
     */
    String generateAnswer(String query, List<String> context);

    /**
     * 生成单个回答（异步）
     *
     * @param query 用户查询
     * @param context 检索到的上下文文档
     * @return 异步任务，返回生成的回答
     */
    CompletableFuture<String> generateAnswerAsync(String query, List<String> context);

    /**
     * 流式生成回答（用于实时显示）
     *
     * @param query 用户查询
     * @param context 检索到的上下文文档
     * @return 流式响应，每生成一个token就返回
     */
    // Stream<String> generateAnswerStream(String query, List<String> context);

    /**
     * 批量生成回答
     *
     * @param queries 查询列表
     * @param contextsList 上下文列表（每个查询对应一个上下文列表）
     * @return 回答列表
     */
    List<String> generateAnswersBatch(List<String> queries, List<List<String>> contextsList);

    /**
     * 生成回答并包含引用来源
     *
     * @param query 用户查询
     * @param contextWithSources 带来源的上下文文档（文档内容 + 来源信息）
     * @return 带引用标注的回答
     */
    AnswerWithCitations generateAnswerWithCitations(String query, List<ContextWithSource> contextWithSources);

    /**
     * 获取模型信息
     *
     * @return 模型名称和配置信息
     */
    ModelInfo getModelInfo();

    /**
     * 检查服务是否可用
     *
     * @return 服务状态
     */
    boolean isAvailable();

    /**
     * 获取服务统计信息
     *
     * @return 统计信息（调用次数、成功率、平均响应时间等）
     */
    LlmStats getStats();

    // ========== 内部数据类型 ==========

    /**
     * 带来源的上下文文档
     */
    class ContextWithSource {
        private final String content;
        private final String source; // 文档ID、标题、URL等
        private final double relevanceScore;

        public ContextWithSource(String content, String source, double relevanceScore) {
            this.content = content;
            this.source = source;
            this.relevanceScore = relevanceScore;
        }

        public String getContent() {
            return content;
        }

        public String getSource() {
            return source;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }
    }

    /**
     * 带引用标注的回答
     */
    class AnswerWithCitations {
        private final String answer;
        private final List<Citation> citations;

        public AnswerWithCitations(String answer, List<Citation> citations) {
            this.answer = answer;
            this.citations = citations;
        }

        public String getAnswer() {
            return answer;
        }

        public List<Citation> getCitations() {
            return citations;
        }
    }

    /**
     * 引用信息
     */
    class Citation {
        private final String source;
        private final int position; // 在回答中的位置（字符索引）
        private final String quote; // 引用的原文片段

        public Citation(String source, int position, String quote) {
            this.source = source;
            this.position = position;
            this.quote = quote;
        }

        public String getSource() {
            return source;
        }

        public int getPosition() {
            return position;
        }

        public String getQuote() {
            return quote;
        }
    }

    /**
     * 模型信息
     */
    class ModelInfo {
        private final String name;
        private final String provider;
        private final int maxTokens;
        private final boolean supportsStreaming;

        public ModelInfo(String name, String provider, int maxTokens, boolean supportsStreaming) {
            this.name = name;
            this.provider = provider;
            this.maxTokens = maxTokens;
            this.supportsStreaming = supportsStreaming;
        }

        public String getName() {
            return name;
        }

        public String getProvider() {
            return provider;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public boolean isSupportsStreaming() {
            return supportsStreaming;
        }
    }

    /**
     * LLM服务统计信息
     */
    class LlmStats {
        private final int totalCalls;
        private final int successfulCalls;
        private final int failedCalls;
        private final double averageResponseTime; // 毫秒
        private final double successRate;
        private final int totalTokensGenerated;
        private final int totalTokensConsumed;

        public LlmStats(int totalCalls, int successfulCalls, int failedCalls,
                       double averageResponseTime, double successRate,
                       int totalTokensGenerated, int totalTokensConsumed) {
            this.totalCalls = totalCalls;
            this.successfulCalls = successfulCalls;
            this.failedCalls = failedCalls;
            this.averageResponseTime = averageResponseTime;
            this.successRate = successRate;
            this.totalTokensGenerated = totalTokensGenerated;
            this.totalTokensConsumed = totalTokensConsumed;
        }

        public int getTotalCalls() {
            return totalCalls;
        }

        public int getSuccessfulCalls() {
            return successfulCalls;
        }

        public int getFailedCalls() {
            return failedCalls;
        }

        public double getAverageResponseTime() {
            return averageResponseTime;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public int getTotalTokensGenerated() {
            return totalTokensGenerated;
        }

        public int getTotalTokensConsumed() {
            return totalTokensConsumed;
        }
    }
}