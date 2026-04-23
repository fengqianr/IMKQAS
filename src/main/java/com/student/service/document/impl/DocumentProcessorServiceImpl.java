package com.student.service.document.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.student.config.RagConfig;
import com.student.entity.Document;
import com.student.entity.DocumentChunk;
import com.student.service.dataBase.MilvusService;
import com.student.service.dataBase.MinioService;
import com.student.service.document.DocumentChunkService;
import com.student.service.document.DocumentService;
import com.student.service.rag.DocumentProcessorService;
import com.student.service.rag.EmbeddingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final MinioService minioService;

    // 用于跟踪处理状态
    private final Map<Long, Document.Status> processingStatus = new ConcurrentHashMap<>();

    @Override
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

        // 清理旧的分块数据（支持重新处理场景）
        cleanupOldChunks(documentId);

        try {
            // 1. 文本提取
            String filePath = document.getFilePath();
            String fileExtension = getFileExtension(filePath);
            log.info("文档处理步骤1-文本提取开始: documentId={}", documentId);
            String fullText = extractText(filePath, fileExtension);
            log.info("文档处理步骤1-文本提取完成: documentId={}, textLength={}", documentId, fullText != null ? fullText.length() : 0);
            if (fullText == null || fullText.trim().isEmpty()) {
                throw new RuntimeException("文本提取失败或内容为空");
            }

            // 2. 智能分块
            log.info("文档处理步骤2-分块开始: documentId={}", documentId);
            List<DocumentChunk> chunks = chunkText(fullText, documentId);
            log.info("文档处理步骤2-分块完成: documentId={}, chunkCount={}", documentId, chunks.size());
            if (chunks.isEmpty()) {
                throw new RuntimeException("文档分块失败，未生成任何分块");
            }

            // 3. 保存分块到数据库
            log.info("文档处理步骤3-保存分块开始: documentId={}", documentId);
            saveChunksToDatabase(chunks);
            log.info("文档处理步骤3-保存分块完成: documentId={}, chunkCount={}", documentId, chunks.size());

            // 4. 向量化分块内容
            log.info("文档处理步骤4-向量化开始: documentId={}, chunkCount={}", documentId, chunks.size());
            List<List<Float>> vectors = vectorizeChunks(chunks);
            log.info("文档处理步骤4-向量化完成: documentId={}, vectorCount={}", documentId, vectors.size());
            if (vectors.size() != chunks.size()) {
                throw new RuntimeException("向量化失败，向量数量与分块数量不匹配");
            }

            // 5. 存储向量到Milvus
            log.info("文档处理步骤5-存储向量到Milvus开始: documentId={}", documentId);
            List<String> vectorIds = storeVectorsToMilvus(vectors, chunks);
            log.info("文档处理步骤5-存储向量到Milvus完成: documentId={}", documentId);
            if (vectorIds.size() != chunks.size()) {
                throw new RuntimeException("向量存储失败，向量ID数量与分块数量不匹配");
            }

            // 6. 更新分块向量ID
            log.info("文档处理步骤6-更新分块向量ID开始: documentId={}", documentId);
            updateChunksWithVectorIds(chunks, vectorIds);
            log.info("文档处理步骤6-更新分块向量ID完成: documentId={}", documentId);

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
        } finally {
            // 处理完成后移除状态映射，防止内存泄漏
            processingStatus.remove(documentId);
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
     * 处理包含查询参数的URL（如MinIO预签名URL）
     */
    private String getFileExtension(String filePath) {
        if (filePath == null) {
            return "";
        }

        // 如果包含查询参数（问号），先去除查询参数部分
        String pathWithoutQuery = filePath;
        int queryIndex = filePath.indexOf('?');
        if (queryIndex > 0) {
            pathWithoutQuery = filePath.substring(0, queryIndex);
        }

        int lastDot = pathWithoutQuery.lastIndexOf('.');
        if (lastDot > 0) {
            return pathWithoutQuery.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 解析文件路径：如果是MinIO URL，则下载到临时文件并返回临时文件路径
     */
    private String resolveFilePath(String filePath) throws IOException {
        if (filePath == null) {
            return null;
        }

        // 检查是否是MinIO预签名URL（包含x-amz-algorithm参数）
        if (filePath.contains("x-amz-algorithm") || filePath.contains("?") || filePath.contains("://")) {
            log.info("检测到URL格式的文件路径，尝试从MinIO下载: {}", filePath.length() > 100 ? filePath.substring(0, 100) + "..." : filePath);

            // 从URL中提取对象名称
            String objectName = extractObjectNameFromUrl(filePath);
            if (objectName == null || objectName.isEmpty()) {
                throw new IOException("无法从URL提取对象名称: " + filePath);
            }

            log.info("从URL提取的对象名称: {}", objectName);

            // 下载文件到临时目录
            try (InputStream inputStream = minioService.downloadFile(objectName)) {
                // 创建临时文件
                String tempDir = System.getProperty("java.io.tmpdir");
                String tempFileName = "minio_download_" + System.currentTimeMillis() + "_" +
                                     objectName.replaceAll("[^a-zA-Z0-9.-]", "_");
                File tempFile = new File(tempDir, tempFileName);

                // 将输入流写入临时文件
                Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                log.info("文件下载到临时位置: {}", tempFile.getAbsolutePath());
                return tempFile.getAbsolutePath();
            }
        }

        // 如果是普通文件路径，直接返回
        return filePath;
    }

    /**
     * 从MinIO URL中提取对象名称
     */
    private String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            // 解析URL
            URI uri = new URI(url);
            String path = uri.getPath();

            // 去除开头的斜杠和bucket名称部分
            // URL格式: http://minio.example.com/bucket/objectName?params
            if (path != null && path.startsWith("/")) {
                path = path.substring(1); // 去除开头的斜杠

                // 找到第一个斜杠后的部分（bucket名称之后的部分）
                int firstSlash = path.indexOf('/');
                if (firstSlash > 0) {
                    // 返回bucket名称之后的部分（对象名称）
                    String objectName = path.substring(firstSlash + 1);
                    // URL解码对象名称
                    try {
                        return URLDecoder.decode(objectName, StandardCharsets.UTF_8.name());
                    } catch (Exception e) {
                        log.warn("URL解码失败: {}, 使用原始名称", objectName, e);
                        return objectName;
                    }
                }
            }

            // 如果无法解析，尝试从查询参数前的部分提取
            // 格式可能是: documents/timestamp/file.docx?params
            int queryIndex = url.indexOf('?');
            String pathPart = queryIndex > 0 ? url.substring(0, queryIndex) : url;

            // 检查是否包含协议
            int protocolIndex = pathPart.indexOf("://");
            if (protocolIndex > 0) {
                pathPart = pathPart.substring(protocolIndex + 3); // 去除协议部分
                // 去除主机名部分
                int hostEndIndex = pathPart.indexOf('/');
                if (hostEndIndex > 0) {
                    pathPart = pathPart.substring(hostEndIndex + 1); // 去除主机名和斜杠
                }
            }

            // 去除开头的斜杠（如果有）
            while (pathPart.startsWith("/")) {
                pathPart = pathPart.substring(1);
            }

            // 如果路径以bucket名称开头，去除bucket名称
            // 假设bucket名称是路径的第一部分
            int firstSlash = pathPart.indexOf('/');
            if (firstSlash > 0) {
                pathPart = pathPart.substring(firstSlash + 1);
            }

            // URL解码路径
            try {
                return URLDecoder.decode(pathPart, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                log.warn("URL解码失败: {}, 使用原始路径", pathPart, e);
                return pathPart;
            }
        } catch (Exception e) {
            log.warn("从URL提取对象名称失败: {}, 使用备用方法", url, e);

            // 备用方法：简单提取
            // 移除查询参数
            String path = url;
            int queryIndex = path.indexOf('?');
            if (queryIndex > 0) {
                path = path.substring(0, queryIndex);
            }

            // 移除协议和主机名
            if (path.contains("://")) {
                path = path.substring(path.indexOf("://") + 3);
                int slashIndex = path.indexOf('/');
                if (slashIndex > 0) {
                    path = path.substring(slashIndex + 1);
                }
            }

            // URL解码路径
            try {
                return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            } catch (Exception exceptione) {
                log.warn("URL解码失败: {}, 使用原始路径", path, exceptione);
                return path;
            }
        }
    }

    /**
     * 文本提取
     * 根据文件类型调用不同的提取器：PDF -> PDFBox, DOCX -> Apache POI, TXT -> 直接读取
     */
    private String extractText(String filePath, String fileExtension) {
        log.info("提取文本: filePath={}, extension={}", filePath, fileExtension);

        String resolvedFilePath = filePath;
        File tempFile = null;

        try {
            // 解析文件路径：如果是URL，下载到临时文件
            resolvedFilePath = resolveFilePath(filePath);
            log.info("解析后的文件路径: {}", resolvedFilePath);

            // 检查是否是临时文件（下载的文件）
            if (!resolvedFilePath.equals(filePath)) {
                tempFile = new File(resolvedFilePath);
            }

            switch (fileExtension.toLowerCase()) {
                case "pdf":
                    return extractTextFromPdf(resolvedFilePath);
                case "docx":
                case "doc":
                    return extractTextFromDocx(resolvedFilePath);
                case "txt":
                    return extractTextFromTxt(resolvedFilePath);
                default:
                    log.warn("不支持的文件类型: {}", fileExtension);
                    throw new UnsupportedOperationException("不支持的文件类型: " + fileExtension);
            }
        } catch (Exception e) {
            log.error("文本提取失败: filePath={}, resolvedFilePath={}", filePath, resolvedFilePath, e);
            throw new RuntimeException("文本提取失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                try {
                    boolean deleted = tempFile.delete();
                    if (deleted) {
                        log.debug("临时文件已删除: {}", tempFile.getAbsolutePath());
                    } else {
                        log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                    }
                } catch (SecurityException e) {
                    log.warn("临时文件删除被拒绝: {}", tempFile.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * 从PDF文件提取文本
     */
    private String extractTextFromPdf(String filePath) {
        try {
            // PDFBox 3.x使用Loader.loadPDF()方法
            PDDocument document = Loader.loadPDF(new File(filePath));
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();
            return text;
        } catch (IOException e) {
            throw new RuntimeException("PDF文本提取失败", e);
        }
    }

    /**
     * 从DOCX/DOC文件提取文本
     */
    private String extractTextFromDocx(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
            return text.toString();
        } catch (IOException e) {
            throw new RuntimeException("DOCX文本提取失败", e);
        }
    }

    /**
     * 从TXT文件提取文本
     */
    private String extractTextFromTxt(String filePath) {
        try {
            return Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("TXT文本读取失败", e);
        }
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

            // 如果已到达文本末尾，退出循环防止死循环
            if (end >= length) {
                break;
            }
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
        log.info("清理旧分块数据: documentId={}", documentId);

        // 删除数据库中的旧分块记录
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId);
        documentChunkService.remove(wrapper);

        // 从Milvus中删除相关向量
        milvusService.deleteByDocumentId(documentId);  // 注意：实际方法名是deleteByDocumentId，不是deleteVectorsByDocumentId

        log.info("旧分块数据清理完成: documentId={}", documentId);
    }
}