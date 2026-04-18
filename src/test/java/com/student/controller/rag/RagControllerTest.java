package com.student.controller.rag;

import com.student.controller.rag.RagController;
import com.student.dto.document.DocumentProcessResponse;
import com.student.entity.Document;
import com.student.service.document.DocumentService;
import com.student.service.dataBase.MinioService;
import com.student.service.rag.DocumentProcessorService;
import com.student.service.rag.QaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RagController单元测试
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class RagControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private RagController ragController;

    @Mock
    private QaService qaService;

    @Mock
    private MinioService minioService;

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentProcessorService documentProcessorService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ragController).build();
    }

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
        when(documentService.save(any(Document.class))).thenAnswer(invocation -> {
            Document savedDocument = invocation.getArgument(0);
            savedDocument.setId(1L);
            savedDocument.setTitle("test.pdf");
            savedDocument.setStatus(Document.Status.UPLOADED);
            return true;
        });

        // 执行请求
        mockMvc.perform(multipart("/api/rag/process-document")
                .file(file)
                .param("title", "测试文档")
                .param("category", "医学指南")
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.documentId").value(1L));

        // 验证异步处理被调用
        verify(documentProcessorService, timeout(1000)).processDocument(1L);
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
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON))
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

        mockMvc.perform(get("/api/rag/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/rag/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("RagController is healthy"));
    }
}