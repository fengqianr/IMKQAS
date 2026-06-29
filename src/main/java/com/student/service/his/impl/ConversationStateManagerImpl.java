package com.student.service.his.impl;

import com.student.service.RedisService;
import com.student.service.his.ConversationState;
import com.student.service.his.ConversationStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对话状态管理器实现
 * 状态存储在Redis，支持分布式 + 自动过期
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationStateManagerImpl implements ConversationStateManager {

    private final RedisService redisService;

    private static final String KEY_PREFIX = "his:state:";
    private static final String INTERVIEW_PREFIX = "his:interview:";
    private static final int STATE_TTL_SECONDS = 1800; // 30分钟

    @Override
    public ConversationState getState(Long conversationId) {
        String key = KEY_PREFIX + conversationId;
        Object value = redisService.get(key);
        if (value == null) return ConversationState.CHAT;
        try {
            return ConversationState.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            return ConversationState.CHAT;
        }
    }

    @Override
    public ConversationState transition(Long conversationId, ConversationState targetState) {
        String key = KEY_PREFIX + conversationId;
        try {
            redisService.set(key, targetState.name(), (long) STATE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("对话状态切换Redis写入失败: conversationId={}, targetState={}, error={}",
                    conversationId, targetState, e.getMessage());
        }
        log.info("对话状态切换: conversationId={}, targetState={}", conversationId, targetState);
        return targetState;
    }

    @Override
    public boolean hasPendingInterview(Long conversationId) {
        String sessionId = getPendingInterviewSessionId(conversationId);
        if (sessionId == null) return false;
        String sessionKey = INTERVIEW_PREFIX + sessionId;
        Object state = redisService.get(sessionKey);
        return state != null;
    }

    @Override
    public String getPendingInterviewSessionId(Long conversationId) {
        String pendingKey = KEY_PREFIX + conversationId + ":pending_interview";
        Object value = redisService.get(pendingKey);
        return value != null ? value.toString() : null;
    }

    /**
     * 设置待恢复的问卷会话ID（Redis失败不阻塞主流程）
     */
    public void setPendingInterviewSessionId(Long conversationId, String sessionId) {
        String pendingKey = KEY_PREFIX + conversationId + ":pending_interview";
        try {
            redisService.set(pendingKey, sessionId, (long) STATE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("设置pending interview session id失败(Redis不可用): conversationId={}, sessionId={}, error={}",
                    conversationId, sessionId, e.getMessage());
        }
    }

    @Override
    public void clear(Long conversationId) {
        String stateKey = KEY_PREFIX + conversationId;
        String pendingKey = KEY_PREFIX + conversationId + ":pending_interview";
        redisService.delete(stateKey);
        redisService.delete(pendingKey);
        log.debug("清除对话状态: conversationId={}", conversationId);
    }
}
