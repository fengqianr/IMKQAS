package com.student.performance;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能测试基类
 * 提供性能测试的通用工具和方法，包括计时、并发测试和性能断言
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(OutputCaptureExtension.class)
public abstract class BasePerformanceTest {

    /**
     * 执行并测量方法执行时间
     *
     * @param task 要执行的任务
     * @param <T>  返回值类型
     * @return 包含执行时间和结果的PerformanceResult对象
     */
    protected <T> PerformanceResult<T> measureExecutionTime(Supplier<T> task) {
        Instant start = Instant.now();
        T result = task.get();
        Instant end = Instant.now();
        long durationMs = Duration.between(start, end).toMillis();
        return new PerformanceResult<>(result, durationMs);
    }

    /**
     * 执行并测量无返回值方法的执行时间
     *
     * @param task 要执行的任务
     * @return 执行时间（毫秒）
     */
    protected long measureExecutionTime(Runnable task) {
        Instant start = Instant.now();
        task.run();
        Instant end = Instant.now();
        return Duration.between(start, end).toMillis();
    }

    /**
     * 断言执行时间不超过阈值
     *
     * @param task      要执行的任务
     * @param maxTimeMs 最大允许时间（毫秒）
     * @param message   断言失败时的消息
     */
    protected void assertExecutionTime(Runnable task, long maxTimeMs, String message) {
        long duration = measureExecutionTime(task);
        assertTrue(duration <= maxTimeMs,
                () -> String.format("%s: 预期最大时间 %dms, 实际时间 %dms", message, maxTimeMs, duration));
    }

    /**
     * 断言执行时间不超过阈值（带返回值）
     *
     * @param task      要执行的任务
     * @param maxTimeMs 最大允许时间（毫秒）
     * @param message   断言失败时的消息
     * @param <T>       返回值类型
     * @return 任务执行结果
     */
    protected <T> T assertExecutionTime(Supplier<T> task, long maxTimeMs, String message) {
        PerformanceResult<T> result = measureExecutionTime(task);
        assertTrue(result.getDurationMs() <= maxTimeMs,
                () -> String.format("%s: 预期最大时间 %dms, 实际时间 %dms", message, maxTimeMs, result.getDurationMs()));
        return result.getResult();
    }

    /**
     * 执行并发测试
     *
     * @param task           要并发执行的任务
     * @param concurrentThreads 并发线程数
     * @param iterations     每个线程的迭代次数
     * @return 包含总时间、吞吐量和成功率的ConcurrentTestResult对象
     */
    protected ConcurrentTestResult executeConcurrentTest(Runnable task, int concurrentThreads, int iterations) {
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentThreads * iterations);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        // 提交任务
        for (int i = 0; i < concurrentThreads * iterations; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪
                    task.run();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 开始执行
        startLatch.countDown();

        try {
            // 等待所有任务完成，设置超时避免无限等待
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            Instant endTime = Instant.now();

            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            long totalTimeMs = Duration.between(startTime, endTime).toMillis();
            int totalTasks = concurrentThreads * iterations;
            double throughput = totalTasks / (totalTimeMs / 1000.0); // 任务/秒
            double successRate = successCount.get() / (double) totalTasks;

            return new ConcurrentTestResult(totalTimeMs, throughput, successRate, successCount.get(), failureCount.get());

        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new RuntimeException("并发测试被中断", e);
        }
    }

    /**
     * 断言并发测试性能指标
     *
     * @param task             要并发执行的任务
     * @param concurrentThreads 并发线程数
     * @param iterations       每个线程的迭代次数
     * @param maxTimeMs        最大允许总时间（毫秒）
     * @param minThroughput    最小吞吐量（任务/秒）
     * @param minSuccessRate   最小成功率（0-1）
     */
    protected void assertConcurrentPerformance(Runnable task, int concurrentThreads, int iterations,
                                               long maxTimeMs, double minThroughput, double minSuccessRate) {
        ConcurrentTestResult result = executeConcurrentTest(task, concurrentThreads, iterations);

        assertTrue(result.getTotalTimeMs() <= maxTimeMs,
                () -> String.format("总时间超出预期: 预期最大 %dms, 实际 %dms", maxTimeMs, result.getTotalTimeMs()));

        assertTrue(result.getThroughput() >= minThroughput,
                () -> String.format("吞吐量低于预期: 预期最小 %.2f 任务/秒, 实际 %.2f 任务/秒",
                        minThroughput, result.getThroughput()));

        assertTrue(result.getSuccessRate() >= minSuccessRate,
                () -> String.format("成功率低于预期: 预期最小 %.2f%%, 实际 %.2f%%",
                        minSuccessRate * 100, result.getSuccessRate() * 100));
    }

    /**
     * 性能测试结果封装类
     *
     * @param <T> 结果类型
     */
    public static class PerformanceResult<T> {
        private final T result;
        private final long durationMs;

        public PerformanceResult(T result, long durationMs) {
            this.result = result;
            this.durationMs = durationMs;
        }

        public T getResult() {
            return result;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    /**
     * 并发测试结果封装类
     */
    public static class ConcurrentTestResult {
        private final long totalTimeMs;
        private final double throughput; // 任务/秒
        private final double successRate; // 0-1
        private final int successCount;
        private final int failureCount;

        public ConcurrentTestResult(long totalTimeMs, double throughput, double successRate,
                                    int successCount, int failureCount) {
            this.totalTimeMs = totalTimeMs;
            this.throughput = throughput;
            this.successRate = successRate;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }

        public long getTotalTimeMs() {
            return totalTimeMs;
        }

        public double getThroughput() {
            return throughput;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        @Override
        public String toString() {
            return String.format("ConcurrentTestResult{总时间=%dms, 吞吐量=%.2f 任务/秒, 成功率=%.2f%%, 成功=%d, 失败=%d}",
                    totalTimeMs, throughput, successRate * 100, successCount, failureCount);
        }
    }
}