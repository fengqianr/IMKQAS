package com.student.service;

import java.util.List;

/**
 * 查询改写服务接口
 * 优化用户查询以提高检索效果，包括查询扩展、同义词替换、意图澄清等
 *
 * @author 系统
 * @version 1.0
 */
public interface QueryRewriteService {

    /**
     * 改写查询（综合方法）
     *
     * @param originalQuery 原始查询
     * @param userId 用户ID（用于个性化）
     * @param conversationId 对话ID（用于上下文）
     * @return 改写后的查询
     */
    String rewrite(String originalQuery, Long userId, Long conversationId);

    /**
     * 批量改写查询
     *
     * @param queries 原始查询列表
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @return 改写后的查询列表
     */
    List<String> rewriteBatch(List<String> queries, Long userId, Long conversationId);

    /**
     * 查询扩展（添加同义词、相关术语）
     *
     * @param query 原始查询
     * @return 扩展后的查询
     */
    String expand(String query);

    /**
     * 查询简化（移除停用词、标准化术语）
     *
     * @param query 原始查询
     * @return 简化后的查询
     */
    String simplify(String query);

    /**
     * 拼写纠正
     *
     * @param query 可能包含拼写错误的查询
     * @return 纠正后的查询
     */
    String correctSpelling(String query);

    /**
     * 添加医疗专业术语
     *
     * @param query 非专业查询
     * @return 专业术语化的查询
     */
    String medicalize(String query);

    /**
     * 获取查询的意图分类
     *
     * @param query 查询
     * @return 意图分类结果
     */
    IntentClassification classifyIntent(String query);

    /**
     * 检查服务是否可用
     *
     * @return 服务状态
     */
    boolean isAvailable();

    /**
     * 获取服务统计信息
     *
     * @return 统计信息
     */
    QueryRewriteStats getStats();

    // ========== 内部数据类型 ==========

    /**
     * 意图分类结果
     */
    class IntentClassification {
        private final String query;
        private final IntentType primaryIntent;
        private final List<IntentType> secondaryIntents;
        private final double confidence;

        public IntentClassification(String query, IntentType primaryIntent,
                                   List<IntentType> secondaryIntents, double confidence) {
            this.query = query;
            this.primaryIntent = primaryIntent;
            this.secondaryIntents = secondaryIntents;
            this.confidence = confidence;
        }

        public String getQuery() {
            return query;
        }

        public IntentType getPrimaryIntent() {
            return primaryIntent;
        }

        public List<IntentType> getSecondaryIntents() {
            return secondaryIntents;
        }

        public double getConfidence() {
            return confidence;
        }
    }

    /**
     * 医疗查询意图类型
     */
    enum IntentType {
        DISEASE_QUERY("疾病查询"),           // 疾病信息、症状、诊断
        DRUG_QUERY("药物查询"),             // 药物信息、用法、副作用
        SYMPTOM_QUERY("症状查询"),          // 症状分析、可能疾病
        DEPARTMENT_GUIDANCE("科室导诊"),    // 推荐就诊科室
        TREATMENT_QUERY("治疗查询"),        // 治疗方法、手术、康复
        PREVENTION_QUERY("预防查询"),       // 疾病预防、健康建议
        EXAMINATION_QUERY("检查查询"),      // 医学检查、化验
        EMERGENCY_QUERY("急诊查询"),        // 紧急情况处理
        GENERAL_HEALTH("一般健康"),         // 健康咨询、生活习惯
        OTHER("其他");                     // 其他类型

        private final String description;

        IntentType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 查询改写统计信息
     */
    class QueryRewriteStats {
        private final int totalQueries;
        private final int rewrittenQueries;
        private final int expandedQueries;
        private final int simplifiedQueries;
        private final int correctedQueries;
        private final double averageProcessingTime; // 毫秒

        public QueryRewriteStats(int totalQueries, int rewrittenQueries,
                                int expandedQueries, int simplifiedQueries,
                                int correctedQueries, double averageProcessingTime) {
            this.totalQueries = totalQueries;
            this.rewrittenQueries = rewrittenQueries;
            this.expandedQueries = expandedQueries;
            this.simplifiedQueries = simplifiedQueries;
            this.correctedQueries = correctedQueries;
            this.averageProcessingTime = averageProcessingTime;
        }

        public int getTotalQueries() {
            return totalQueries;
        }

        public int getRewrittenQueries() {
            return rewrittenQueries;
        }

        public int getExpandedQueries() {
            return expandedQueries;
        }

        public int getSimplifiedQueries() {
            return simplifiedQueries;
        }

        public int getCorrectedQueries() {
            return correctedQueries;
        }

        public double getAverageProcessingTime() {
            return averageProcessingTime;
        }
    }
}