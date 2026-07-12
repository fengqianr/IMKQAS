package com.student.service.his;

/**
 * 访谈状态枚举
 *
 * @author 系统
 * @version 2.0
 */
public enum InterviewStatus {

    // ==================== 会话生命周期状态 ====================

    /** 旧版"进行中"，保留兼容旧数据，新代码不使用 */
    ACTIVE,

    /** 暂停（关页面/刷新），可恢复 */
    PAUSED,

    /** 超时过期，不可恢复 */
    EXPIRED,

    /** 用户主动放弃，已清理 */
    ABANDONED,

    // ==================== 题目级操作状态（握手协议） ====================

    /** 正在提问，等待用户回答 */
    QUESTIONING,

    /** 收到用户回答，正在调用LLM提取编码 */
    ANSWER_RECEIVED,

    /** LLM需要追问，等待用户补充说明 */
    CLARIFYING,

    /** LLM已返回编码，状态机正在校验合法性 */
    VALIDATING,

    /** 全部题目采集完毕 */
    COMPLETED;

    // ==================== 辅助方法 ====================

    /**
     * 判断是否为题目级操作状态（即会话正在进行中）
     */
    public static boolean isQuestionLevelState(String status) {
        return "QUESTIONING".equals(status)
                || "ANSWER_RECEIVED".equals(status)
                || "CLARIFYING".equals(status)
                || "VALIDATING".equals(status);
    }

    /**
     * 判断是否为活跃状态（可接受用户输入）
     */
    public static boolean isActiveState(String status) {
        return isQuestionLevelState(status) || "ACTIVE".equals(status);
    }
}
