package com.student.service.document.impl;

import com.student.config.RagConfig;
import com.student.entity.Document;
import com.student.entity.DocumentChunk;
import com.student.service.dataBase.MilvusService;
import com.student.service.document.DocumentChunkService;
import com.student.service.document.DocumentService;
import com.student.service.rag.DocumentProcessorService;
import com.student.service.rag.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档处理服务实现类
 * 实现文档解析、分块、向量化和存储的完整流水线
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessorServiceImpl implements DocumentProcessorService {

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final RagConfig ragConfig;

    // 用于跟踪处理状态
    private final Map<Long, Document.Status> processingStatus = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public boolean processDocument(Long documentId) {
        log.info("开始处理文档: documentId={}", documentId);

        Document document = documentService.getById(documentId);
        if (document == null) {
            log.error("文档不存在: documentId={}", documentId);
            return false;
        }

        // 更新状态为处理中
        updateDocumentStatus(document, Document.Status.PROCESSING);
        processingStatus.put(documentId, Document.Status.PROCESSING);

        try {
            // 1. 文本提取
            String filePath = document.getFilePath();
            String fileExtension = getFileExtension(filePath);
            String fullText = extractText(filePath, fileExtension);
            if (fullText == null || fullText.trim().isEmpty()) {
                throw new RuntimeException("文本提取失败或内容为空");
            }

            // 2. 智能分块
            List<DocumentChunk> chunks = chunkText(fullText, documentId);
            if (chunks.isEmpty()) {
                throw new RuntimeException("文档分块失败，未生成任何分块");
            }

            // 3. 保存分块到数据库
            saveChunksToDatabase(chunks);
            log.info("文档分块保存完成: documentId={}, chunkCount={}", documentId, chunks.size());

            // 4. 向量化分块内容
            List<List<Float>> vectors = vectorizeChunks(chunks);
            if (vectors.size() != chunks.size()) {
                throw new RuntimeException("向量化失败，向量数量与分块数量不匹配");
            }

            // 5. 存储向量到Milvus
            List<String> vectorIds = storeVectorsToMilvus(vectors, chunks);
            if (vectorIds.size() != chunks.size()) {
                throw new RuntimeException("向量存储失败，向量ID数量与分块数量不匹配");
            }

            // 6. 更新分块向量ID
            updateChunksWithVectorIds(chunks, vectorIds);

            // 7. 更新文档状态
            document.incrementChunkCount(chunks.size());
            updateDocumentStatus(document, Document.Status.COMPLETED);
            processingStatus.put(documentId, Document.Status.COMPLETED);

            log.info("文档处理完成: documentId={}, chunkCount={}", documentId, chunks.size());
            return true;

        } catch (Exception e) {
            log.error("文档处理失败: documentId={}", documentId, e);
            updateDocumentStatus(document, Document.Status.FAILED);
            processingStatus.put(documentId, Document.Status.FAILED);
            return false;
        }
    }

    @Override
    public int processDocuments(List<Long> documentIds) {
        int successCount = 0;
        for (Long documentId : documentIds) {
            if (processDocument(documentId)) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public Document.Status getProcessingStatus(Long documentId) {
        return processingStatus.getOrDefault(documentId, documentService.getById(documentId).getStatus());
    }

    @Override
    public boolean cancelProcessing(Long documentId) {
        // 简单实现：更新状态为失败，表示已取消
        Document document = documentService.getById(documentId);
        if (document != null && document.getStatus() == Document.Status.PROCESSING) {
            updateDocumentStatus(document, Document.Status.FAILED);
            processingStatus.put(documentId, Document.Status.FAILED);
            log.info("文档处理已取消: documentId={}", documentId);
            return true;
        }
        return false;
    }

    @Override
    public boolean retryFailedDocument(Long documentId) {
        Document document = documentService.getById(documentId);
        if (document != null && document.getStatus() == Document.Status.FAILED) {
            // 清理旧的分块数据
            cleanupOldChunks(documentId);
            // 重新处理
            return processDocument(documentId);
        }
        return false;
    }

    @Override
    public Map<String, Object> getProcessingStats() {
        Map<String, Object> stats = new HashMap<>();
        List<Document> allDocuments = documentService.list();

        long total = allDocuments.size();
        long completed = allDocuments.stream().filter(d -> d.getStatus() == Document.Status.COMPLETED).count();
        long failed = allDocuments.stream().filter(d -> d.getStatus() == Document.Status.FAILED).count();
        long processing = allDocuments.stream().filter(d -> d.getStatus() == Document.Status.PROCESSING).count();
        long uploaded = allDocuments.stream().filter(d -> d.getStatus() == Document.Status.UPLOADED).count();

        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("processing", processing);
        stats.put("uploaded", uploaded);

        return stats;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 更新文档状态
     */
    private void updateDocumentStatus(Document document, Document.Status status) {
        document.updateStatus(status);
        documentService.updateById(document);
        log.debug("文档状态更新: documentId={}, status={}", document.getId(), status);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filePath) {
        if (filePath == null) {
            return "";
        }
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0) {
            return filePath.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 文本提取（待实现）
     */
    private String extractText(String filePath, String fileExtension) {
        // TODO: 根据文件类型调用不同的提取器
        // PDF -> PDFBox, DOCX -> Apache POI, TXT -> 直接读取
        log.warn("文本提取功能待实现: filePath={}, extension={}", filePath, fileExtension);
        // 模拟返回
        return "这是模拟的文档内容。医学文档通常包含疾病诊断、治疗方案、药物用量等信息。\n" +
                "例如：高血压患者应定期监测血压，遵医嘱服用降压药物。\n" +
                "常见降压药包括ACEI、ARB、CCB、利尿剂等。";
    }

    /**
     * 文本分块（待实现）
     */
    private List<DocumentChunk> chunkText(String text, Long documentId) {
        RagConfig.DocumentConfig.ChunkConfig chunkConfig = ragConfig.getDocument().getChunk();
        int chunkSize = chunkConfig.getSize();
        int overlap = chunkConfig.getOverlap();

        List<DocumentChunk> chunks = new ArrayList<>();
        // 简单实现：按字符数固定分块
        int length = text.length();
        int index = 0;
        int chunkIndex = 0;

        while (index < length) {
            int end = Math.min(index + chunkSize, length);
            String chunkContent = text.substring(index, end);

            DocumentChunk chunk = DocumentChunk.builder()
                    .documentId(documentId)
                    .chunkIndex(chunkIndex)
                    .content(chunkContent)
                    .metadata(String.format("{\"start_char\": %d, \"end_char\": %d}", index, end))
                    .build();

            chunks.add(chunk);
            chunkIndex++;
            index = end - overlap; // 应用重叠
        }

        log.debug("文本分块完成: documentId={}, chunkCount={}", documentId, chunks.size());
        return chunks;
    }

    /**
     * 保存分块到数据库
     */
    private void saveChunksToDatabase(List<DocumentChunk> chunks) {
        documentChunkService.saveBatch(chunks);
    }

    /**
     * 向量化分块内容
     */
    private List<List<Float>> vectorizeChunks(List<DocumentChunk> chunks) {
        List<String> texts = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            texts.add(chunk.getContent());
        }

        List<List<Float>> vectors = embeddingService.embedBatch(texts);
        if (vectors == null || vectors.size() != texts.size()) {
            throw new RuntimeException("向量化失败");
        }

        // 验证向量维度
        for (List<Float> vector : vectors) {
            if (!embeddingService.validateVector(vector)) {
                throw new RuntimeException("向量验证失败");
            }
        }

        return vectors;
    }

    /**
     * 存储向量到Milvus
     */
    private List<String> storeVectorsToMilvus(List<List<Float>> vectors, List<DocumentChunk> chunks) {
        List<String> vectorIds = new ArrayList<>();

        for (int i = 0; i < vectors.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            List<Float> vector = vectors.get(i);

            // 构建元数据JSON
            String metadata = String.format(
                "{\"document_id\": %d, \"chunk_index\": %d, \"chunk_identifier\": \"%s\"}",
                chunk.getDocumentId(), chunk.getChunkIndex(), chunk.getChunkIdentifier()
            );

            // 调用MilvusService插入单个向量
            Long vectorId = milvusService.insertVector(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getContent(),
                vector,
                metadata
            );

            if (vectorId == null) {
                throw new RuntimeException("向量存储到Milvus失败: chunkIndex=" + chunk.getChunkIndex());
            }

            vectorIds.add(vectorId.toString());
        }

        return vectorIds;
    }

    /**
     * 更新分块向量ID
     */
    private void updateChunksWithVectorIds(List<DocumentChunk> chunks, List<String> vectorIds) {
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            chunk.updateVectorId(vectorIds.get(i));
            documentChunkService.updateById(chunk);
        }
    }

    /**
     * 清理旧的分块数据
     */
    private void cleanupOldChunks(Long documentId) {
        // 删除已有的分块记录
        // 实现方式：根据document_id删除document_chunks表中的记录
        // 注意：这里需要实现DocumentChunkService中的删除方法
        log.info("清理旧分块数据: documentId={}", documentId);
        // TODO: 实现具体清理逻辑
    }
}