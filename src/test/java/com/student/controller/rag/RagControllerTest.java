package com.student.controller.rag;

import com.student.dto.document.DocumentProcessResponse;
import com.student.entity.Document;
import com.student.service.document.DocumentService;
import com.student.service.dataBase.MinioService;
import com.student.service.rag.DocumentProcessorService;
import com.student.service.rag.QaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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

    @MockBean
    private MinioService minioService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private DocumentProcessorService documentProcessorService;

    @MockBean
    private com.student.filter.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.student.utils.JwtUtil jwtUtil;

    @MockBean
    private com.student.service.common.impl.UserDetailsServiceImpl userDetailsService;

    @Test
    void testProcessDocument_Success() throws Exception {
        // 模拟文件上传
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "PDF content".getBytes());

        // 模拟MinIO上传
        when(minioService.uploadFile(any(), any())).thenReturn("/uploads/test.pdf");

        // 模拟文档保存
        Document document = Document.builder()
                .id(1L)
                .title("test.pdf")
                .status(Document.Status.UPLOADED)
                .build();
        when(documentService.save(any())).thenReturn(true);

        // 执行请求
        mockMvc.perform(multipart("/api/rag/process-document")
                .file(file)
                .param("title", "测试文档")
                .param("category", "医学指南"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.documentId").value(1L));

        // 验证异步处理被调用
        verify(documentProcessorService, timeout(5000)).processDocument(1L);
    }

    @Test
    void testProcessDocument_MissingFile() throws Exception {
        // 创建空文件
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                new byte[0]);

        mockMvc.perform(multipart("/api/rag/process-document")
                        .file(file)
                        .param("title", "测试文档")
                        .param("category", "医疗指南")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("文件不能为空"));
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