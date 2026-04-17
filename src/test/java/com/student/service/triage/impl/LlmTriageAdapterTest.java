package com.student.service.triage.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
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

import com.student.model.triage.DepartmentRecommendation;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.EmergencyLevel;
import com.student.service.LlmService;
import com.student.service.triage.config.TriageConfig;

/**
 * LlmTriageAdapter 单元测试
 * 测试LLM分流适配器的核心功能，包括提示词构建、响应解析和结果转换
 *
 * @author 系统生成
 * @version 1.0
 */
@DisplayName("LLM分流适配器测试")
class LlmTriageAdapterTest extends BaseMockitoTest {

    @Mock
    private LlmService llmService;

    @Mock
    private TriageConfig config;

    @Mock
    private ExecutorService executorService;

    private LlmTriageAdapter llmTriageAdapter;

    @BeforeEach
    void setUp() {
        // 初始化适配器
        llmTriageAdapter = new LlmTriageAdapter(llmService, config, executorService);

        // 默认配置
        when(config.getLlmTriagePromptTemplate()).thenReturn("""
            你是一个医疗分诊专家。请根据以下症状描述，推荐最合适的就诊科室。
            症状：{symptoms}

            请按以下格式回复：
            1. 主要推荐科室：[科室名称]
            2. 置信度：[0.0-1.0之间的浮点数]
            3. 理由：[简要说明推荐理由]
            4. 备选科室：[科室1, 科室2...]（可选）
            5. 紧急程度：[CRITICAL/HIGH/MEDIUM/LOW]（可选）
            """);
        when(config.getLlmTimeout()).thenReturn(3000);

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
    @DisplayName("LLM分析 - 成功解析标准响应")
    void testAnalyze_SuccessfulParsing() {
        // 模拟LLM服务可用
        when(llmService.isAvailable()).thenReturn(true);

        // 模拟LLM返回标准格式响应
        String llmResponse = """
            1. 主要推荐科室：呼吸内科
            2. 置信度：0.85
            3. 理由：症状符合上呼吸道感染表现
            4. 备选科室：发热门诊, 全科医学科
            5. 紧急程度：LOW
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        // 执行LLM分析
        DepartmentTriageResult result = llmTriageAdapter.analyze("发烧咳嗽");

        // 验证结果
        assertNotNull(result);
        assertEquals("呼吸内科", result.getRecommendations().get(0).getDepartmentName());
        assertEquals(0.85, result.getConfidence(), 0.01);
        assertEquals("LLM", result.getSource());
        assertEquals("发烧咳嗽", result.getSymptoms());
        assertTrue(result.getAdvice().contains("呼吸内科"));
        assertFalse(result.getRecommendations().isEmpty());

        // 验证备选科室被解析
        assertEquals(3, result.getRecommendations().size()); // 主要 + 2个备选
    }

    @Test
    @DisplayName("LLM分析 - LLM服务不可用")
    void testAnalyze_LlmServiceUnavailable() {
        // 模拟LLM服务不可用
        when(llmService.isAvailable()).thenReturn(false);

        DepartmentTriageResult result = llmTriageAdapter.analyze("发烧咳嗽");

        assertNull(result); // 服务不可用返回null
    }

    @Test
    @DisplayName("LLM分析 - LLM返回空响应")
    void testAnalyze_EmptyResponse() {
        // 模拟LLM服务可用但返回空响应
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn("");

        DepartmentTriageResult result = llmTriageAdapter.analyze("发烧咳嗽");

        assertNotNull(result);
        assertEquals("LLM_FALLBACK", result.getSource());
        assertEquals(0.0, result.getConfidence(), 0.01);
        assertTrue(result.getAdvice().contains("LLM返回空响应"));
    }

    @Test
    @DisplayName("LLM分析 - 响应中缺少科室推荐")
    void testAnalyze_MissingDepartmentInResponse() {
        // 模拟LLM返回不完整响应（缺少科室推荐）
        when(llmService.isAvailable()).thenReturn(true);
        String llmResponse = """
            2. 置信度：0.85
            3. 理由：症状符合上呼吸道感染表现
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        DepartmentTriageResult result = llmTriageAdapter.analyze("发烧咳嗽");

        assertNotNull(result);
        assertEquals("LLM_FALLBACK", result.getSource());
        assertTrue(result.getAdvice().contains("LLM响应中未找到科室推荐"));
    }

    @Test
    @DisplayName("LLM分析 - 异步执行")
    void testAnalyze_AsyncExecution() throws Exception {
        // 模拟LLM服务可用
        when(llmService.isAvailable()).thenReturn(true);

        // 模拟LLM返回标准响应
        String llmResponse = """
            1. 主要推荐科室：呼吸内科
            2. 置信度：0.85
            3. 理由：症状符合上呼吸道感染表现
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        // 模拟异步执行
        CompletableFuture<DepartmentTriageResult> mockFuture =
            CompletableFuture.completedFuture(createSuccessResult());
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return mockFuture;
        });

        // 执行异步分析
        DepartmentTriageResult result = llmTriageAdapter.analyze("发烧咳嗽", true);

        assertNotNull(result);
        assertEquals("呼吸内科", result.getRecommendations().get(0).getDepartmentName());
    }

    @Test
    @DisplayName("LLM分析 - 异步执行超时")
    void testAnalyze_AsyncTimeout() {
        // 模拟LLM服务可用
        when(llmService.isAvailable()).thenReturn(true);

        // 覆盖默认模拟，让execute什么都不做，模拟超时
        doNothing().when(executorService).execute(any(Runnable.class));

        // 覆盖submit模拟，返回未完成的future
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            // 不执行任务，返回未完成的future
            return new CompletableFuture<>();
        });

        when(executorService.submit(any(Callable.class))).thenAnswer(invocation -> {
            // 不执行任务，返回未完成的future
            return new CompletableFuture<>();
        });

        // 执行异步分析（应返回null）
        DepartmentTriageResult result = llmTriageAdapter.analyze("发烧咳嗽", true);

        assertNull(result);
    }

    @Test
    @DisplayName("响应解析 - 标准格式")
    void testResponseParsing_StandardFormat() {
        // 模拟LLM返回标准格式
        when(llmService.isAvailable()).thenReturn(true);
        String llmResponse = """
            1. 主要推荐科室：神经内科
            2. 置信度：0.92
            3. 理由：症状符合神经系统疾病表现
            4. 备选科室：神经外科, 康复医学科
            5. 紧急程度：MEDIUM
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        DepartmentTriageResult result = llmTriageAdapter.analyze("头痛头晕");

        assertNotNull(result);
        assertEquals("神经内科", result.getRecommendations().get(0).getDepartmentName());
        assertEquals(0.92, result.getConfidence(), 0.01);
        assertEquals(3, result.getRecommendations().size()); // 主要 + 2备选
        // 紧急程度应为MEDIUM，但LLM结果中不设置EmergencyCheck，仅用于建议
        assertTrue(result.getAdvice().contains("神经内科"));
    }

    @Test
    @DisplayName("响应解析 - 备选格式")
    void testResponseParsing_AlternativeFormat() {
        // 模拟LLM返回备选格式（使用中文冒号）
        when(llmService.isAvailable()).thenReturn(true);
        String llmResponse = """
            主要推荐科室：骨科
            置信度：0.78
            理由：症状符合骨折表现
            备选科室：创伤外科，康复科
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        DepartmentTriageResult result = llmTriageAdapter.analyze("手臂疼痛肿胀");

        assertNotNull(result);
        assertEquals("骨科", result.getRecommendations().get(0).getDepartmentName());
        assertEquals(0.78, result.getConfidence(), 0.01);
    }

    @Test
    @DisplayName("响应解析 - 置信度超出范围")
    void testResponseParsing_ConfidenceOutOfRange() {
        // 模拟LLM返回超出范围的置信度
        when(llmService.isAvailable()).thenReturn(true);
        String llmResponse = """
            主要推荐科室：心内科
            置信度：1.5
            理由：症状符合心脏病表现
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        DepartmentTriageResult result = llmTriageAdapter.analyze("胸痛");

        assertNotNull(result);
        // 置信度应被限制在1.0
        assertEquals(1.0, result.getConfidence(), 0.01);
    }

    @Test
    @DisplayName("响应解析 - 缺失置信度使用默认值")
    void testResponseParsing_MissingConfidenceUsesDefault() {
        // 模拟LLM返回缺少置信度
        when(llmService.isAvailable()).thenReturn(true);
        String llmResponse = """
            主要推荐科室：皮肤科
            理由：症状符合皮肤病表现
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        DepartmentTriageResult result = llmTriageAdapter.analyze("皮疹瘙痒");

        assertNotNull(result);
        // 默认置信度0.7
        assertEquals(0.7, result.getConfidence(), 0.01);
    }

    @Test
    @DisplayName("响应解析 - 紧急程度解析")
    void testResponseParsing_EmergencyLevel() {
        // 模拟LLM返回不同紧急程度
        when(llmService.isAvailable()).thenReturn(true);
        String llmResponse = """
            主要推荐科室：急诊科
            置信度：0.95
            理由：症状危重需要紧急处理
            紧急程度：CRITICAL
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        DepartmentTriageResult result = llmTriageAdapter.analyze("胸痛呼吸困难意识模糊");

        assertNotNull(result);
        assertTrue(result.getAdvice().contains("注意：检测到危急"));
    }

    @Test
    @DisplayName("批量分析 - 成功")
    void testBatchAnalyze_Success() {
        // 模拟LLM服务可用
        when(llmService.isAvailable()).thenReturn(true);

        // 模拟LLM返回标准响应
        String llmResponse = """
            主要推荐科室：呼吸内科
            置信度：0.85
            理由：症状符合上呼吸道感染表现
            """;
        when(llmService.generateAnswer(anyString(), anyList())).thenReturn(llmResponse);

        // 模拟异步执行
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(createSuccessResult());
        });

        List<String> symptomsList = List.of("发烧咳嗽", "头痛头晕");
        List<DepartmentTriageResult> results = llmTriageAdapter.batchAnalyze(symptomsList);

        assertNotNull(results);
        assertEquals(2, results.size());
        results.forEach(result -> {
            assertNotNull(result);
            assertEquals("LLM", result.getSource());
        });
    }

    @Test
    @DisplayName("批量分析 - 部分失败")
    void testBatchAnalyze_PartialFailure() {
        // 模拟LLM服务可用
        when(llmService.isAvailable()).thenReturn(true);

        // 第一次调用成功，第二次调用返回null
        when(llmService.generateAnswer(anyString(), anyList()))
            .thenReturn("主要推荐科室：呼吸内科\n置信度：0.85")
            .thenReturn(null);

        // 模拟异步执行
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            DepartmentTriageResult result = createSuccessResult();
            if (invocation.getArgument(0, Runnable.class).toString().contains("第二次")) {
                result = null;
            }
            return CompletableFuture.completedFuture(result);
        });

        List<String> symptomsList = List.of("发烧咳嗽", "头痛头晕");
        List<DepartmentTriageResult> results = llmTriageAdapter.batchAnalyze(symptomsList);

        assertNotNull(results);
        assertEquals(2, results.size()); // 包含成功和降级结果
    }

    @Test
    @DisplayName("服务可用性检查")
    void testIsAvailable() {
        when(llmService.isAvailable()).thenReturn(true);
        assertTrue(llmTriageAdapter.isAvailable());

        when(llmService.isAvailable()).thenReturn(false);
        assertFalse(llmTriageAdapter.isAvailable());

        when(llmService.isAvailable()).thenReturn(true);
        LlmTriageAdapter adapterWithoutLlmService = new LlmTriageAdapter(null, config, executorService);
        assertFalse(adapterWithoutLlmService.isAvailable());
    }

    @Test
    @DisplayName("获取LLM服务信息")
    void testGetLlmServiceInfo() {
        when(llmService.getModelInfo()).thenReturn(new LlmService.ModelInfo("GPT-4", "OpenAI", 4096, true));
        String info = llmTriageAdapter.getLlmServiceInfo();
        assertEquals("GPT-4", info);

        LlmTriageAdapter adapterWithoutLlmService = new LlmTriageAdapter(null, config, executorService);
        assertEquals("LLM服务未配置", adapterWithoutLlmService.getLlmServiceInfo());
    }

    @Test
    @DisplayName("清除缓存")
    void testClearCache() {
        // 清除缓存方法目前只是日志记录，验证不抛出异常
        assertDoesNotThrow(() -> llmTriageAdapter.clearCache());
    }

    @Test
    @DisplayName("提示词构建")
    void testPromptConstruction() {
        // 模拟配置
        String template = "症状：{symptoms}\n请推荐科室。";
        when(config.getLlmTriagePromptTemplate()).thenReturn(template);

        // 调用私有方法通过公共方法间接测试
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.generateAnswer(contains("症状：发烧咳嗽"), anyList()))
            .thenReturn("主要推荐科室：呼吸内科");

        DepartmentTriageResult result = llmTriageAdapter.analyze("发烧咳嗽");

        assertNotNull(result);
        verify(llmService).generateAnswer(contains("症状：发烧咳嗽"), anyList());
    }

    private DepartmentTriageResult createSuccessResult() {
        DepartmentRecommendation recommendation = new DepartmentRecommendation();
        recommendation.setDepartmentId("respiratory");
        recommendation.setDepartmentName("呼吸内科");
        recommendation.setConfidence(0.85);
        recommendation.setReason("症状符合上呼吸道感染表现");

        DepartmentTriageResult result = new DepartmentTriageResult();
        result.setSymptoms("发烧咳嗽");
        result.setRecommendations(List.of(recommendation));
        result.setConfidence(0.85);
        result.setSource("LLM");
        result.setAdvice("基于AI分析，建议就诊呼吸内科（匹配度良好）。症状符合上呼吸道感染表现。");
        return result;
    }
}