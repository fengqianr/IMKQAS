package com.student.service.evaluation;

import com.student.entity.evaluation.OnlineMetricsSnapshot;

/**
 * 在线指标采集器接口
 * 定时采集RAG管线的运行时健康度指标
 *
 * @author 系统
 * @version 1.0
 */
public interface OnlineMetricsCollector {

    /**
     * 手动触发一次指标采集快照
     *
     * @return 采集到的在线指标快照
     */
    OnlineMetricsSnapshot collectSnapshot();

    /**
     * 获取当前窗口的最新快照
     */
    OnlineMetricsSnapshot getCurrentSnapshot();

    /**
     * 获取累积统计（自启动以来）
     */
    CumulativeStats getCumulativeStats();

    /**
     * 累积统计
     */
    class CumulativeStats {
        /** 总请求数 */
        public long totalRequests;
        /** 成功数 */
        public long successfulRequests;
        /** 失败数 */
        public long failedRequests;
        /** 成功率 */
        public double successRate;
        /** 平均耗时（ms） */
        public double avgTotalMs;
        /** 语义缓存命中率 */
        public double cacheHitRate;
        /** 安全阻断总数 */
        public long totalSafetyBlocks;
        /** 自启动以来的运行时间（秒） */
        public long uptimeSeconds;
    }
}
