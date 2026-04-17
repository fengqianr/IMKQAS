package com.student.service.triage.stats;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.EmergencyLevel;
import com.student.model.triage.TriageStats;
import com.student.service.triage.config.TriageConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分流统计收集器
 * 负责收集、聚合和提供分流服务的统计信息
 *
 * @author 系统生成
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class TriageStatsCollector {

    private final TriageConfig config;

    // ========== 请求统计 ==========
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    // ========== 引擎使用统计 ==========
    private final AtomicInteger ruleEngineRequests = new AtomicInteger(0);
    private final AtomicInteger llmRequests = new AtomicInteger(0);
    private final AtomicInteger hybridRequests = new AtomicInteger(0);
    private final AtomicInteger fallbackRequests = new AtomicInteger(0);

    // ========== 性能统计 ==========
    private final AtomicLong ruleEngineTotalTime = new AtomicLong(0);
    private final AtomicLong llmTotalTime = new AtomicLong(0);
    private final AtomicLong emergencyDetectionTotalTime = new AtomicLong(0);

    // ========== 质量统计 ==========
    private final AtomicInteger ruleEngineSuccessCount = new AtomicInteger(0);
    private final AtomicInteger llmSuccessCount = new AtomicInteger(0);
    private final AtomicInteger timeoutCount = new AtomicInteger(0);

    // ========== 业务统计 ==========
    private final ConcurrentHashMap<String, AtomicInteger> departmentCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<EmergencyLevel, AtomicInteger> emergencyCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> sourceCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AtomicInteger> confidenceDistribution = new ConcurrentHashMap<>();

    // 时间窗口统计
    private volatile LocalDateTime statsStartTime = LocalDateTime.now();
    private volatile LocalDateTime lastResetTime = LocalDateTime.now();

    /**
     * 记录分流结果
     *
     * @param result 分流结果
     * @param processingTime 处理时间（毫秒）
     */
    public void recordTriageResult(DepartmentTriageResult result, long processingTime) {
        if (!config.isEnableStats()) {
            return;
        }

        try {
            totalRequests.incrementAndGet();
            totalProcessingTime.addAndGet(processingTime);

            // 记录成功/失败
            if (result != null && result.getConfidence() > 0) {
                successfulRequests.incrementAndGet();
            } else {
                failedRequests.incrementAndGet();
            }

            if (result == null) {
                return;
            }

            // 记录来源统计
            String source = result.getSource();
            if (source != null) {
                sourceCounters.computeIfAbsent(source, k -> new AtomicInteger(0)).incrementAndGet();

                // 分类记录引擎使用
                switch (source) {
                    case "RULE_ENGINE":
                    case "RULE_ENGINE_FALLBACK":
                        ruleEngineRequests.incrementAndGet();
                        if (result.getConfidence() >= config.getRuleEngineThreshold()) {
                            ruleEngineSuccessCount.incrementAndGet();
                        }
                        break;
                    case "LLM":
                    case "LLM_FALLBACK":
                        llmRequests.incrementAndGet();
                        if (result.getConfidence() >= 0.5) {
                            llmSuccessCount.incrementAndGet();
                        }
                        break;
                    case "HYBRID":
                        hybridRequests.incrementAndGet();
                        break;
                    case "FALLBACK":
                        fallbackRequests.incrementAndGet();
                        break;
                }
            }

            // 记录科室推荐统计
            if (result.getRecommendations() != null) {
                result.getRecommendations().forEach(rec -> {
                    if (rec.getDepartmentName() != null) {
                        departmentCounters.computeIfAbsent(
                            rec.getDepartmentName(),
                            k -> new AtomicInteger(0)
                        ).incrementAndGet();
                    }
                });
            }

            // 记录急诊检测统计
            if (result.getEmergencyCheck() != null && result.getEmergencyCheck().isEmergency()) {
                EmergencyLevel level = result.getEmergencyCheck().getEmergencyLevel();
                emergencyCounters.computeIfAbsent(
                    level,
                    k -> new AtomicInteger(0)
                ).incrementAndGet();
            }

            // 记录置信度分布
            int confidenceBucket = (int) (result.getConfidence() * 10); // 0-10分桶
            confidenceDistribution.computeIfAbsent(
                confidenceBucket,
                k -> new AtomicInteger(0)
            ).incrementAndGet();

            // 定期清理过时统计
            if (totalRequests.get() % 1000 == 0) {
                cleanupOldStats();
            }

        } catch (Exception e) {
            log.error("统计记录失败", e);
        }
    }

    /**
     * 记录引擎处理时间
     *
     * @param engineType 引擎类型（RULE_ENGINE, LLM, EMERGENCY）
     * @param processingTime 处理时间（毫秒）
     */
    public void recordEngineProcessingTime(String engineType, long processingTime) {
        if (!config.isEnableStats()) {
            return;
        }

        switch (engineType) {
            case "RULE_ENGINE":
                ruleEngineTotalTime.addAndGet(processingTime);
                break;
            case "LLM":
                llmTotalTime.addAndGet(processingTime);
                break;
            case "EMERGENCY":
                emergencyDetectionTotalTime.addAndGet(processingTime);
                break;
        }
    }

    /**
     * 记录超时事件
     */
    public void recordTimeout() {
        if (config.isEnableStats()) {
            timeoutCount.incrementAndGet();
        }
    }

    /**
     * 记录降级事件
     */
    public void recordFallback(String fallbackReason) {
        if (config.isEnableStats()) {
            log.info("分流降级事件: {}", fallbackReason);
            fallbackRequests.incrementAndGet();
        }
    }

    /**
     * 获取统计信息
     */
    public TriageStats getStats() {
        int total = totalRequests.get();
        int successful = successfulRequests.get();
        int failed = failedRequests.get();

        // 计算成功率
        double successRate = total > 0 ? (double) successful / total : 0.0;

        // 计算平均处理时间
        double avgProcessingTime = total > 0 ? (double) totalProcessingTime.get() / total : 0.0;

        // 计算引擎平均时间
        double ruleEngineAvgTime = ruleEngineRequests.get() > 0 ?
            (double) ruleEngineTotalTime.get() / ruleEngineRequests.get() : 0.0;
        double llmAvgTime = llmRequests.get() > 0 ?
            (double) llmTotalTime.get() / llmRequests.get() : 0.0;
        double emergencyAvgTime = emergencyDetectionTotalTime.get() > 0 ?
            (double) emergencyDetectionTotalTime.get() / Math.max(1, successful) : 0.0;

        // 计算引擎成功率
        double ruleEngineSuccessRate = ruleEngineRequests.get() > 0 ?
            (double) ruleEngineSuccessCount.get() / ruleEngineRequests.get() : 0.0;
        double llmSuccessRate = llmRequests.get() > 0 ?
            (double) llmSuccessCount.get() / llmRequests.get() : 0.0;

        // 构建科室推荐分布
        Map<String, Integer> topDepartments = getTopDepartments(10);

        // 构建紧急级别分布
        Map<EmergencyLevel, Integer> emergencyDistribution = getEmergencyDistribution();

        // 构建来源分布
        Map<String, Integer> sourceDistribution = getSourceDistribution();

        // 构建置信度分布
        Map<Integer, Integer> confidenceDistributionMap = getConfidenceDistribution();

        return new TriageStats(
            total,
            successful,
            failed,
            successRate,
            avgProcessingTime,
            ruleEngineRequests.get(),
            llmRequests.get(),
            hybridRequests.get(),
            fallbackRequests.get(),
            ruleEngineAvgTime,
            llmAvgTime,
            emergencyAvgTime,
            ruleEngineSuccessRate,
            llmSuccessRate,
            timeoutCount.get(),
            topDepartments,
            emergencyDistribution,
            sourceDistribution,
            confidenceDistributionMap,
            statsStartTime,
            lastResetTime
        );
    }

    /**
     * 获取热门科室推荐
     */
    private Map<String, Integer> getTopDepartments(int limit) {
        return departmentCounters.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get()))
            .limit(limit)
            .collect(
                java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get()
                )
            );
    }

    /**
     * 获取紧急级别分布
     */
    private Map<EmergencyLevel, Integer> getEmergencyDistribution() {
        Map<EmergencyLevel, Integer> distribution = new java.util.HashMap<>();
        emergencyCounters.forEach((level, counter) -> {
            distribution.put(level, counter.get());
        });
        return distribution;
    }

    /**
     * 获取来源分布
     */
    private Map<String, Integer> getSourceDistribution() {
        Map<String, Integer> distribution = new java.util.HashMap<>();
        sourceCounters.forEach((source, counter) -> {
            distribution.put(source, counter.get());
        });
        return distribution;
    }

    /**
     * 获取置信度分布
     */
    private Map<Integer, Integer> getConfidenceDistribution() {
        Map<Integer, Integer> distribution = new java.util.HashMap<>();
        confidenceDistribution.forEach((bucket, counter) -> {
            distribution.put(bucket, counter.get());
        });
        return distribution;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        totalProcessingTime.set(0);

        ruleEngineRequests.set(0);
        llmRequests.set(0);
        hybridRequests.set(0);
        fallbackRequests.set(0);

        ruleEngineTotalTime.set(0);
        llmTotalTime.set(0);
        emergencyDetectionTotalTime.set(0);

        ruleEngineSuccessCount.set(0);
        llmSuccessCount.set(0);
        timeoutCount.set(0);

        departmentCounters.clear();
        emergencyCounters.clear();
        sourceCounters.clear();
        confidenceDistribution.clear();

        lastResetTime = LocalDateTime.now();

        log.info("分流统计信息已重置");
    }

    /**
     * 清理过时统计
     */
    private void cleanupOldStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = now.minusDays(config.getStatsRetentionDays());

        if (statsStartTime.isBefore(cutoffTime)) {
            log.info("清理过时分流统计信息");
            resetStats();
        }
    }

    /**
     * 获取统计采样率
     */
    public double getSamplingRate() {
        return config.getStatsSamplingRate();
    }

    /**
     * 检查是否启用统计
     */
    public boolean isStatsEnabled() {
        return config.isEnableStats();
    }

    /**
     * 获取统计开始时间
     */
    public LocalDateTime getStatsStartTime() {
        return statsStartTime;
    }

    /**
     * 获取最后重置时间
     */
    public LocalDateTime getLastResetTime() {
        return lastResetTime;
    }

    /**
     * 获取总请求数
     */
    public int getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        int total = totalRequests.get();
        return total > 0 ? (double) successfulRequests.get() / total : 0.0;
    }

    /**
     * 导出统计快照（用于监控和告警）
     */
    public Map<String, Object> exportSnapshot() {
        Map<String, Object> snapshot = new java.util.HashMap<>();

        snapshot.put("totalRequests", totalRequests.get());
        snapshot.put("successfulRequests", successfulRequests.get());
        snapshot.put("failedRequests", failedRequests.get());
        snapshot.put("successRate", getSuccessRate());
        snapshot.put("avgProcessingTime", totalRequests.get() > 0 ?
            (double) totalProcessingTime.get() / totalRequests.get() : 0.0);

        snapshot.put("ruleEngineRequests", ruleEngineRequests.get());
        snapshot.put("llmRequests", llmRequests.get());
        snapshot.put("hybridRequests", hybridRequests.get());
        snapshot.put("fallbackRequests", fallbackRequests.get());

        snapshot.put("timeoutCount", timeoutCount.get());
        snapshot.put("statsStartTime", statsStartTime.toString());
        snapshot.put("lastResetTime", lastResetTime.toString());

        return snapshot;
    }
}