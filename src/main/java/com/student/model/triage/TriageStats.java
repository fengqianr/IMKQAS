package com.student.model.triage;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科室分流统计信息
 */
@Data
public class TriageStats {
    private long totalRequests = 0L;
    private long ruleEngineRequests = 0L;
    private long llmFallbackRequests = 0L;
    private long emergencyDetections = 0L;
    private double averageResponseTimeMs = 0.0;
    private double cacheHitRate = 0.0;
    private LocalDateTime lastUpdated = LocalDateTime.now();

    /**
     * 计算规则引擎使用率
     */
    public double getRuleEngineUsageRate() {
        if (totalRequests == 0) return 0.0;
        return (double) ruleEngineRequests / totalRequests * 100;
    }

    /**
     * 计算LLM降级率
     */
    public double getLlmFallbackRate() {
        if (totalRequests == 0) return 0.0;
        return (double) llmFallbackRequests / totalRequests * 100;
    }
}