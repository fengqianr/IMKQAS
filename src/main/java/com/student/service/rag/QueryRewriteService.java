package com.student.service.rag;

import java.util.List;

/**
 * 查询改写服务接口
 * 优化用户查询以提高检索效果，包括拼写纠正、停用词简化、实体识别、同义词扩展、医学术语化
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

    /**
     * 查询归一化（新增接口）
     * 完整的归一化流程：拼写纠正 → 简化 → 术语化
     * 供LLM服务生成缓存键使用
     *
     * @param query 原始查询
     * @return 归一化后的查询
     */
    default String normalize(String query) {
        // 默认实现：依次调用现有方法
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        try {
            String corrected = correctSpelling(query);
            String simplified = simplify(corrected);
            String medicalized = medicalize(simplified);
            return medicalized;
        } catch (Exception e) {
            // 降级：返回原始查询
            return query;
        }
    }

    // ========== 内部数据类型 ==========

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