package com.imkqas.service.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * Agent 问答请求
 *
 * @author 系统
 * @version 1.0
 */
public record AgentRequest(
        @NotBlank(message = "查询内容不能为空") String query,
        Long userId,
        Long conversationId
) {
}
