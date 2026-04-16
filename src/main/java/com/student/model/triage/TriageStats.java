package com.student.model.triage;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 科室分流统计信息
 */
@Data
public class TriageStats {
    // ========== 请求统计 ==========
    private int totalRequests = 0;
    private int successfulRequests = 0;
    private int failedRequests = 0;
    private double successRate = 0.0;
    private double avgProcessingTime = 0.0;

    // ========== 引擎使用统计 ==========
    private int ruleEngineRequests = 0;
    private int llmRequests = 0;
    private int hybridRequests = 0;
    private int fallbackRequests = 0;

    // ========== 性能统计 ==========
    private double ruleEngineAvgTime = 0.0;
    private double llmAvgTime = 0.0;
    private double emergencyAvgTime = 0.0;

    // ========== 质量统计 ==========
    private double ruleEngineSuccessRate = 0.0;
    private double llmSuccessRate = 0.0;
    private int timeoutCount = 0;

    // ========== 业务统计 ==========
    private Map<String, Integer> topDepartments;
    private Map<EmergencyLevel, Integer> emergencyDistribution;
    private Map<String, Integer> sourceDistribution;
    private Map<Integer, Integer> confidenceDistribution;

    // ========== 时间统计 ==========
    private LocalDateTime statsStartTime;
    private LocalDateTime lastResetTime;

    /**
     * 全参数构造函数（用于统计收集器）
     */
    public TriageStats(int totalRequests, int successfulRequests, int failedRequests,
                      double successRate, double avgProcessingTime,
                      int ruleEngineRequests, int llmRequests, int hybridRequests, int fallbackRequests,
                      double ruleEngineAvgTime, double llmAvgTime, double emergencyAvgTime,
                      double ruleEngineSuccessRate, double llmSuccessRate, int timeoutCount,
                      Map<String, Integer> topDepartments,
                      Map<EmergencyLevel, Integer> emergencyDistribution,
                      Map<String, Integer> sourceDistribution,
                      Map<Integer, Integer> confidenceDistribution,
                      LocalDateTime statsStartTime, LocalDateTime lastResetTime) {
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.successRate = successRate;
        this.avgProcessingTime = avgProcessingTime;
        this.ruleEngineRequests = ruleEngineRequests;
        this.llmRequests = llmRequests;
        this.hybridRequests = hybridRequests;
        this.fallbackRequests = fallbackRequests;
        this.ruleEngineAvgTime = ruleEngineAvgTime;
        this.llmAvgTime = llmAvgTime;
        this.emergencyAvgTime = emergencyAvgTime;
        this.ruleEngineSuccessRate = ruleEngineSuccessRate;
        this.llmSuccessRate = llmSuccessRate;
        this.timeoutCount = timeoutCount;
        this.topDepartments = topDepartments;
        this.emergencyDistribution = emergencyDistribution;
        this.sourceDistribution = sourceDistribution;
        this.confidenceDistribution = confidenceDistribution;
        this.statsStartTime = statsStartTime;
        this.lastResetTime = lastResetTime;
    }

    /**
     * 无参构造函数（用于序列化）
     */
    public TriageStats() {
    }

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
        return (double) llmRequests / totalRequests * 100;
    }
}