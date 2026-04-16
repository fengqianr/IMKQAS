package com.student.controller.triage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.dto.triage.BatchTriageRequest;
import com.student.dto.triage.TriageRequest;
import com.student.model.triage.DepartmentRecommendation;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.EmergencyCheckResult;
import com.student.model.triage.EmergencyLevel;
import com.student.model.triage.TriageStats;
import com.student.service.triage.TriageService;
import com.student.filter.JwtAuthenticationFilter;
import com.student.utils.JwtUtil;
import com.student.service.UserDetailsServiceImpl;
import com.student.config.TestSecurityConfig;

/**
 * DepartmentTriageController 单元测试
 * 测试科室导诊控制器的REST API
 *
 * @author 系统生成
 * @version 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"spring.security.enabled=false"})
@DisplayName("科室导诊控制器测试")
class DepartmentTriageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TriageService triageService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    private ObjectMapper objectMapper;

    private TriageRequest validRequest;
    private DepartmentTriageResult successResult;

    @BeforeEach
    void setUp() {
        // 初始化ObjectMapper
        objectMapper = new ObjectMapper();

        // 准备有效的请求
        validRequest = new TriageRequest();
        validRequest.setSymptoms("发烧咳嗽");
        validRequest.setUserId(123L);
        validRequest.setIncludeEmergencyCheck(true);

        // 准备成功结果
        DepartmentRecommendation recommendation = new DepartmentRecommendation();
        recommendation.setDepartmentId("respiratory");
        recommendation.setDepartmentName("呼吸内科");
        recommendation.setConfidence(0.85);
        recommendation.setReason("症状匹配呼吸系统疾病");

        EmergencyCheckResult emergencyResult = new EmergencyCheckResult();
        emergencyResult.setEmergency(false);
        emergencyResult.setEmergencyLevel(EmergencyLevel.LOW);
        emergencyResult.setEmergencySymptoms(List.of());
        emergencyResult.setAdvice("未检测到急诊症状");

        successResult = new DepartmentTriageResult();
        successResult.setSymptoms("发烧咳嗽");
        successResult.setRecommendations(List.of(recommendation));
        successResult.setConfidence(0.85);
        successResult.setSource("RULE_ENGINE");
        successResult.setAdvice("建议就诊呼吸内科");
        successResult.setUserId(123L);
        successResult.setProcessingTimeMs(150L);
        successResult.setEmergencyCheck(emergencyResult);
    }

    @Test
    @DisplayName("单次分流 - 成功")
    void testTriage_Success() throws Exception {
        // 模拟服务返回成功结果
        when(triageService.isAvailable()).thenReturn(true);
        when(triageService.triage(any(TriageRequest.class))).thenReturn(successResult);

        // 执行请求并验证
        mockMvc.perform(post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symptoms").value("发烧咳嗽"))
                .andExpect(jsonPath("$.confidence").value(0.85))
                .andExpect(jsonPath("$.source").value("RULE_ENGINE"))
                .andExpect(jsonPath("$.userId").value(123))
                .andExpect(jsonPath("$.processingTimeMs").value(150L));
    }

    @Test
    @DisplayName("单次分流 - 服务不可用")
    void testTriage_ServiceUnavailable() throws Exception {
        // 模拟服务不可用
        when(triageService.isAvailable()).thenReturn(false);

        mockMvc.perform(post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.source").value("FALLBACK"))
                .andExpect(jsonPath("$.confidence").value(0.0));

        verify(triageService).isAvailable();
    }

    @Test
    @DisplayName("单次分流 - 无效请求参数")
    void testTriage_InvalidRequest() throws Exception {
        // 创建无效请求（症状为空，触发@NotBlank验证）
        TriageRequest invalidRequest = new TriageRequest();
        invalidRequest.setSymptoms("");
        invalidRequest.setUserId(123L);

        // 注意：当@Valid验证失败时，控制器方法不会被执行
        // 验证由Spring框架处理，返回全局错误响应
        // 不需要模拟服务调用，因为控制器方法不会被执行

        mockMvc.perform(post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("症状描述不能为空"))
                .andExpect(jsonPath("$.code").value("20000"));
    }

    @Test
    @DisplayName("单次分流 - 服务器内部错误")
    void testTriage_InternalError() throws Exception {
        // 模拟服务抛出运行时异常
        when(triageService.isAvailable()).thenReturn(true);
        when(triageService.triage(any(TriageRequest.class)))
                .thenThrow(new RuntimeException("数据库连接失败"));

        mockMvc.perform(post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.source").value("FALLBACK"))
                .andExpect(jsonPath("$.advice").value("分流处理失败: 服务器内部错误"));
    }

    @Test
    @DisplayName("批量分流 - 成功")
    void testBatchTriage_Success() throws Exception {
        // 准备批量请求
        BatchTriageRequest batchRequest = new BatchTriageRequest();
        batchRequest.setSymptomsList(Arrays.asList("发烧咳嗽", "头痛头晕"));
        batchRequest.setUserId(123L);
        batchRequest.setIncludeEmergencyCheck(true);

        // 准备批量结果
        List<DepartmentTriageResult> batchResults = Arrays.asList(successResult, successResult);

        // 模拟服务
        when(triageService.isAvailable()).thenReturn(true);
        when(triageService.batchTriage(any(BatchTriageRequest.class))).thenReturn(batchResults);

        // 执行请求
        mockMvc.perform(post("/api/triage/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symptoms").value("发烧咳嗽"))
                .andExpect(jsonPath("$[1].symptoms").value("发烧咳嗽"));
    }

    @Test
    @DisplayName("批量分流 - 服务不可用")
    void testBatchTriage_ServiceUnavailable() throws Exception {
        BatchTriageRequest batchRequest = new BatchTriageRequest();
        batchRequest.setSymptomsList(Arrays.asList("发烧咳嗽"));
        batchRequest.setUserId(123L);

        when(triageService.isAvailable()).thenReturn(false);

        mockMvc.perform(post("/api/triage/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source").value("FALLBACK"));
    }

    @Test
    @DisplayName("获取统计信息 - 成功")
    void testGetStats_Success() throws Exception {
        // 准备统计信息
        TriageStats stats = new TriageStats();
        stats.setTotalRequests(100);
        stats.setSuccessfulRequests(95);
        stats.setFailedRequests(5);
        stats.setSuccessRate(0.95);
        stats.setAvgProcessingTime(150.0);

        // 模拟服务
        when(triageService.getStats()).thenReturn(stats);

        // 执行请求
        mockMvc.perform(get("/api/triage/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.successRate").value(0.95))
                .andExpect(jsonPath("$.avgProcessingTime").value(150.0));
    }

    @Test
    @DisplayName("获取统计信息 - 服务器错误")
    void testGetStats_ServerError() throws Exception {
        // 模拟服务抛出异常
        when(triageService.getStats()).thenThrow(new RuntimeException("统计数据库错误"));

        mockMvc.perform(get("/api/triage/stats"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("健康检查 - 服务健康")
    void testHealthCheck_Healthy() throws Exception {
        // 准备统计信息
        TriageStats stats = new TriageStats();
        stats.setTotalRequests(100);
        stats.setSuccessRate(0.95);
        stats.setAvgProcessingTime(150.0);

        // 模拟服务
        when(triageService.isAvailable()).thenReturn(true);
        when(triageService.getStats()).thenReturn(stats);

        // 执行请求
        mockMvc.perform(get("/api/triage/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAvailable").value(true))
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.successRate").value(0.95));
    }

    @Test
    @DisplayName("健康检查 - 服务不可用")
    void testHealthCheck_Unhealthy() throws Exception {
        // 准备统计信息
        TriageStats stats = new TriageStats();
        stats.setTotalRequests(100);
        stats.setSuccessRate(0.95);
        stats.setAvgProcessingTime(150.0);

        // 模拟服务不可用
        when(triageService.isAvailable()).thenReturn(false);
        when(triageService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/triage/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.serviceAvailable").value(false));
    }

    @Test
    @DisplayName("请求参数验证 - 症状描述过长")
    void testRequestValidation_SymptomsTooLong() throws Exception {
        // 创建症状描述过长的请求
        TriageRequest longSymptomsRequest = new TriageRequest();
        longSymptomsRequest.setSymptoms("a".repeat(600)); // 超过500字符限制
        longSymptomsRequest.setUserId(123L);

        // 由于@Valid注解会触发验证，这里我们期望400错误
        mockMvc.perform(post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(longSymptomsRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("请求参数验证 - 缺失症状描述")
    void testRequestValidation_MissingSymptoms() throws Exception {
        // 创建缺少症状描述的请求
        TriageRequest missingSymptomsRequest = new TriageRequest();
        missingSymptomsRequest.setUserId(123L);
        // symptoms字段为null

        mockMvc.perform(post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(missingSymptomsRequest)))
                .andExpect(status().isBadRequest());
    }
}