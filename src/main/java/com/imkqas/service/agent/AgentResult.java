package com.imkqas.service.agent;

import com.imkqas.service.rag.MultiRetrievalService;

import java.util.List;

/**
 * Agent 执行结果
 * 包含回答、分流结果、工具调用轨迹、检索来源等诊断信息
 *
 * @author 系统
 * @version 1.0
 */
public record AgentResult(
        String answer,
        String route,
        List<String> toolsCalled,
        int iterations,
        long elapsedMs,
        boolean blocked,
        List<MultiRetrievalService.RetrievalResult> sources,
        List<AgentToolStep> steps
) {

    /** 降级路由标识：Agent 超时/异常，需由调用方回退原 RAG 链路 */
    public static final String ROUTE_RAG_FALLBACK = "RAG_FALLBACK";

    /** 急症阻断结果 */
    public static AgentResult blocked(String advice) {
        return new AgentResult(advice, null, List.of(), 0, 0, true, List.of(), List.of());
    }

    /** 是否为降级信号（需调用方回退原 RAG，而非直接返回 answer） */
    public boolean isFallback() {
        return ROUTE_RAG_FALLBACK.equals(route);
    }
}
