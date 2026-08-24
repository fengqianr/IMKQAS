package com.imkqas.controller.agent;

import com.imkqas.dto.ApiResponse;
import com.imkqas.entity.Message;
import com.imkqas.service.agent.AgentExecutor;
import com.imkqas.service.agent.AgentRequest;
import com.imkqas.service.agent.AgentResult;
import com.imkqas.service.common.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ReAct Agent 控制器
 * 提供基于 ReAct 的智能体问答入口（复杂问题多轮推理）
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "ReAct Agent", description = "基于 ReAct 的智能体问答接口")
public class AgentController {

    private final AgentExecutor agentExecutor;
    private final MessageService messageService;

    @PostMapping("/answer")
    @Operation(summary = "Agent 问答", description = "复杂问题走 ReAct Agent 多轮推理，简单问题返回提示")
    public ApiResponse<AgentResult> answer(@Valid @RequestBody AgentRequest request) {
        log.info("[Agent] 请求: query={}, userId={}, conversationId={}",
                request.query(), request.userId(), request.conversationId());
        AgentResult result = agentExecutor.execute(request.query(), request.userId(), request.conversationId());
        // 持久化用户问题与 Agent 回答内容（仅当指定对话且回答有效时）
        persistMessages(request, result);
        return ApiResponse.success(result);
    }

    /**
     * 将用户问题与 Agent 回答内容持久化到 messages 表。
     * 仅在请求携带 conversationId 且 Agent 正常返回（非降级）时写入，避免脏数据。
     */
    private void persistMessages(AgentRequest request, AgentResult result) {
        Long conversationId = request.conversationId();
        if (conversationId == null) {
            log.info("[Agent] conversationId 为空，跳过消息持久化: query={}", truncate(request.query()));
            return;
        }
        if (result.isFallback() || result.answer() == null) {
            log.info("[Agent] Agent 降级或回答为空，跳过消息持久化: conversationId={}", conversationId);
            return;
        }
        try {
            messageService.save(Message.builder()
                    .conversationId(conversationId)
                    .role(Message.Role.USER)
                    .content(request.query())
                    .build());
            messageService.save(Message.builder()
                    .conversationId(conversationId)
                    .role(Message.Role.ASSISTANT)
                    .content(result.answer())
                    .build());
            log.info("[Agent] 问答已持久化: conversationId={}, answerLength={}",
                    conversationId, result.answer().length());
        } catch (Exception e) {
            log.error("[Agent] 持久化问答失败: conversationId={}", conversationId, e);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }
}
