package com.student.service.his;

/**
 * 对话状态管理器接口
 * 管理 CHAT ↔ QUESTIONNAIRE 状态切换
 *
 * @author 系统
 * @version 1.0
 */
public interface ConversationStateManager {

    /**
     * 获取当前对话状态
     */
    ConversationState getState(Long conversationId);

    /**
     * 执行状态转换
     *
     * @return 转换后的状态
     */
    ConversationState transition(Long conversationId, ConversationState targetState);

    /**
     * 检查是否存在未完成的问卷会话
     */
    boolean hasPendingInterview(Long conversationId);

    /**
     * 获取未完成问卷的会话ID
     */
    String getPendingInterviewSessionId(Long conversationId);

    /**
     * 清除对话状态（问卷完成/取消时）
     */
    void clear(Long conversationId);
}
