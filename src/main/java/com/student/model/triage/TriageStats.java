package com.student.model.triage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 科室分流统计信息实体
 * 存储科室分流服务的统计信息
 *
 * @author 系统生成
 * @version 1.0
 * @since 2026-04-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageStats {

    /**
     * 总请求数
     */
    private Long totalRequests;

    /**
     * 规则引擎处理的请求数
     */
    private Long ruleEngineRequests;

    /**
     * LLM降级处理的请求数
     */
    private Long llmFallbackRequests;

    /**
     * 急诊检测次数
     */
    private Long emergencyDetections;

    /**
     * 平均响应时间（毫秒）
     */
    private Double averageResponseTimeMs;

    /**
     * 缓存命中率（0.0-1.0）
     */
    private Double cacheHitRate;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdated;

    /**
     * 获取规则引擎使用率
     *
     * @return 规则引擎使用率（0.0-1.0）
     */
    public double getRuleEngineUsageRate() {
        if (totalRequests == null || totalRequests == 0) {
            return 0.0;
        }
        if (ruleEngineRequests == null) {
            return 0.0;
        }
        return (double) ruleEngineRequests / totalRequests;
    }

    /**
     * 获取LLM降级率
     *
     * @return LLM降级率（0.0-1.0）
     */
    public double getLlmFallbackRate() {
        if (totalRequests == null || totalRequests == 0) {
            return 0.0;
        }
        if (llmFallbackRequests == null) {
            return 0.0;
        }
        return (double) llmFallbackRequests / totalRequests;
    }

    /**
     * 获取急诊检测率
     *
     * @return 急诊检测率（0.0-1.0）
     */
    public double getEmergencyDetectionRate() {
        if (totalRequests == null || totalRequests == 0) {
            return 0.0;
        }
        if (emergencyDetections == null) {
            return 0.0;
        }
        return (double) emergencyDetections / totalRequests;
    }

    /**
     * 获取规则引擎使用率百分比
     *
     * @return 规则引擎使用率百分比（0-100）
     */
    public int getRuleEngineUsagePercentage() {
        return (int) Math.round(getRuleEngineUsageRate() * 100);
    }

    /**
     * 获取LLM降级率百分比
     *
     * @return LLM降级率百分比（0-100）
     */
    public int getLlmFallbackPercentage() {
        return (int) Math.round(getLlmFallbackRate() * 100);
    }

    /**
     * 获取缓存命中率百分比
     *
     * @return 缓存命中率百分比（0-100）
     */
    public int getCacheHitPercentage() {
        return cacheHitRate != null ? (int) Math.round(cacheHitRate * 100) : 0;
    }

    /**
     * 获取平均响应时间描述
     *
     * @return 平均响应时间描述文本
     */
    public String getAverageResponseTimeDescription() {
        if (averageResponseTimeMs == null) {
            return "未知";
        }
        if (averageResponseTimeMs < 1000) {
            return String.format("%.1fms", averageResponseTimeMs);
        } else {
            return String.format("%.2fs", averageResponseTimeMs / 1000.0);
        }
    }

    /**
     * 获取统计摘要
     *
     * @return 统计摘要文本
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("科室分流统计摘要\n");
        sb.append("================\n");

        if (totalRequests != null) {
            sb.append("总请求数: ").append(totalRequests).append("\n");
        }

        sb.append("规则引擎使用率: ").append(getRuleEngineUsagePercentage()).append("%\n");
        sb.append("LLM降级率: ").append(getLlmFallbackPercentage()).append("%\n");

        if (emergencyDetections != null) {
            sb.append("急诊检测次数: ").append(emergencyDetections).append("\n");
        }

        if (averageResponseTimeMs != null) {
            sb.append("平均响应时间: ").append(getAverageResponseTimeDescription()).append("\n");
        }

        sb.append("缓存命中率: ").append(getCacheHitPercentage()).append("%\n");

        if (lastUpdated != null) {
            sb.append("最后更新: ").append(lastUpdated).append("\n");
        }

        return sb.toString();
    }

    /**
     * 判断统计信息是否为空
     *
     * @return 如果统计信息为空返回true，否则返回false
     */
    public boolean isEmpty() {
        return totalRequests == null || totalRequests == 0;
    }

    /**
     * 获取性能指标描述
     *
     * @return 性能指标描述
     */
    public String getPerformanceDescription() {
        if (averageResponseTimeMs == null) {
            return "性能数据不可用";
        }

        if (averageResponseTimeMs < 100) {
            return "优秀 (<100ms)";
        } else if (averageResponseTimeMs < 500) {
            return "良好 (100-500ms)";
        } else if (averageResponseTimeMs < 1000) {
            return "一般 (500-1000ms)";
        } else {
            return "较慢 (>1000ms)";
        }
    }
}