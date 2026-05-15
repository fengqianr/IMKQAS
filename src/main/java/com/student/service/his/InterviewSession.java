package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
}
