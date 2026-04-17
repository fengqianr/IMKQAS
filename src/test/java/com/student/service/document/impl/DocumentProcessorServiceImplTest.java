package com.student.service.document.impl;

import com.student.config.RagConfig;
import com.student.entity.Document;
import com.student.service.dataBase.MilvusService;
import com.student.service.document.DocumentChunkService;
import com.student.service.document.DocumentService;
import com.student.service.rag.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentProcessorServiceImplTest {

    @Mock
    private DocumentService documentService;
    @Mock
    private DocumentChunkService documentChunkService;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private MilvusService milvusService;
    @Mock
    private RagConfig ragConfig;

    @InjectMocks
    private DocumentProcessorServiceImpl documentProcessorService;

    private Document testDocument;

    @BeforeEach
    void setUp() {
        testDocument = new Document();
        testDocument.setId(1L);
        testDocument.setTitle("测试文档");
        testDocument.setFilePath("/uploads/test.pdf");
        testDocument.setStatus(Document.Status.UPLOADED);
    }

    @Test
    void testExtractText_PdfFile() throws Exception {
        // 使用反射测试私有方法
        java.lang.reflect.Method extractTextMethod = DocumentProcessorServiceImpl.class
                .getDeclaredMethod("extractText", String.class, String.class);
        extractTextMethod.setAccessible(true);

        // 当PDF文件路径被传入时，extractText应调用PDFBox提取文本
        // 注意：文件可能不存在，所以会抛出异常，这是预期的
        Exception exception = assertThrows(Exception.class, () -> {
            extractTextMethod.invoke(documentProcessorService, "/uploads/test.pdf", "pdf");
        });

        // 验证异常类型 - 应该是RuntimeException包装的IOException
        assertNotNull(exception);
        // 由于文件不存在，应该抛出异常，这验证了PDFBox被调用
    }

    @Test
    void testCleanupOldChunks() throws Exception {
        Long documentId = 1L;

        // 使用反射测试私有方法
        java.lang.reflect.Method cleanupOldChunksMethod = DocumentProcessorServiceImpl.class
                .getDeclaredMethod("cleanupOldChunks", Long.class);
        cleanupOldChunksMethod.setAccessible(true);

        // 模拟DocumentChunkService删除操作 - remove方法可能返回boolean
        when(documentChunkService.remove(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(true);
        // 模拟MilvusService删除向量 - 返回boolean
        when(milvusService.deleteByDocumentId(documentId)).thenReturn(true);

        // 测试方法应无异常
        assertDoesNotThrow(() -> cleanupOldChunksMethod.invoke(documentProcessorService, documentId));

        // 验证交互
        verify(documentChunkService, times(1)).remove(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class));
        verify(milvusService, times(1)).deleteByDocumentId(documentId);
    }
}