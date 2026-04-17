package com.student.controller.rag;

import com.student.service.rag.QaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RagController单元测试
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"spring.security.enabled=false"})
@org.springframework.test.context.ActiveProfiles("test")
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QaService qaService;

    @Test
    void testProcessDocument_Success() throws Exception {
        // 创建模拟文件
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test content".getBytes()
        );

        mockMvc.perform(multipart("/api/rag/process-document")
                        .file(file)
                        .param("title", "测试文档")
                        .param("category", "医疗指南")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(content().string("文档处理请求已接收，功能待实现"));
    }

    @Test
    void testProcessDocument_MissingFile() throws Exception {
        mockMvc.perform(multipart("/api/rag/process-document")
                        .param("title", "测试文档")
                        .param("category", "医疗指南")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isInternalServerError()); // 全局异常处理器返回500
    }

    @Test
    void testGetStats_Success() throws Exception {
        // 模拟统计信息 - 使用合理的测试值
        QaService.QaStats mockStats = new QaService.QaStats(
                100,    // totalQueries
                85,     // successfulQueries
                15,     // failedQueries
                250.5,  // averageProcessingTime
                0.85,   // successRate
                500,    // totalRetrievedDocuments
                12000   // totalGeneratedTokens
        );
        when(qaService.getStats()).thenReturn(mockStats);

        mockMvc.perform(get("/api/rag/stats"))
                .andExpect(status().isOk());
    }

    @Test
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/rag/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("RagController is healthy"));
    }
}