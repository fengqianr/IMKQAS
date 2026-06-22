package com.student.service.evaluation.impl;

import com.student.config.EvaluationConfig;
import com.student.service.evaluation.GenerationEvaluator;
import com.student.service.LlmService;
import com.student.utils.evaluation.MetricsCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成质量评估器实现
 * 使用LLM-as-Judge评估忠实度、答案相关性、上下文相关性
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GenerationEvaluatorImpl implements GenerationEvaluator {

    private final LlmService llmService;
    private final EvaluationConfig evaluationConfig;

    @Override
    public double evaluateFaithfulness(String query, String answer, List<String> context) {
        if (!evaluationConfig.getOffline().getLlmJudge().isEnabled()) {
            return heuristicFaithfulness(answer, context);
        }
        if (answer == null || answer.isEmpty()) {
            return 0.0;
        }
        String prompt = buildFaithfulnessPrompt(query, answer, context);
        String judgeResult = llmService.generateAnswerDirect(prompt);
        return parseScore(judgeResult);
    }

    @Override
    public double evaluateAnswerRelevance(String query, String answer) {
        if (!evaluationConfig.getOffline().getLlmJudge().isEnabled()) {
            return heuristicAnswerRelevance(query, answer);
        }
        if (answer == null || answer.isEmpty()) {
            return 0.0;
        }
        String prompt = buildAnswerRelevancePrompt(query, answer);
        String judgeResult = llmService.generateAnswerDirect(prompt);
        return parseScore(judgeResult);
    }

    @Override
    public double evaluateContextRelevance(String query, List<String> context) {
        if (!evaluationConfig.getOffline().getLlmJudge().isEnabled()) {
            return heuristicContextRelevance(query, context);
        }
        if (context == null || context.isEmpty()) {
            return 0.0;
        }
        String prompt = buildContextRelevancePrompt(query, context);
        String judgeResult = llmService.generateAnswerDirect(prompt);
        return parseScore(judgeResult);
    }

    @Override
    public GenerationEvalResult evaluateBatch(
            List<String> queries, List<String> answers, List<List<String>> contexts) {

        GenerationEvalResult result = new GenerationEvalResult();
        result.evaluatedQueries = queries.size();
        result.faithfulnessScores = new ArrayList<>();
        result.answerRelevanceScores = new ArrayList<>();
        result.contextRelevanceScores = new ArrayList<>();

        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            String answer = i < answers.size() ? answers.get(i) : "";
            List<String> context = i < contexts.size() ? contexts.get(i) : List.of();

            double faith = evaluateFaithfulness(query, answer, context);
            double relevance = evaluateAnswerRelevance(query, answer);
            double ctxRel = evaluateContextRelevance(query, context);

            result.faithfulnessScores.add(faith);
            result.answerRelevanceScores.add(relevance);
            result.contextRelevanceScores.add(ctxRel);
        }

        result.avgFaithfulness = MetricsCalculator.average(result.faithfulnessScores);
        result.avgAnswerRelevance = MetricsCalculator.average(result.answerRelevanceScores);
        result.avgContextRelevance = MetricsCalculator.average(result.contextRelevanceScores);

        log.info("生成质量评估完成: faithfulness={:.3f}, answerRelevance={:.3f}, contextRelevance={:.3f}",
                result.avgFaithfulness, result.avgAnswerRelevance, result.avgContextRelevance);

        return result;
    }

    // ==================== LLM-as-Judge Prompt构建 ====================

    private String buildFaithfulnessPrompt(String query, String answer, List<String> context) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < Math.min(context.size(), 5); i++) {
            ctx.append("[").append(i + 1).append("] ").append(context.get(i)).append("\n");
        }

        return String.format("""
                你是一个医疗回答质量评估专家。请判断以下回答是否忠实于给定的参考知识片段。

                【用户问题】
                %s

                【参考知识片段】
                %s

                【待评估回答】
                %s

                请仅输出一个0到1之间的分数（保留2位小数），表示回答的忠实度：
                - 0.0：完全编造，与知识片段毫无关系
                - 0.5：部分基于知识片段，但有明显编造成分
                - 1.0：完全基于知识片段，没有编造内容

                分数：""", query, ctx.toString(), answer);
    }

    private String buildAnswerRelevancePrompt(String query, String answer) {
        return String.format("""
                你是一个医疗回答质量评估专家。请判断以下回答是否与用户问题直接相关。

                【用户问题】
                %s

                【待评估回答】
                %s

                请仅输出一个0到1之间的分数（保留2位小数），表示回答的相关性：
                - 0.0：完全离题，与问题无关
                - 0.5：部分相关，但偏离了问题核心
                - 1.0：完全切题，直接回答了用户的问题

                分数：""", query, answer);
    }

    private String buildContextRelevancePrompt(String query, List<String> context) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < Math.min(context.size(), 5); i++) {
            ctx.append("[").append(i + 1).append("] ").append(context.get(i)).append("\n");
        }

        return String.format("""
                你是一个医疗检索质量评估专家。请判断以下检索到的知识片段是否与用户问题相关。

                【用户问题】
                %s

                【检索到的知识片段】
                %s

                请仅输出一个0到1之间的分数（保留2位小数），表示这些片段与问题的整体相关性：
                - 0.0：完全无关，检索结果毫无价值
                - 0.5：部分相关，有些有用有些无用
                - 1.0：高度相关，所有片段都与问题密切相关

                分数：""", query, ctx.toString());
    }

    // ==================== 分数解析 ====================

    private double parseScore(String judgeResult) {
        if (judgeResult == null || judgeResult.isEmpty()) {
            return 0.5;
        }
        try {
            // 提取数字
            String cleaned = judgeResult.replaceAll("[^0-9.]", "").trim();
            if (cleaned.isEmpty()) {
                return 0.5;
            }
            double score = Double.parseDouble(cleaned);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (NumberFormatException e) {
            // 启发式回退
            String lower = judgeResult.toLowerCase();
            if (lower.contains("高") || lower.contains("high") || lower.contains("忠实") || lower.contains("相关")) {
                return 0.8;
            }
            if (lower.contains("中等") || lower.contains("medium") || lower.contains("部分")) {
                return 0.5;
            }
            if (lower.contains("低") || lower.contains("low") || lower.contains("不") || lower.contains("编造")) {
                return 0.3;
            }
            return 0.5;
        }
    }

    // ==================== 启发式回退（LLM不可用时） ====================

    private double heuristicFaithfulness(String answer, List<String> context) {
        if (answer == null || answer.isEmpty() || context == null || context.isEmpty()) {
            return 0.0;
        }
        // 简单关键词共现比例
        int totalWords = answer.length();
        int matchedChars = 0;
        for (String ctx : context) {
            for (int i = 0; i < ctx.length() - 2; i++) {
                if (answer.contains(ctx.substring(i, Math.min(i + 3, ctx.length())))) {
                    matchedChars++;
                }
            }
        }
        return Math.min(1.0, (double) matchedChars / Math.max(1, totalWords));
    }

    private double heuristicAnswerRelevance(String query, String answer) {
        if (answer == null || answer.isEmpty()) {
            return 0.0;
        }
        // 基于回答长度的启发式
        if (answer.contains("无法回答") || answer.contains("不知道") || answer.contains("没有相关信息")) {
            return 0.3;
        }
        if (answer.length() < 50) {
            return 0.5;
        }
        if (answer.length() > 200) {
            return 0.8;
        }
        return 0.6;
    }

    private double heuristicContextRelevance(String query, List<String> context) {
        if (context == null || context.isEmpty()) {
            return 0.0;
        }
        // 基于context数量和长度的启发式
        int totalLen = context.stream().mapToInt(String::length).sum();
        if (totalLen < 100) {
            return 0.3;
        }
        if (totalLen > 1000) {
            return 0.8;
        }
        return 0.6;
    }
}
