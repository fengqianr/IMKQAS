package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 填表会话状态
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    /** 会话唯一ID */
    private String sessionId;

    /** 问卷标识 */
    private String questionnaireId;

    /** 问卷标题 */
    private String questionnaireTitle;

    /** 用户ID */
    private Long userId;

    /** 对话ID */
    private Long conversationId;

    /** 当前题号（从0开始） */
    private int currentQuestionIndex;

    /** 总题数 */
    private int totalQuestions;

    /** 已收集的答案: linkId -> answerCode */
    @Builder.Default
    private Map<String, String> answers = new LinkedHashMap<>();

    /** 当前总分 */
    private int currentScore;

    /** 是否已完成 */
    private boolean completed;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后活动时间 */
    private LocalDateTime lastActiveAt;

    /** 采集模式：llm_driven / manual_form */
    @Builder.Default
    private String collectionMode = "manual_form";

    /** 用户原始表述: linkId -> 用户原始输入文本 */
    @Builder.Default
    private Map<String, String> rawInputs = new LinkedHashMap<>();

    /** 已触发的安全标记列表 */
    @Builder.Default
    private List<String> safetyFlags = new java.util.ArrayList<>();

    /** 增量上下文摘要（每完成一题后追加） */
    private String contextSummary;

    /** 当前降级层级：llm / rule_parser / manual_form */
    @Builder.Default
    private String degradationLevel = "llm";

    /** 连续LLM失败次数（断路器计数） */
    @Builder.Default
    private int consecutiveFailures = 0;

    /** 溯源信息: linkId -> 来源+置信度 */
    @Builder.Default
    private Map<String, Provenance> provenance = new LinkedHashMap<>();

    /**
     * 是否已超时
     */
    public boolean isExpired(int timeoutMinutes) {
        return lastActiveAt != null
                && lastActiveAt.plusMinutes(timeoutMinutes).isBefore(LocalDateTime.now());
    }

    /**
     * 获取进度百分比
     */
    public int getProgressPercent() {
        if (totalQuestions == 0) return 0;
        return answers.size() * 100 / totalQuestions;
    }

    /**
     * 溯源信息——记录每个答案的提取来源和置信度
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Provenance {
        /** 来源：llm / rule_parser / direct_input / fallback */
        private String source;
        /** 置信度 0.0-1.0 */
        private double confidence;
    }
}
