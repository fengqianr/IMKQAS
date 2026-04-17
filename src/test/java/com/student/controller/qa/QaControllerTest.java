package com.student.controller.qa;

import com.student.service.rag.QaService;
import com.student.service.triage.TriageService;
import com.student.service.drug.DrugQueryService;
import com.student.dto.triage.TriageRequest;
import com.student.model.triage.DepartmentTriageResult;
import com.student.entity.drug.Drug;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * QaController单元测试 - 简化版本
 * 主要验证API端点可访问，参数验证生效
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"spring.security.enabled=false"})
@org.springframework.test.context.ActiveProfiles("test")
class QaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QaService qaService;

    @MockBean
    private TriageService triageService;

    @MockBean
    private DrugQueryService drugQueryService;

    @Test
    void testTriage_Success() throws Exception {
        // 模拟一个简单的分流结果
        DepartmentTriageResult mockResult = new DepartmentTriageResult();
        when(triageService.triage(any(TriageRequest.class))).thenReturn(mockResult);

        mockMvc.perform(post("/api/qa/triage")
                        .param("symptoms", "头痛发烧")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());
    }

    @Test
    void testTriage_EmptySymptoms() throws Exception {
        mockMvc.perform(post("/api/qa/triage")
                        .param("symptoms", "")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSearchDrug_Success() throws Exception {
        List<Drug> drugList = Arrays.asList(
                Drug.builder()
                        .id(1L)
                        .genericName("阿司匹林")
                        .brandName("拜阿司匹灵")
                        .drugClass("非甾体抗炎药")
                        .build()
        );
        when(drugQueryService.searchDrugsByName("阿司匹林")).thenReturn(drugList);

        mockMvc.perform(get("/api/qa/drug")
                        .param("name", "阿司匹林")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testSearchDrug_EmptyName() throws Exception {
        mockMvc.perform(get("/api/qa/drug")
                        .param("name", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testStreamAnswer_Success() throws Exception {
        // 模拟问答响应
        QaService.QaResponse mockResponse = new QaService.QaResponse(
                "头痛怎么办",
                "建议休息，可服用止痛药",
                Arrays.asList("头痛常见原因有..."),
                0.9,
                1200,
                "qwen-turbo"
        );
        when(qaService.answer(anyString(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/qa/stream")
                        .param("query", "头痛怎么办")
                        .param("userId", "1")
                        .param("conversationId", "1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/qa/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("QaController is healthy"));
    }
}