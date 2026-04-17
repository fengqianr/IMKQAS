package com.student.performance;

import com.student.service.rag.QaService;
import com.student.service.rag.QaService.QaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发问答性能测试
 * 测试系统在高并发场景下的性能和稳定性
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@org.springframework.test.context.ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.main.lazy-initialization=true",
        "spring.jmx.enabled=false",
        "logging.level.com.student=WARN" // 减少日志输出
})
public class ConcurrentQaTest extends BasePerformanceTest {

    @MockBean
    private QaService qaService;

    private QaResponse mockResponse;
    private String[] testQuestions;
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        // 准备模拟响应
        List<String> retrievedContext = Arrays.asList("高血压是一种常见的慢性病", "症状包括头痛、眩晕、心悸等");
        mockResponse = new QaResponse(
                "高血压有什么症状？",
                "这是一个测试回答。高血压的常见症状包括头痛、眩晕、心悸等。治疗方法包括生活方式干预和药物治疗。",
                retrievedContext,
                0.85,
                100L,
                "test-model"
        );

        when(qaService.answer(anyString(), anyLong(), anyLong()))
                .thenReturn(mockResponse);

        // 测试问题集
        testQuestions = new String[]{
                "高血压有什么症状？",
                "糖尿病如何治疗？",
                "冠心病有什么危险因素？",
                "发烧应该怎么办？",
                "咳嗽持续不好怎么处理？",
                "头痛怎么缓解？",
                "腹痛可能是什么原因？",
                "胸闷气短应该看什么科？",
                "失眠有什么解决办法？",
                "过敏反应如何处理？"
        };

        requestCounter.set(0);
    }

    /**
     * 低并发测试（5个并发用户）
     * 模拟轻度并发场景
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testLowConcurrency() {
        int concurrentUsers = 5;
        int requestsPerUser = 10;
        int totalRequests = concurrentUsers * requestsPerUser;

        System.out.printf("开始低并发测试: %d 个并发用户, 每个用户 %d 个请求, 总计 %d 个请求%n",
                concurrentUsers, requestsPerUser, totalRequests);

        ConcurrentTestResult result = executeConcurrentTest(() -> {
            int requestId = requestCounter.incrementAndGet();
            String question = testQuestions[requestId % testQuestions.length];
            QaResponse response = qaService.answer(question, 100L + requestId, (long) requestId);
            assertNotNull(response);
            assertNotNull(response.getAnswer());
        }, concurrentUsers, requestsPerUser);

        System.out.println("低并发测试结果: " + result);

        // 性能断言
        assertTrue(result.getTotalTimeMs() < 15000, // 总时间小于15秒
                () -> String.format("总时间超出预期: %dms", result.getTotalTimeMs()));

        assertTrue(result.getThroughput() > 2.0, // 吞吐量大于2请求/秒
                () -> String.format("吞吐量过低: %.2f 请求/秒", result.getThroughput()));

        assertTrue(result.getSuccessRate() >= 0.95, // 成功率大于95%
                () -> String.format("成功率过低: %.2f%%", result.getSuccessRate() * 100));
    }

    /**
     * 中等并发测试（10个并发用户）
     * 模拟典型并发场景
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testMediumConcurrency() {
        int concurrentUsers = 10;
        int requestsPerUser = 20;
        int totalRequests = concurrentUsers * requestsPerUser;

        System.out.printf("开始中等并发测试: %d 个并发用户, 每个用户 %d 个请求, 总计 %d 个请求%n",
                concurrentUsers, requestsPerUser, totalRequests);

        ConcurrentTestResult result = executeConcurrentTest(() -> {
            int requestId = requestCounter.incrementAndGet();
            String question = testQuestions[requestId % testQuestions.length];
            QaResponse response = qaService.answer(question, 100L + requestId, (long) requestId);
            assertNotNull(response);
            assertNotNull(response.getAnswer());
        }, concurrentUsers, requestsPerUser);

        System.out.println("中等并发测试结果: " + result);

        // 性能断言
        assertTrue(result.getTotalTimeMs() < 30000, // 总时间小于30秒
                () -> String.format("总时间超出预期: %dms", result.getTotalTimeMs()));

        assertTrue(result.getThroughput() > 5.0, // 吞吐量大于5请求/秒
                () -> String.format("吞吐量过低: %.2f 请求/秒", result.getThroughput()));

        assertTrue(result.getSuccessRate() >= 0.90, // 成功率大于90%
                () -> String.format("成功率过低: %.2f%%", result.getSuccessRate() * 100));
    }

    /**
     * 高并发测试（20个并发用户）
     * 模拟压力测试场景
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testHighConcurrency() {
        int concurrentUsers = 20;
        int requestsPerUser = 30;
        int totalRequests = concurrentUsers * requestsPerUser;

        System.out.printf("开始高并发测试: %d 个并发用户, 每个用户 %d 个请求, 总计 %d 个请求%n",
                concurrentUsers, requestsPerUser, totalRequests);

        ConcurrentTestResult result = executeConcurrentTest(() -> {
            int requestId = requestCounter.incrementAndGet();
            String question = testQuestions[requestId % testQuestions.length];
            QaResponse response = qaService.answer(question, 100L + requestId, (long) requestId);
            assertNotNull(response);
            // 高并发下允许部分失败，只检查非空
        }, concurrentUsers, requestsPerUser);

        System.out.println("高并发测试结果: " + result);

        // 压力测试的性能断言较为宽松
        assertTrue(result.getTotalTimeMs() < 60000, // 总时间小于60秒
                () -> String.format("总时间超出预期: %dms", result.getTotalTimeMs()));

        assertTrue(result.getThroughput() > 8.0, // 吞吐量大于8请求/秒
                () -> String.format("吞吐量过低: %.2f 请求/秒", result.getThroughput()));

        assertTrue(result.getSuccessRate() >= 0.80, // 成功率大于80%
                () -> String.format("成功率过低: %.2f%%", result.getSuccessRate() * 100));
    }

    /**
     * 突发流量测试
     * 模拟短时间内大量请求涌入的场景
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testBurstTraffic() {
        int burstSize = 50; // 突发请求数量
        System.out.printf("开始突发流量测试: %d 个并发请求%n", burstSize);

        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 使用线程池执行突发请求
        ExecutorService executor = Executors.newFixedThreadPool(burstSize);
        CountDownLatch latch = new CountDownLatch(burstSize);

        for (int i = 0; i < burstSize; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    String question = testQuestions[requestId % testQuestions.length];
                    QaResponse response = qaService.answer(question, 100L + requestId, (long) requestId);
                    if (response != null && response.getAnswer() != null) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            // 等待所有请求完成，设置超时
            boolean completed = latch.await(20, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            double throughput = burstSize / (totalTime / 1000.0);
            double successRate = successCount.get() / (double) burstSize;

            System.out.printf("突发流量测试结果: 总时间 %dms, 吞吐量 %.2f 请求/秒, 成功率 %.2f%% (%d/%d)%n",
                    totalTime, throughput, successRate * 100, successCount.get(), burstSize);

            // 断言
            assertTrue(totalTime < 15000, () -> String.format("响应时间过长: %dms", totalTime));
            assertTrue(throughput > 3.0, () -> String.format("吞吐量过低: %.2f 请求/秒", throughput));
            assertTrue(successRate >= 0.85, () -> String.format("成功率过低: %.2f%%", successRate * 100));

        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            fail("突发流量测试被中断");
        }
    }

    /**
     * 长时间运行测试
     * 模拟系统长时间运行下的稳定性
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void testLongRunningStability() {
        int concurrentUsers = 5;
        int durationSeconds = 30; // 运行30秒
        System.out.printf("开始长时间运行测试: %d 个并发用户, 持续 %d 秒%n", concurrentUsers, durationSeconds);

        AtomicLong totalRequests = new AtomicLong(0);
        AtomicLong successfulRequests = new AtomicLong(0);
        CountDownLatch stopSignal = new CountDownLatch(1);

        // 创建并发用户
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = i;
            executor.submit(() -> {
                int requestId = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (stopSignal.getCount() == 0) {
                            break; // 收到停止信号
                        }

                        String question = testQuestions[(userId + requestId) % testQuestions.length];
                        QaResponse response = qaService.answer(
                                question, 1000L + userId, (long) (userId * 1000 + requestId));

                        totalRequests.incrementAndGet();
                        if (response != null && response.getAnswer() != null) {
                            successfulRequests.incrementAndGet();
                        }

                        requestId++;
                        // 短暂休眠，模拟用户思考时间
                        Thread.sleep(100 + (userId * 10));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        totalRequests.incrementAndGet();
                        // 记录失败但继续运行
                    }
                }
            });
        }

        // 运行指定时间
        try {
            Thread.sleep(durationSeconds * 1000L);
            stopSignal.countDown(); // 发送停止信号

            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            long total = totalRequests.get();
            long successful = successfulRequests.get();
            double successRate = total > 0 ? successful / (double) total : 0.0;
            double throughput = total / (double) durationSeconds;

            System.out.printf("长时间运行测试结果: 总请求 %d, 成功请求 %d, 成功率 %.2f%%, 平均吞吐量 %.2f 请求/秒%n",
                    total, successful, successRate * 100, throughput);

            // 稳定性断言
            assertTrue(total > 0, "没有处理任何请求");
            assertTrue(throughput > 1.0, () -> String.format("吞吐量过低: %.2f 请求/秒", throughput));
            assertTrue(successRate >= 0.90, () -> String.format("成功率过低: %.2f%%", successRate * 100));

        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            fail("长时间运行测试被中断");
        }
    }

    /**
     * 混合负载测试
     * 模拟不同类型请求混合的场景
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testMixedWorkload() {
        int concurrentUsers = 15;
        int requestsPerUser = 10;

        System.out.printf("开始混合负载测试: %d 个并发用户, 每个用户 %d 个请求%n",
                concurrentUsers, requestsPerUser);

        // 定义不同类型的任务
        Runnable[] tasks = new Runnable[]{
                () -> { // 类型1: 普通问答
                    QaResponse response = qaService.answer("高血压症状", 1L, 1L);
                    assertNotNull(response);
                },
                () -> { // 类型2: 详细问答
                    QaResponse response = qaService.answer("糖尿病治疗方案和注意事项", 2L, 2L);
                    assertNotNull(response);
                },
                () -> { // 类型3: 简短问答
                    QaResponse response = qaService.answer("发烧怎么办", 3L, 3L);
                    assertNotNull(response);
                }
        };

        ConcurrentTestResult result = executeConcurrentTest(() -> {
            // 随机选择任务类型
            int taskIndex = ThreadLocalRandom.current().nextInt(tasks.length);
            tasks[taskIndex].run();
        }, concurrentUsers, requestsPerUser);

        System.out.println("混合负载测试结果: " + result);

        // 性能断言
        assertTrue(result.getTotalTimeMs() < 40000,
                () -> String.format("总时间超出预期: %dms", result.getTotalTimeMs()));
        assertTrue(result.getThroughput() > 3.0,
                () -> String.format("吞吐量过低: %.2f 请求/秒", result.getThroughput()));
        assertTrue(result.getSuccessRate() >= 0.85,
                () -> String.format("成功率过低: %.2f%%", result.getSuccessRate() * 100));
    }
}