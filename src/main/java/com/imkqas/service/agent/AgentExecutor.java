package com.imkqas.service.agent;

import com.imkqas.config.AgentProperties;
import com.imkqas.service.rag.MultiRetrievalService;
import com.imkqas.service.rag.SafetyDecision;
import com.imkqas.service.rag.SafetyGuardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 安全执行器
 * 负责急症预检、分流、超时控制与回答净化，兜底 Agent 失效场景
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
public class AgentExecutor {

    private final MedicalAgent medicalAgent;
    private final MedicalTools medicalTools;
    private final QueryComplexityClassifier classifier;
    private final SafetyGuardService safetyGuardService;
    private final AgentProperties agentProperties;

    public AgentExecutor(MedicalAgent medicalAgent,
                         MedicalTools medicalTools,
                         QueryComplexityClassifier classifier,
                         SafetyGuardService safetyGuardService,
                         AgentProperties agentProperties) {
        this.medicalAgent = medicalAgent;
        this.medicalTools = medicalTools;
        this.classifier = classifier;
        this.safetyGuardService = safetyGuardService;
        this.agentProperties = agentProperties;
    }

    /**
     * 执行 Agent 问答
     *
     * @param query          用户问题
     * @param userId         用户ID（可为 null）
     * @param conversationId 对话ID（可为 null）
     * @return 执行结果；超时/异常时返回 RAG_FALLBACK 降级信号，由调用方回退原 RAG
     */
    public AgentResult execute(String query, Long userId, Long conversationId) {
        long start = System.currentTimeMillis();

        // ① 急症预检（复用现有 SafetyGuardService）
        SafetyDecision decision = safetyGuardService.checkEmergency(query);
        if (decision.isBlocked()) {
            log.info("[Agent] 急症命中，直接阻断: query={}, reason={}", query, decision.getReasonCode());
            return AgentResult.blocked(decision.getAdviceMessage());
        }

        // ② 分流（MVP 阶段简单问题暂不接 RAG 快速链路，二期接入 QaServiceImpl）
        QueryComplexityClassifier.Route route = classifier.classify(query);
        if (route == QueryComplexityClassifier.Route.SIMPLE) {
            long elapsed = System.currentTimeMillis() - start;
            return new AgentResult("该问题已判定为简单问题，本期 MVP 未接入 RAG 快速链路，请使用 /api/qa/ask 接口。",
                    "SIMPLE", List.of(), 0, elapsed, false, List.of(), List.of());
        }

        // ③ 超时控制 + 调用 Agent
        String raw;
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> medicalAgent.answer(query));
            raw = future.get(agentProperties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[Agent] 执行超时: query={}, timeout={}s，交由调用方降级到原 RAG",
                    query, agentProperties.getTimeoutSeconds());
            return ragFallback(query, start, "timeout");
        } catch (Exception e) {
            log.error("[Agent] 执行异常: query={}，交由调用方降级到原 RAG", query, e);
            return ragFallback(query, start, "exception");
        }

        // ④ 后置净化（复用 sanitizeAnswer，MVP 用固定置信度触发危险模式替换）
        String answer = safetyGuardService.sanitizeAnswer(raw, 0.8);

        List<String> toolsCalled = medicalTools.drainToolCalls();
        List<MultiRetrievalService.RetrievalResult> sources = medicalTools.drainRetrievedSources();
        List<AgentToolStep> steps = medicalTools.drainToolSteps();
        long elapsed = System.currentTimeMillis() - start;
        log.info("[Agent] 执行完成: query={}, toolsCalled={}, sources={}, steps={}, elapsed={}ms",
                query, toolsCalled, sources.size(), steps.size(), elapsed);
        return new AgentResult(answer, "COMPLEX", toolsCalled, toolsCalled.size(), elapsed, false, sources, steps);
    }

    /**
     * 返回降级信号
     * 不再直接调用 qaService.answer()（其 COMPLEX 路由会重入 AgentExecutor 形成递归），
     * 改由调用方（QaServiceImpl）在检测到 RAG_FALLBACK 后就地回退原 RAG 链路。
     */
    private AgentResult ragFallback(String query, long start, String reason) {
        List<String> toolsCalled = medicalTools.drainToolCalls();
        medicalTools.drainRetrievedSources();
        medicalTools.drainToolSteps();
        log.info("[Agent] 返回降级信号: query={}, reason={}", query, reason);
        return new AgentResult(null, AgentResult.ROUTE_RAG_FALLBACK, toolsCalled, 0,
                System.currentTimeMillis() - start, false, List.of(), List.of());
    }
}
