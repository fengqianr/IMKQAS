package com.student.controller.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.dto.document.DocumentProcessResponse;
import com.student.entity.Document;
import com.student.service.rag.QaService;
import com.student.service.dataBase.MinioService;
import com.student.service.document.DocumentService;
import com.student.service.rag.DocumentProcessorService;
import com.student.filter.JwtAuthenticationFilter;
import com.student.utils.JwtUtil;
import com.student.service.common.impl.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RagController集成测试
 * 测试文档上传和处理API
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"spring.security.enabled=false"})
class RagControllerIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Primary
        @Bean
        public QaService qaService() {
            return Mockito.mock(QaService.class);
        }

        @Primary
        @Bean
        public MinioService minioService() {
            return Mockito.mock(MinioService.class);
        }

        @Primary
        @Bean
        public DocumentService documentService() {
            return Mockito.mock(DocumentService.class);
        }

        @Primary
        @Bean
        public DocumentProcessorService documentProcessorService() {
            return Mockito.mock(DocumentProcessorService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QaService qaService;

    @Autowired
    private MinioService minioService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentProcessorService documentProcessorService;

    // 安全组件模拟
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @org.junit.jupiter.api.AfterEach
    void resetMocks() {
        Mockito.reset(minioService, documentService, documentProcessorService, qaService);
    }

    @Test
    void testProcessDocument_ValidFile() throws Exception {
        // 设置模拟行为
        when(minioService.uploadFile(any(), any())).thenReturn("/test/path/document.pdf");
        when(documentService.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(123L); // 设置模拟ID
            return true;
        });
        when(documentProcessorService.processDocument(any(Long.class))).thenReturn(true);

        // 创建测试文件
        String content = "这是一个测试PDF内容。";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                content.getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/rag/process-document")
                        .file(file)
                        .param("title", "测试文档")
                        .param("category", "测试")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        DocumentProcessResponse response = objectMapper.readValue(
                responseContent, DocumentProcessResponse.class);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getMessage());
        assertNotNull(response.getDocumentId());
        assertEquals(123L, response.getDocumentId());

        // 验证模拟调用
        verify(minioService, times(1)).uploadFile(any(), any());
        verify(documentService, times(1)).save(any(Document.class));
        verify(documentProcessorService, timeout(5000).times(1)).processDocument(eq(123L));
    }

    @Test
    void testProcessDocument_EmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/rag/process-document")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());

        // 验证没有调用任何服务
        verifyNoInteractions(minioService);
        verifyNoInteractions(documentService);
        verifyNoInteractions(documentProcessorService);
    }

    @Test
    void testProcessDocument_MissingFile() throws Exception {
        mockMvc.perform(multipart("/api/rag/process-document")
                        .param("title", "测试文档")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isInternalServerError());

        // 验证没有调用任何服务
        verifyNoInteractions(minioService);
        verifyNoInteractions(documentService);
        verifyNoInteractions(documentProcessorService);
    }

    @Test
    void testProcessDocument_UnsupportedFileType() throws Exception {
        // 设置模拟行为
        when(minioService.uploadFile(any(), any())).thenReturn("/test/path/unsupported.exe");
        when(documentService.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(456L); // 设置模拟ID
            return true;
        });
        when(documentProcessorService.processDocument(any(Long.class))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.exe",
                "application/octet-stream",
                "binary content".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/rag/process-document")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk()) // 控制器可能返回成功，但处理会失败
                .andReturn();

        // 可选：验证响应
        String responseContent = result.getResponse().getContentAsString();
        DocumentProcessResponse response = objectMapper.readValue(
                responseContent, DocumentProcessResponse.class);
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(456L, response.getDocumentId());

        // 验证模拟调用
        verify(minioService, times(1)).uploadFile(any(), any());
        verify(documentService, times(1)).save(any(Document.class));
        verify(documentProcessorService, timeout(5000).times(1)).processDocument(eq(456L));
    }
}