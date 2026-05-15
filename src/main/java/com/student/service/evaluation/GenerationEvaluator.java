package com.student.service.evaluation;

import java.util.List;

/**
 * 生成质量评估器接口
 * 使用LLM-as-Judge评估Faithfulness、Answer Relevance、Context Relevance
 *
 * @author 系统
 * @version 1.0
 */
public interface GenerationEvaluator {

    /**
     * 生成评估结果
     */
    class GenerationEvalResult {
        /** 平均忠实度分数 */
        public double avgFaithfulness;
        /** 平均答案相关性分数 */
        public double avgAnswerRelevance;
        /** 平均上下文相关性分数 */
        public double avgContextRelevance;
        /** 各查询的忠实度分数列表 */
        public List<Double> faithfulnessScores;
        /** 各查询的答案相关性分数列表 */
        public List<Double> answerRelevanceScores;
        /** 各查询的上下文相关性分数列表 */
        public List<Double> contextRelevanceScores;
        /** 评估查询数 */
        public int evaluatedQueries;
    }

    /**
     * 评估单个回答的忠实度
     *
     * @param query   用户查询
     * @param answer  LLM生成的回答
     * @param context 检索到的上下文片段（用于验证）
     * @return 忠实度分数 [0, 1]
     */
    double evaluateFaithfulness(String query, String answer, List<String> context);

    /**
     * 评估单个回答的相关性
     *
     * @param query  用户查询
     * @param answer LLM生成的回答
     * @return 答案相关性分数 [0, 1]
     */
    double evaluateAnswerRelevance(String query, String answer);

    /**
     * 评估上下文相关性
     *
     * @param query   用户查询
     * @param context 检索到的上下文片段
     * @return 上下文相关性分数 [0, 1]
     */
    double evaluateContextRelevance(String query, List<String> context);

    /**
     * 批量评估生成质量
     *
     * @param queries  查询列表
     * @param answers  回答列表（一一对应）
     * @param contexts 上下文列表（一一对应）
     * @return 生成评估结果
     */
    GenerationEvalResult evaluateBatch(
            List<String> queries,
            List<String> answers,
            List<List<String>> contexts);
}
