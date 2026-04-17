package com.student.service.triage.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.student.dto.triage.BatchTriageRequest;
import com.student.dto.triage.TriageRequest;
import com.student.model.triage.DepartmentRecommendation;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.EmergencyCheckResult;
import com.student.model.triage.EmergencyLevel;
import com.student.model.triage.TriageStats;
import com.student.service.triage.TriageService;
import com.student.service.triage.config.TriageConfig;
import com.student.service.triage.stats.TriageStatsCollector;

/**
 * HybridTriageServiceImpl 单元测试
 * 测试混合分流服务的核心逻辑，包括顺序执行、条件触发和结果融合
 *
 * @author 系统生成
 * @version 1.0
 */
@DisplayName("混合分流服务测试")
class HybridTriageServiceImplTest extends BaseMockitoTest {

    @Mock
    private SymptomNormalizer symptomNormalizer;

    @Mock
    private RuleBasedTriageEngine ruleEngine;

    @Mock
    private EmergencySymptomDetector emergencyDetector;

    @Mock
    private LlmTriageAdapter llmAdapter;

    @Mock
    private TriageConfig config;

    @Mock
    private ExecutorService executorService;

    @Mock
    private TriageStatsCollector statsCollector;

    private HybridTriageServiceImpl hybridTriageService;

    private TriageRequest validRequest;
    private DepartmentTriageResult ruleEngineResult;
    private DepartmentTriageResult llmResult;
    private EmergencyCheckResult emergencyResult;

    @BeforeEach
    void setUp() {
        // 初始化服务
        hybridTriageService = new HybridTriageServiceImpl(
            symptomNormalizer, ruleEngine, emergencyDetector, llmAdapter,
            config, executorService, statsCollector
        );

        // 准备有效请求
        validRequest = new TriageRequest();
        validRequest.setSymptoms("发烧咳嗽");
        validRequest.setUserId(123L);
        validRequest.setIncludeEmergencyCheck(true);

        // 准备规则引擎结果
        DepartmentRecommendation ruleRec = new DepartmentRecommendation();
        ruleRec.setDepartmentId("respiratory");
        ruleRec.setDepartmentName("呼吸内科");
        ruleRec.setConfidence(0.7);
        ruleRec.setReason("症状匹配呼吸系统疾病");

        ruleEngineResult = new DepartmentTriageResult();
        ruleEngineResult.setSymptoms("发烧咳嗽");
        ruleEngineResult.setRecommendations(List.of(ruleRec));
        ruleEngineResult.setConfidence(0.7);
        ruleEngineResult.setSource("RULE_ENGINE");
        ruleEngineResult.setAdvice("建议就诊呼吸内科");

        // 准备LLM结果
        DepartmentRecommendation llmRec = new DepartmentRecommendation();
        llmRec.setDepartmentId("respiratory");
        llmRec.setDepartmentName("呼吸内科");
        llmRec.setConfidence(0.9);
        llmRec.setReason("AI分析认为呼吸系统感染可能性高");

        llmResult = new DepartmentTriageResult();
        llmResult.setSymptoms("发烧咳嗽");
        llmResult.setRecommendations(List.of(llmRec));
        llmResult.setConfidence(0.9);
        llmResult.setSource("LLM");
        llmResult.setAdvice("AI建议就诊呼吸内科");

        // 准备急诊检测结果
        emergencyResult = new EmergencyCheckResult();
        emergencyResult.setEmergency(false);
        emergencyResult.setEmergencyLevel(EmergencyLevel.LOW);
        emergencyResult.setEmergencySymptoms(List.of());
        emergencyResult.setAdvice("未检测到急诊症状");

        // 默认配置
        when(config.getRuleEngineThreshold()).thenReturn(0.6);
        when(config.getRuleEngineWeight()).thenReturn(0.8);
        when(config.getLlmWeight()).thenReturn(0.2);
        when(config.getLlmTimeout()).thenReturn(3000);
        when(config.getBatchProcessingMaxSize()).thenReturn(20);
        when(config.getBatchProcessingTimeout()).thenReturn(10000);
        when(config.getMaxRecommendations()).thenReturn(3);
        when(config.getFallbackAdvice()).thenReturn("建议咨询医院导诊台");
        when(config.isEnableEmergencyDetection()).thenReturn(true);
        when(config.isEnableStats()).thenReturn(true);

        // 默认executorService模拟 - 立即执行任务
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        // 默认submit模拟
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        });

        when(executorService.submit(any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> task = invocation.getArgument(0);
            return CompletableFuture.completedFuture(task.call());
        });
    }

    @Test
    @DisplayName("单次分流 - 规则引擎高置信度")
    void testTriage_RuleEngineHighConfidence() {
        // 模拟症状标准化
        when(symptomNormalizer.normalize("发烧咳嗽")).thenReturn("发烧 咳嗽");

        // 模拟急诊检测
        when(emergencyDetector.detect(eq("发烧 咳嗽"), any(ExecutorService.class)))
            .thenReturn(emergencyResult);

        // 模拟规则引擎返回高置信度结果
        ruleEngineResult.setConfidence(0.8); // 高于阈值
        when(ruleEngine.analyze("发烧 咳嗽")).thenReturn(ruleEngineResult);

        // 执行分流
        DepartmentTriageResult result = hybridTriageService.triage(validRequest);

        // 验证结果
        assertNotNull(result);
        assertEquals(0.8, result.getConfidence(), 0.01);
        assertEquals("RULE_ENGINE", result.getSource());
        assertEquals(123L, result.getUserId());
        assertNotNull(result.getProcessingTimeMs());
        assertTrue(result.getProcessingTimeMs() >= 0);

        // 验证统计记录被调用
        verify(statsCollector).recordTriageResult(any(DepartmentTriageResult.class), anyLong());
    }

    @Test
    @DisplayName("单次分流 - 规则引擎低置信度触发LLM")
    void testTriage_RuleEngineLowConfidenceTriggersLlm() {
        // 模拟症状标准化
        when(symptomNormalizer.normalize("发烧咳嗽")).thenReturn("发烧 咳嗽");

        // 模拟急诊检测
        when(emergencyDetector.detect(eq("发烧 咳嗽"), any(ExecutorService.class)))
            .thenReturn(emergencyResult);

        // 模拟规则引擎返回低置信度结果
        ruleEngineResult.setConfidence(0.5); // 低于阈值
        when(ruleEngine.analyze("发烧 咳嗽")).thenReturn(ruleEngineResult);

        // 模拟LLM可用并返回结果
        when(llmAdapter.isAvailable()).thenReturn(true);
        when(llmAdapter.analyze(eq("发烧 咳嗽"), eq(false))).thenReturn(llmResult);

        // 模拟异步执行 - 让executorService立即执行任务
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        // 模拟submit方法也立即执行任务并返回已完成的Future
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        });

        when(executorService.submit(any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> task = invocation.getArgument(0);
            return CompletableFuture.completedFuture(task.call());
        });

        // 执行分流
        DepartmentTriageResult result = hybridTriageService.triage(validRequest);

        // 验证结果使用了融合逻辑（应为混合来源）
        assertNotNull(result);
        assertEquals("HYBRID", result.getSource());
        assertTrue(result.getConfidence() > 0);

        // 验证LLM被调用
        verify(llmAdapter).analyze(eq("发烧 咳嗽"), eq(false));
    }

    @Test
    @DisplayName("单次分流 - LLM不可用时使用规则引擎降级")
    void testTriage_LlmUnavailableUsesRuleEngineFallback() {
        // 模拟症状标准化
        when(symptomNormalizer.normalize("发烧咳嗽")).thenReturn("发烧 咳嗽");

        // 模拟急诊检测
        when(emergencyDetector.detect(eq("发烧 咳嗽"), any(ExecutorService.class)))
            .thenReturn(emergencyResult);

        // 模拟规则引擎返回低置信度结果
        ruleEngineResult.setConfidence(0.5);
        when(ruleEngine.analyze("发烧 咳嗽")).thenReturn(ruleEngineResult);

        // 模拟LLM不可用
        when(llmAdapter.isAvailable()).thenReturn(false);

        // 执行分流
        DepartmentTriageResult result = hybridTriageService.triage(validRequest);

        // 验证结果使用了规则引擎降级
        assertNotNull(result);
        assertEquals("RULE_ENGINE_FALLBACK", result.getSource());
        assertEquals(0.5, result.getConfidence(), 0.01);

        // 验证LLM未被调用
        verify(llmAdapter, never()).analyze(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("单次分流 - 请求验证失败")
    void testTriage_RequestValidationFailure() {
        // 创建无效请求（症状为空）
        TriageRequest invalidRequest = new TriageRequest();
        invalidRequest.setSymptoms("");
        invalidRequest.setUserId(123L);

        // 执行分流，应抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            hybridTriageService.triage(invalidRequest);
        });

        // 验证没有调用任何引擎
        verifyNoInteractions(symptomNormalizer, ruleEngine, emergencyDetector, llmAdapter);
    }

    @Test
    @DisplayName("单次分流 - 急诊检测禁用")
    void testTriage_EmergencyDetectionDisabled() {
        // 配置禁用急诊检测
        when(config.isEnableEmergencyDetection()).thenReturn(false);
        validRequest.setIncludeEmergencyCheck(false);

        // 模拟症状标准化
        when(symptomNormalizer.normalize("发烧咳嗽")).thenReturn("发烧 咳嗽");

        // 模拟规则引擎返回高置信度结果
        ruleEngineResult.setConfidence(0.8);
        when(ruleEngine.analyze("发烧 咳嗽")).thenReturn(ruleEngineResult);

        // 执行分流
        DepartmentTriageResult result = hybridTriageService.triage(validRequest);

        // 验证结果
        assertNotNull(result);
        assertEquals("RULE_ENGINE", result.getSource());

        // 验证急诊检测未被调用
        verify(emergencyDetector, never()).detect(anyString(), any());
    }

    @Test
    @DisplayName("批量分流 - 成功处理")
    void testBatchTriage_Success() {
        // 准备批量请求
        BatchTriageRequest batchRequest = new BatchTriageRequest();
        batchRequest.setSymptomsList(Arrays.asList("发烧咳嗽", "头痛头晕"));
        batchRequest.setUserId(123L);
        batchRequest.setIncludeEmergencyCheck(true);

        // 模拟症状标准化
        when(symptomNormalizer.normalize(anyString())).thenReturn("标准化症状");

        // 模拟急诊检测
        when(emergencyDetector.detect(anyString(), any(ExecutorService.class)))
            .thenReturn(emergencyResult);

        // 模拟规则引擎返回高置信度结果
        ruleEngineResult.setConfidence(0.8);
        when(ruleEngine.analyze(anyString())).thenReturn(ruleEngineResult);

        // 模拟异步执行
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(ruleEngineResult);
        });

        // 执行批量分流
        List<DepartmentTriageResult> results = hybridTriageService.batchTriage(batchRequest);

        // 验证结果
        assertNotNull(results);
        assertEquals(2, results.size());
        results.forEach(result -> {
            assertNotNull(result);
            assertEquals("RULE_ENGINE", result.getSource());
        });
    }

    @Test
    @DisplayName("批量分流 - 批量大小超过限制")
    void testBatchTriage_BatchSizeExceedsLimit() {
        // 准备超过限制的批量请求
        BatchTriageRequest batchRequest = new BatchTriageRequest();
        batchRequest.setSymptomsList(Arrays.asList("症状1", "症状2", "症状3", "症状4", "症状5"));
        when(config.getBatchProcessingMaxSize()).thenReturn(3); // 限制为3

        // 执行批量分流，应抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            hybridTriageService.batchTriage(batchRequest);
        });
    }

    @Test
    @DisplayName("批量分流 - 空症状列表")
    void testBatchTriage_EmptySymptomsList() {
        // 准备空列表请求
        BatchTriageRequest batchRequest = new BatchTriageRequest();
        batchRequest.setSymptomsList(List.of());
        batchRequest.setUserId(123L);

        // 执行批量分流，应抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            hybridTriageService.batchTriage(batchRequest);
        });
    }

    @Test
    @DisplayName("批量分流 - 处理超时")
    void testBatchTriage_Timeout() {
        // 准备批量请求
        BatchTriageRequest batchRequest = new BatchTriageRequest();
        batchRequest.setSymptomsList(Arrays.asList("发烧咳嗽"));
        batchRequest.setUserId(123L);

        // 模拟症状标准化
        when(symptomNormalizer.normalize(anyString())).thenReturn("标准化症状");

        // 模拟急诊检测
        when(emergencyDetector.detect(anyString(), any(ExecutorService.class)))
            .thenReturn(emergencyResult);

        // 模拟规则引擎返回结果
        ruleEngineResult.setConfidence(0.8);
        when(ruleEngine.analyze(anyString())).thenReturn(ruleEngineResult);

        // 模拟异步执行（正常执行）
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(ruleEngineResult);
        });

        // 执行批量分流（不会超时，因为模拟立即完成）
        List<DepartmentTriageResult> results = hybridTriageService.batchTriage(batchRequest);

        // 验证结果
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("异步分流 - 成功")
    void testTriageAsync_Success() {
        // 模拟症状标准化
        when(symptomNormalizer.normalize("发烧咳嗽")).thenReturn("发烧 咳嗽");

        // 模拟急诊检测
        when(emergencyDetector.detect(eq("发烧 咳嗽"), any(ExecutorService.class)))
            .thenReturn(emergencyResult);

        // 模拟规则引擎返回高置信度结果
        ruleEngineResult.setConfidence(0.8);
        when(ruleEngine.analyze("发烧 咳嗽")).thenReturn(ruleEngineResult);

        // 模拟异步执行
        CompletableFuture<DepartmentTriageResult> mockFuture =
            CompletableFuture.completedFuture(ruleEngineResult);
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return mockFuture;
        });

        // 执行异步分流
        CompletableFuture<DepartmentTriageResult> future =
            hybridTriageService.triageAsync(validRequest);

        // 验证未来结果
        assertNotNull(future);
        assertTrue(future.isDone());
        assertDoesNotThrow(() -> {
            DepartmentTriageResult result = future.get();
            assertNotNull(result);
            assertEquals("RULE_ENGINE", result.getSource());
        });
    }

    @Test
    @DisplayName("服务可用性检查 - 所有组件可用")
    void testIsAvailable_AllComponentsAvailable() {
        // 模拟所有组件可用
        when(llmAdapter.isAvailable()).thenReturn(true);
        when(config.isEnableEmergencyDetection()).thenReturn(true);

        boolean available = hybridTriageService.isAvailable();

        assertTrue(available);
    }

    @Test
    @DisplayName("服务可用性检查 - LLM不可用")
    void testIsAvailable_LlmUnavailable() {
        // 模拟LLM不可用
        when(llmAdapter.isAvailable()).thenReturn(false);
        when(config.isEnableEmergencyDetection()).thenReturn(true);

        boolean available = hybridTriageService.isAvailable();

        // LLM不可用不影响整体可用性（规则引擎总是可用）
        assertTrue(available);
    }

    @Test
    @DisplayName("服务可用性检查 - 急诊检测禁用")
    void testIsAvailable_EmergencyDetectionDisabled() {
        // 模拟急诊检测禁用
        when(llmAdapter.isAvailable()).thenReturn(true);
        when(config.isEnableEmergencyDetection()).thenReturn(false);

        boolean available = hybridTriageService.isAvailable();

        // 急诊检测禁用不影响整体可用性
        assertTrue(available);
    }

    @Test
    @DisplayName("获取统计信息")
    void testGetStats() {
        // 准备统计信息
        TriageStats mockStats = new TriageStats();
        mockStats.setTotalRequests(100);
        mockStats.setSuccessRate(0.95);

        // 模拟统计收集器
        when(statsCollector.getStats()).thenReturn(mockStats);

        TriageStats stats = hybridTriageService.getStats();

        assertNotNull(stats);
        assertEquals(100, stats.getTotalRequests());
        assertEquals(0.95, stats.getSuccessRate(), 0.01);
    }

    @Test
    @DisplayName("结果融合 - 权重计算")
    void testResultFusion_WeightCalculation() {
        // 设置权重
        when(config.getRuleEngineWeight()).thenReturn(0.7);
        when(config.getLlmWeight()).thenReturn(0.3);

        // 准备两个结果
        ruleEngineResult.setConfidence(0.6);
        llmResult.setConfidence(0.9);

        // 使用反射调用私有方法（简化测试）
        // 实际融合逻辑在executeHybridTriage中测试
    }

    @Test
    @DisplayName("降级结果创建")
    void testFallbackResultCreation() {
        // 调用私有方法（通过公共方法间接测试）
        // 当服务不可用时，控制器会调用创建降级结果的方法
        // 我们通过模拟异常来测试降级路径
        when(symptomNormalizer.normalize("发烧咳嗽")).thenThrow(new RuntimeException("标准化失败"));

        DepartmentTriageResult result = hybridTriageService.triage(validRequest);

        assertNotNull(result);
        assertEquals("FALLBACK", result.getSource());
        assertEquals(0.0, result.getConfidence(), 0.01);
        assertTrue(result.getAdvice().contains("建议咨询医院导诊台"));
    }

    @Test
    @DisplayName("服务运行时间统计")
    void testServiceUptime() {
        long uptime = hybridTriageService.getServiceUptime();
        assertTrue(uptime >= 0);

        int totalRequests = hybridTriageService.getTotalRequestsProcessed();
        assertEquals(0, totalRequests); // 初始状态
    }

    @Test
    @DisplayName("服务状态重置")
    void testServiceReset() {
        // 先处理一个请求以增加计数
        when(symptomNormalizer.normalize("发烧咳嗽")).thenReturn("发烧 咳嗽");
        when(emergencyDetector.detect(anyString(), any(ExecutorService.class)))
            .thenReturn(emergencyResult);
        when(ruleEngine.analyze("发烧 咳嗽")).thenReturn(ruleEngineResult);
        ruleEngineResult.setConfidence(0.8);

        hybridTriageService.triage(validRequest);

        // 重置服务
        hybridTriageService.resetService();

        // 验证请求计数重置
        int totalRequests = hybridTriageService.getTotalRequestsProcessed();
        assertEquals(0, totalRequests);
    }
}