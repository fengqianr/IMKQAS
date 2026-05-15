package com.student.utils.evaluation;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 管线步骤追踪上下文
 * 基于ThreadLocal记录RAG管线各步骤的耗时和中间状态，用于在线指标采集和离线评估
 *
 * 典型用法:
 * <pre>
 * PipelineTraceContext.start();
 * try {
 *     PipelineTraceContext.recordStep("查询预处理", 2, durationMs, 1, 1, Map.of("entities", 5));
 *     // ... 管线业务逻辑 ...
 *     PipelineTrace trace = PipelineTraceContext.finish();
 * } finally {
 *     PipelineTraceContext.clear();
 * }
 * </pre>
 *
 * @author 系统
 * @version 1.0
 */
public final class PipelineTraceContext {

    private static final ThreadLocal<PipelineTrace> TRACE = ThreadLocal.withInitial(PipelineTrace::new);

    /** 最近N次的trace记录，供在线指标采集器消费 */
    private static final ConcurrentLinkedQueue<PipelineTrace> RECENT_TRACES = new ConcurrentLinkedQueue<>();
    private static final int MAX_RECENT = 500;

    private PipelineTraceContext() {
    }

    /** 开始追踪 */
    public static void start() {
        TRACE.get().start();
    }

    /** 获取当前线程的trace */
    public static PipelineTrace get() {
        return TRACE.get();
    }

    /** 记录一个步骤 */
    public static void recordStep(String stepName, int stepOrder, long durationMs,
                                   int inputCount, int outputCount,
                                   Map<String, Object> intermediateData) {
        PipelineTrace trace = TRACE.get();
        trace.recordStep(stepName, stepOrder, durationMs, inputCount, outputCount, intermediateData);
    }

    /** 记录一个步骤（简化版，无中间数据） */
    public static void recordStep(String stepName, int stepOrder, long durationMs) {
        recordStep(stepName, stepOrder, durationMs, 0, 0, Collections.emptyMap());
    }

    /** 结束追踪并返回完整trace，同时推入近期队列供采集器消费 */
    public static PipelineTrace finish() {
        PipelineTrace trace = TRACE.get();
        trace.finish();
        RECENT_TRACES.offer(trace);
        while (RECENT_TRACES.size() > MAX_RECENT) {
            RECENT_TRACES.poll();
        }
        return trace;
    }

    /** 清除当前线程的trace */
    public static void clear() {
        TRACE.remove();
    }

    /** 获取近期所有trace并清空 */
    public static List<PipelineTrace> drainRecentTraces() {
        List<PipelineTrace> list = new ArrayList<>(RECENT_TRACES);
        RECENT_TRACES.clear();
        return list;
    }

    /**
     * 管线追踪记录
     */
    public static class PipelineTrace {
        private Instant pipelineStart;
        private Instant pipelineEnd;
        private final List<StepRecord> steps = new ArrayList<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private boolean finished;

        public void start() {
            this.pipelineStart = Instant.now();
            this.finished = false;
        }

        public void finish() {
            this.pipelineEnd = Instant.now();
            this.finished = true;
        }

        public void recordStep(String stepName, int stepOrder, long durationMs,
                                int inputCount, int outputCount,
                                Map<String, Object> intermediateData) {
            StepRecord record = new StepRecord();
            record.stepName = stepName;
            record.stepOrder = stepOrder;
            record.durationMs = durationMs;
            record.inputCount = inputCount;
            record.outputCount = outputCount;
            record.intermediateData = intermediateData != null ? new HashMap<>(intermediateData) : new HashMap<>();
            record.status = "SUCCESS";
            record.timestamp = Instant.now();
            steps.add(record);
        }

        public void putMetadata(String key, Object value) {
            metadata.put(key, value);
        }

        // Getters
        public Instant getPipelineStart() { return pipelineStart; }
        public Instant getPipelineEnd() { return pipelineEnd; }
        public List<StepRecord> getSteps() { return Collections.unmodifiableList(steps); }
        public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
        public boolean isFinished() { return finished; }
        public long getTotalDurationMs() {
            if (pipelineStart != null && pipelineEnd != null) {
                return pipelineEnd.toEpochMilli() - pipelineStart.toEpochMilli();
            }
            return steps.stream().mapToLong(s -> s.durationMs).sum();
        }
    }

    /**
     * 单步记录
     */
    public static class StepRecord {
        public String stepName;
        public int stepOrder;
        public long durationMs;
        public int inputCount;
        public int outputCount;
        public Map<String, Object> intermediateData;
        public String status;
        public Instant timestamp;
    }
}
