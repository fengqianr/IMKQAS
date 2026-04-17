package com.student.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.controller.qa.QaController;
import com.student.service.rag.QaService;
import com.student.service.rag.QaService.QaResponse;
import com.student.service.triage.TriageService;
import com.student.service.drug.DrugQueryService;
import com.student.dto.triage.TriageRequest;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.DepartmentRecommendation;
import com.student.model.triage.EmergencyCheckResult;
import com.student.entity.drug.Drug;
import com.student.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 问答控制器性能测试
 * 测试问答API接口的性能指标，包括响应时间、吞吐量和并发处理能力
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
public class QaControllerPerformanceTest extends BasePerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QaService qaService;

    @MockBean
    private TriageService triageService;

    @MockBean
    private DrugQueryService drugQueryService;

    // 安全组件模拟
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private Map<String, Object> validQaRequest;

    @BeforeEach
    void setUp() throws Exception {
        // 准备测试请求
        validQaRequest = new HashMap<>();
        validQaRequest.put("question", "高血压有什么症状？");
        validQaRequest.put("userId", 123L);
        validQaRequest.put("conversationId", 123L);

        // 模拟QaService响应
        List<String> retrievedContext = Arrays.asList("高血压是一种常见的慢性病", "症状包括头痛、眩晕、心悸等");
        QaResponse mockQaResponse = new QaResponse(
                "高血压有什么症状？",
                "高血压的常见症状包括头痛、眩晕、心悸、耳鸣等。严重时可能出现视力模糊、呼吸困难等症状。",
                retrievedContext,
                0.85,
                100L,
                "test-model"
        );

        when(qaService.answer(anyString(), anyLong(), anyLong()))
                .thenReturn(mockQaResponse);
    }

    /**
     * 问答API单次请求性能测试
     * 测试单个问答请求的响应时间
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testQaApiSingleRequestPerformance() throws Exception {
        assertExecutionTime(() -> {
            try {
                mockMvc.perform(post("/api/qa/ask")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validQaRequest)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.answer").exists())
                        .andExpect(jsonPath("$.confidence").exists());
            } catch (Exception e) {
                throw new RuntimeException("API请求失败", e);
            }
        }, 3000, "问答API单次请求响应时间");
    }

    /**
     * 问答API批量请求性能测试
     * 测试连续多个问答请求的吞吐量
     */
    @Test
    void testQaApiBatchRequestPerformance() throws Exception {
        String[] questions = {
                "高血压有什么症状？",
                "糖尿病如何治疗？",
                "冠心病有什么危险因素？",
                "发烧应该怎么办？",
                "咳嗽持续不好怎么处理？"
        };

        long totalTime = measureExecutionTime(() -> {
            for (String question : questions) {
                Map<String, Object> request = new HashMap<>(validQaRequest);
                request.put("question", question);

                try {
                    mockMvc.perform(post("/api/qa/ask")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    throw new RuntimeException("API请求失败: " + question, e);
                }
            }
        });

        double throughput = questions.length / (totalTime / 1000.0); // 请求/秒
        System.out.printf("批量API请求性能: 处理 %d 个请求, 总时间 %dms, 吞吐量 %.2f 请求/秒%n",
                questions.length, totalTime, throughput);

        // 断言吞吐量 > 1 请求/秒
        assertTrue(throughput > 1.0, () -> String.format("吞吐量过低: %.2f 请求/秒", throughput));
    }

    /**
     * 问答API错误请求性能测试
     * 测试无效请求的处理性能
     */
    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void testQaApiErrorRequestPerformance() throws Exception {
        // 无效请求：缺少必要字段
        Map<String, Object> invalidRequest = new HashMap<>();
        invalidRequest.put("question", ""); // 空问题

        assertExecutionTime(() -> {
            try {
                mockMvc.perform(post("/api/qa/ask")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                        .andExpect(status().isBadRequest());
            } catch (Exception e) {
                throw new RuntimeException("错误请求测试失败", e);
            }
        }, 1000, "错误请求处理响应时间");
    }

    /**
     * 问答API并发性能测试
     * 测试并发请求的处理能力
     */
    @Test
    void testQaApiConcurrentPerformance() {
        int concurrentThreads = 5;
        int iterations = 2; // 每个线程2次请求

        assertConcurrentPerformance(() -> {
            try {
                mockMvc.perform(post("/api/qa/ask")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validQaRequest)))
                        .andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException("并发请求失败", e);
            }
        }, concurrentThreads, iterations, 10000,  // 最大总时间10秒
                0.5,  // 最小吞吐量0.5请求/秒
                0.8); // 最小成功率80%
    }

    /**
     * 流式问答API性能测试（模拟）
     * 测试流式问答接口的响应性能
     * 注意：由于流式响应需要特殊处理，这里使用模拟测试
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testStreamingQaApiPerformance() throws Exception {
        // qaService.answer已经在setUp中模拟

        assertExecutionTime(() -> {
            try {
                mockMvc.perform(post("/api/qa/stream")
                                .param("query", "高血压有什么症状？")
                                .param("userId", "123")
                                .param("conversationId", "123"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
            } catch (Exception e) {
                throw new RuntimeException("流式API请求失败", e);
            }
        }, 5000, "流式问答API响应时间");
    }

    /**
     * 科室导诊API性能测试
     * 测试科室导诊接口的响应时间
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testTriageApiPerformance() throws Exception {
        // 准备科室导诊请求
        Map<String, Object> triageRequest = new HashMap<>();
        triageRequest.put("symptoms", "发烧咳嗽头痛");
        triageRequest.put("userId", 123);

        // 创建科室推荐列表
        DepartmentRecommendation recommendation = new DepartmentRecommendation();
        recommendation.setDepartmentId("respiratory");
        recommendation.setDepartmentName("呼吸内科");
        recommendation.setConfidence(0.85);
        recommendation.setReason("症状匹配呼吸系统疾病");
        recommendation.setEmergency(false);
        recommendation.setPriority(1);

        List<DepartmentRecommendation> recommendations = Arrays.asList(recommendation);

        // 创建分流结果
        DepartmentTriageResult mockTriageResult = new DepartmentTriageResult();
        mockTriageResult.setSymptoms("发烧咳嗽头痛");
        mockTriageResult.setRecommendations(recommendations);
        mockTriageResult.setConfidence(0.85);
        mockTriageResult.setSource("TEST");
        mockTriageResult.setProcessingTimeMs(100L);
        mockTriageResult.setAdvice("请及时就诊");

        // 模拟TriageService响应
        when(triageService.triage(any(TriageRequest.class)))
                .thenReturn(mockTriageResult);

        assertExecutionTime(() -> {
            try {
                mockMvc.perform(post("/api/qa/triage")
                                .param("symptoms", "发烧咳嗽头痛"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.recommendations[0].departmentName").value("呼吸内科"))
                        .andExpect(jsonPath("$.confidence").value(0.85));
            } catch (Exception e) {
                throw new RuntimeException("科室导诊API请求失败", e);
            }
        }, 2000, "科室导诊API响应时间");
    }

    /**
     * 药物查询API性能测试
     * 测试药物查询接口的响应时间
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDrugQueryApiPerformance() throws Exception {
        // 创建模拟药物数据
        Drug mockDrug = Drug.builder()
                .id(1L)
                .genericName("阿司匹林")
                .brandName("阿司匹林肠溶片")
                .indications("[\"用于缓解轻度或中度疼痛，如头痛、牙痛、神经痛等\"]")
                .dosage("一次1-2片，一日3次")
                .drugClass("解热镇痛药")
                .dosageForm("肠溶片")
                .specification("100mg")
                .manufacturer("某制药公司")
                .build();

        List<Drug> mockDrugList = Arrays.asList(mockDrug);

        // 模拟DrugQueryService响应
        when(drugQueryService.searchDrugsByName(anyString()))
                .thenReturn(mockDrugList);

        assertExecutionTime(() -> {
            try {
                mockMvc.perform(get("/api/qa/drug")
                                .param("name", "阿司匹林"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].genericName").value("阿司匹林"))
                        .andExpect(jsonPath("$[0].indications").exists())
                        .andExpect(jsonPath("$[0].dosage").exists());
            } catch (Exception e) {
                throw new RuntimeException("药物查询API请求失败", e);
            }
        }, 2000, "药物查询API响应时间");
    }
}