package com.student.service.rag;

import com.student.config.SafetyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 医疗安全兜底服务
 * 提供三层安全防护：
 * 1. 急症关键词预检（检索前）
 * 2. 检索置信度门控（检索后、LLM前）
 * 3. 回答安全校验（LLM后）
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyGuardService {

    private final SafetyConfig safetyConfig;

    // 统计计数器
    private final AtomicInteger emergencyBlockCount = new AtomicInteger(0);
    private final AtomicInteger confidenceBlockCount = new AtomicInteger(0);
    private final AtomicInteger safetyCheckCount = new AtomicInteger(0);

    // 已编译的正则模式缓存
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    // ==================== ① 急症预检 ====================

    /**
     * 急症关键词检测
     * 在检索之前调用，一旦命中高危关键词直接阻断并返回就医建议
     *
     * @param query 用户原始查询
     * @return SafetyDecision：pass → 放行, block → 阻断
     */
    public SafetyDecision checkEmergency(String query) {
        if (!safetyConfig.isEnabled() || !safetyConfig.getEmergency().isEnabled()) {
            return SafetyDecision.pass();
        }
        if (query == null || query.trim().isEmpty()) {
            return SafetyDecision.pass();
        }

        Map<String, List<String>> keywords = safetyConfig.getEmergency().getKeywords();
        Map<String, String> templates = safetyConfig.getEmergency().getResponseTemplates();

        // 按严重程度从高到低遍历
        String[] levels = {"CRITICAL", "HIGH", "MEDIUM"};
        for (String level : levels) {
            List<String> levelKeywords = keywords.getOrDefault(level, List.of());
            String template = templates.getOrDefault(level, "建议尽快就医。");

            for (String keyword : levelKeywords) {
                if (query.contains(keyword)) {
                    log.warn("[安全兜底] 急症关键词命中: level={}, keyword='{}', query='{}'",
                            level, keyword, truncate(query, 80));
                    emergencyBlockCount.incrementAndGet();
                    return SafetyDecision.block(
                            "EMERGENCY_" + level,
                            template,
                            level
                    );
                }
            }
        }

        return SafetyDecision.pass();
    }

    // ==================== ② 置信度门控 ====================

    /**
     * 评估检索结果置信度
     * 在重排序之后、LLM生成之前调用
     *
     * @param results 重排序后的检索结果列表
     * @return ConfidenceDecision：pass / warning / block
     */
    public ConfidenceDecision assessConfidence(List<?> results) {
        if (!safetyConfig.isEnabled() || !safetyConfig.getConfidence().isEnabled()) {
            return ConfidenceDecision.pass(1.0);
        }
        if (results == null || results.isEmpty()) {
            log.warn("[安全兜底] 检索结果为空，阻断");
            confidenceBlockCount.incrementAndGet();
            return ConfidenceDecision.block(0.0, safetyConfig.getConfidence().getNoRetrievalResponse());
        }

        double maxScore = results.stream()
                .mapToDouble(r -> {
                    if (r instanceof MultiRetrievalService.RetrievalResult) {
                        Double score = ((MultiRetrievalService.RetrievalResult) r).getScore();
                        return score != null ? score : 0.0;
                    }
                    return 0.0;
                })
                .max()
                .orElse(0.0);

        double minThreshold = safetyConfig.getConfidence().getMinThreshold();
        double warningThreshold = safetyConfig.getConfidence().getWarningThreshold();

        if (maxScore < minThreshold) {
            log.warn("[安全兜底] 置信度过低: maxScore={}, threshold={}", maxScore, minThreshold);
            confidenceBlockCount.incrementAndGet();
            return ConfidenceDecision.block(maxScore, safetyConfig.getConfidence().getLowConfidenceResponse());
        } else if (maxScore < warningThreshold) {
            log.info("[安全兜底] 置信度偏低: maxScore={}, warningThreshold={}, 将添加免责声明",
                    maxScore, warningThreshold);
            return ConfidenceDecision.warning(maxScore, null);
        }

        return ConfidenceDecision.pass(maxScore);
    }

    // ==================== ③ 回答安全校验 ====================

    /**
     * 校验并净化 LLM 回答
     * 替换危险用药模式、根据置信度添加免责声明
     *
     * @param answer     LLM 原始回答
     * @param confidence 当前问答置信度
     * @return 净化后的安全回答
     */
    public String sanitizeAnswer(String answer, double confidence) {
        if (!safetyConfig.isEnabled() || !safetyConfig.getAnswerSafety().isEnabled()) {
            return answer;
        }
        if (answer == null || answer.trim().isEmpty()) {
            return answer;
        }

        safetyCheckCount.incrementAndGet();

        // 替换危险模式
        String sanitized = replaceBlockedPatterns(answer);

        // 在置信度偏低时添加免责声明
        double disclaimerMaxConf = safetyConfig.getAnswerSafety().getDisclaimerMaxConfidence();
        if (confidence < disclaimerMaxConf) {
            String disclaimer = safetyConfig.getAnswerSafety().getDisclaimer();
            if (!sanitized.endsWith(disclaimer)) {
                sanitized = sanitized + disclaimer;
            }
        }

        if (!sanitized.equals(answer)) {
            log.info("[安全兜底] 回答已净化: originalLength={}, sanitizedLength={}",
                    answer.length(), sanitized.length());
        }

        return sanitized;
    }

    /**
     * 替换回答中的危险模式
     */
    private String replaceBlockedPatterns(String answer) {
        List<String> patterns = safetyConfig.getAnswerSafety().getBlockedPatterns();
        String replacement = safetyConfig.getAnswerSafety().getReplacementText();

        for (String patternStr : patterns) {
            Pattern pattern = compiledPatterns.computeIfAbsent(patternStr, Pattern::compile);
            if (pattern.matcher(answer).find()) {
                answer = pattern.matcher(answer).replaceAll(replacement);
            }
        }
        return answer;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
