package com.imkqas.service.agent;

/**
 * Agent 单次工具调用步骤
 * 记录工具名、入参、返回结果摘要与耗时，用于前端可视化 Agent 的推理/工具调用过程
 *
 * @author 系统
 * @version 1.0
 */
public record AgentToolStep(
        String toolName,
        String params,
        String result,
        long durationMs
) {
}
