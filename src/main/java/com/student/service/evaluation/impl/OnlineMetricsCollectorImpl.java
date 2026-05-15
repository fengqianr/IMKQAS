package com.student.service.evaluation.impl;

import com.student.config.EvaluationConfig;
import com.student.entity.evaluation.OnlineMetricsSnapshot;
import com.student.mapper.evaluation.OnlineMetricsSnapshotMapper;
import com.student.service.evaluation.OnlineMetricsCollector;
import com.student.utils.evaluation.MetricsCalculator;
import com.student.utils.evaluation.PipelineTraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 在线指标采集器实现
 * 通过 @Scheduled 定时消费 PipelineTraceContext 中的数据并持久化到数据库
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OnlineMetricsCollectorImpl implements OnlineMetricsCollector {

    private final EvaluationConfig evaluationConfig;
    private final OnlineMetricsSnapshotMapper snapshotMapper;

    /** 上次采集时间 */
    private Instant lastCollectionTime = Instant.now();

    /** 累积统计 */
    private final AtomicLong cumTotalRequests = new AtomicLong(0);
    private final AtomicLong cumSuccessfulRequests = new AtomicLong(0);
    private final AtomicLong cumFailedRequests = new AtomicLong(0);
    private final AtomicLong cumCacheHits = new AtomicLong(0);
    private final AtomicLong cumCacheMisses = new AtomicLong(0);
    private final AtomicLong cumSafetyBlocks = new AtomicLong(0);
    private final AtomicLong cumTotalTimeMs = new AtomicLong(0);
    private final Instant startupTime = Instant.now();

    /** 最近一次的在线快照 */
    private volatile OnlineMetricsSnapshot currentSnapshot;

    @Override
    @Scheduled(fixedDelayString = "#{@evaluationConfig.online.collectionInterval * 1000}")
    public OnlineMetricsSnapshot collectSnapshot() {
        if (!evaluationConfig.isEnabled() || !evaluationConfig.getOnline().isEnabled()) {
            return null;
        }

        try {
            // 从PipelineTraceContext消费近期trace
            List<PipelineTraceContext.PipelineTrace> traces = PipelineTraceContext.drainRecentTraces();

            if (traces.isEmpty()) {
                return currentSnapshot;
            }

            // 计算延迟指标
            List<Long> totalTimes = new ArrayList<>();
            List<Long> retrievalTimes = new ArrayList<>();
            List<Long> llmTimes = new ArrayList<>();
            int cacheHits = 0;
            int cacheMisses = 0;
            int safetyBlocks = 0;
            List<Double> confidences = new ArrayList<>();

            for (PipelineTraceContext.PipelineTrace trace : traces) {
                long total = trace.getTotalDurationMs();
                totalTimes.add(total);

                // 从步骤记录中提取各阶段耗时
                for (PipelineTraceContext.StepRecord step : trace.getSteps()) {
                    switch (step.stepName) {
                        case "双路召回+RRF融合" -> retrievalTimes.add(step.durationMs);
                        case "LLM生成" -> llmTimes.add(step.durationMs);
                        case "安全兜底①-急症预检" -> {
                            if ("BLOCKED".equals(step.status)) safetyBlocks++;
                        }
                    }
                }

                // 缓存命中
                Object cacheHitObj = trace.getMetadata().get("cacheHit");
                if (Boolean.TRUE.equals(cacheHitObj)) {
                    cacheHits++;
                } else {
                    cacheMisses++;
                }

                // 置信度
                Object confObj = trace.getMetadata().get("confidence");
                if (confObj instanceof Double) {
                    confidences.add((Double) confObj);
                }
            }

            int numTraces = traces.size();
            int numRequests = numTraces;

            // 更新累积统计
            cumTotalRequests.addAndGet(numRequests);
            cumTotalTimeMs.addAndGet(totalTimes.stream().mapToLong(Long::longValue).sum());
            cumCacheHits.addAndGet(cacheHits);
            cumCacheMisses.addAndGet(cacheMisses);
            cumSafetyBlocks.addAndGet(safetyBlocks);
            // 成功率（非异常退出就算成功）
            cumSuccessfulRequests.addAndGet(numRequests);

            // 构建快照
            OnlineMetricsSnapshot snapshot = OnlineMetricsSnapshot.builder()
                    .snapshotTime(LocalDateTime.now())
                    .windowSeconds(evaluationConfig.getOnline().getCollectionInterval())
                    .totalRequests(numRequests)
                    .successfulRequests(numRequests)
                    .failedRequests(0)
                    .successRate(1.0)

                    // 延迟
                    .avgTotalMs(MetricsCalculator.average(
                            totalTimes.stream().map(Long::doubleValue).toList()))
                    .p50TotalMs(MetricsCalculator.percentile(totalTimes, 50))
                    .p95TotalMs(MetricsCalculator.percentile(totalTimes, 95))
                    .p99TotalMs(MetricsCalculator.percentile(totalTimes, 99))
                    .avgRetrievalMs(MetricsCalculator.average(
                            retrievalTimes.stream().map(Long::doubleValue).toList()))
                    .avgLlmMs(MetricsCalculator.average(
                            llmTimes.stream().map(Long::doubleValue).toList()))

                    // 缓存
                    .semanticCacheHits(cacheHits)
                    .semanticCacheMisses(cacheMisses)
                    .cacheHitRate((cacheHits + cacheMisses) > 0
                            ? (double) cacheHits / (cacheHits + cacheMisses) : 0.0)

                    // 安全
                    .emergencyBlockCount(safetyBlocks)
                    .confidenceBlockCount(0)
                    .safetyCheckCount(numRequests)
                    .safetyBlockRate(numRequests > 0 ? (double) safetyBlocks / numRequests : 0.0)

                    // 置信度分布
                    .confBucket035(0).confBucket3560(0).confBucket6080(0).confBucket80100(0)
                    .avgConfidence(MetricsCalculator.average(confidences))

                    // 检索模式
                    .vectorRetrievalCount(0)
                    .keywordRetrievalCount(0)
                    .hybridRetrievalCount(numRequests)

                    .build();

            // 填充置信度分桶
            int[] buckets = MetricsCalculator.confidenceDistribution(confidences);
            snapshot.setConfBucket035(buckets[0]);
            snapshot.setConfBucket3560(buckets[1]);
            snapshot.setConfBucket6080(buckets[2]);
            snapshot.setConfBucket80100(buckets[3]);

            // 持久化
            snapshotMapper.insert(snapshot);
            currentSnapshot = snapshot;
            lastCollectionTime = Instant.now();

            log.debug("在线指标采集完成: {} 条trace, avgTotal={:.0f}ms, cacheHitRate={:.1%}",
                    numTraces, snapshot.getAvgTotalMs(), snapshot.getCacheHitRate());

            return snapshot;

        } catch (Exception e) {
            log.error("在线指标采集失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public OnlineMetricsSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    @Override
    public CumulativeStats getCumulativeStats() {
        CumulativeStats stats = new CumulativeStats();
        stats.totalRequests = cumTotalRequests.get();
        stats.successfulRequests = cumSuccessfulRequests.get();
        stats.failedRequests = cumFailedRequests.get();
        stats.successRate = stats.totalRequests > 0
                ? (double) stats.successfulRequests / stats.totalRequests : 0.0;
        stats.avgTotalMs = stats.totalRequests > 0
                ? (double) cumTotalTimeMs.get() / stats.totalRequests : 0.0;
        long totalCache = cumCacheHits.get() + cumCacheMisses.get();
        stats.cacheHitRate = totalCache > 0
                ? (double) cumCacheHits.get() / totalCache : 0.0;
        stats.totalSafetyBlocks = cumSafetyBlocks.get();
        stats.uptimeSeconds = Instant.now().getEpochSecond() - startupTime.getEpochSecond();
        return stats;
    }
}
