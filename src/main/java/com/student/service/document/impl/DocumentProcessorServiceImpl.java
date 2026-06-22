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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.student.utils.PdfClassification;
import com.student.utils.PdfPreCheckResult;
import com.student.utils.PdfPreChecker;
import com.student.utils.PdfToMarkdownConverter;
import com.student.utils.MinerUException;
import com.student.utils.RecursiveTextSplitter;
import com.student.utils.MedicalDocumentSplitter;
import com.student.utils.PageType;
import com.student.utils.PageLevelResult;
import com.student.utils.PageLevelClassifier;
import com.student.utils.TextCleaner;

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
                default:
                    log.warn("不支持的文件类型: {}", fileExtension);
                    throw new UnsupportedOperationException("仅支持 PDF 文件，不支持的类型: " + fileExtension);
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
     * 从PDF文件提取文本：MinerU → PDFBox → Tesseract OCR 三级降级管道
     */
    private String extractTextFromPdf(String filePath) {
        RagConfig.DocumentConfig.MinerUConfig mineruConfig = ragConfig.getDocument().getMineru();
        RagConfig.DocumentConfig.OcrConfig ocrConfig = ragConfig.getDocument().getOcr();
        RagConfig.DocumentConfig.PdfPreCheckConfig preCheckConfig = ragConfig.getDocument().getPdfPreCheck();
        RagConfig.DocumentConfig.PageLevelConfig pageLevelConfig = ragConfig.getDocument().getPageLevel();
        RagConfig.DocumentConfig.TextCleanerConfig textCleanerConfig = ragConfig.getDocument().getTextCleaner();

        // PDF 预检查：快速判定 PDF 类型，决定提取路径
        PdfClassification classification = PdfClassification.UNCERTAIN;
        if (preCheckConfig.isEnabled()) {
            try {
                PdfPreChecker checker = new PdfPreChecker(
                        preCheckConfig.getDigitalNativeMinTextLength(),
                        preCheckConfig.getScannedMaxTextLength(),
                        preCheckConfig.getMinPagesWithText(),
                        preCheckConfig.getTocPatternRepeatThreshold());
                PdfPreCheckResult preCheckResult = checker.preCheck(new File(filePath));
                classification = preCheckResult.getClassification();
                log.info("PDF 预检查完成: classification={}, reason={}, totalPages={}, sampledTextLength={}",
                        classification, preCheckResult.getReason(),
                        preCheckResult.getTotalPages(), preCheckResult.getSampledText().length());
            } catch (Exception e) {
                log.warn("PDF 预检查异常，回退到 UNCERTAIN 流程: {}", e.getMessage());
                classification = PdfClassification.UNCERTAIN;
            }
        }

        String text;

        // 提取文本：根据预检结果和配置选择路径
        if (classification == PdfClassification.SCANNED) {
            text = extractTextWithLegacyPipeline(filePath, mineruConfig, ocrConfig, classification);
        } else if (pageLevelConfig.isEnabled()) {
            log.info("启用页面级处理管道: classification={}", classification);
            text = extractTextWithPageLevelPipeline(filePath, pageLevelConfig, ocrConfig);
            // 页面级处理不足时回退到传统管道
            if (text == null || text.length() < ocrConfig.getMinTextLength()) {
                log.warn("页面级处理文本不足({})，回退到传统管道",
                        text != null ? text.length() : 0);
                String legacyText = extractTextWithLegacyPipeline(
                        filePath, mineruConfig, ocrConfig, classification);
                if (legacyText != null && (text == null || legacyText.length() > text.length())) {
                    text = legacyText;
                }
            }
        } else {
            text = extractTextWithLegacyPipeline(filePath, mineruConfig, ocrConfig, classification);
        }

        // 文本后处理
        if (textCleanerConfig.isEnabled() && text != null && !text.isBlank()) {
            text = applyTextCleaning(text, textCleanerConfig);
        }

        if (text != null && !text.isBlank()) {
            return text;
        }

        String detail = switch (classification) {
            case SCANNED -> "该 PDF 被判定为扫描件/纯图片，MinerU 云端 API、PDFBox 和 Tesseract OCR 均未提取到有效文本。"
                    + "请检查网络连通性（mineru.net）及 Tesseract OCR 是否正确安装。";
            case DIGITAL_NATIVE -> "该 PDF 被判定为数字原生，但 PDFBox 未提取到有效文本。"
                    + "文件可能为空白页或使用了不支持的字体编码。";
            case UNCERTAIN -> "该 PDF 类型不确定，所有提取管道均未获得有效文本。"
                    + "文件可能为纯图片扫描件，请确认 MinerU API 可正常访问或 OCR 已启用。";
        };
        throw new RuntimeException("PDF 文本提取失败: " + detail);
    }

    /**
     * 传统 3 级降级管道：MinerU → PDFBox → Tesseract OCR
     */
    private String extractTextWithLegacyPipeline(String filePath,
                                                  RagConfig.DocumentConfig.MinerUConfig mineruConfig,
                                                  RagConfig.DocumentConfig.OcrConfig ocrConfig,
                                                  PdfClassification classification) {
        String text = null;

        if (mineruConfig.isEnabled() && classification != PdfClassification.DIGITAL_NATIVE) {
            try {
                PdfToMarkdownConverter converter = new PdfToMarkdownConverter(
                        mineruConfig.getApiEndpoint(), mineruConfig.getTimeout());
                text = converter.convert(new File(filePath));
                if (text != null && text.length() >= ocrConfig.getMinTextLength()) {
                    log.info("MinerU 提取成功: {} 字符", text.length());
                    return text;
                }
                log.warn("MinerU 提取文本过短 ({} 字符)，回退到 PDFBox", text != null ? text.length() : 0);
            } catch (MinerUException e) {
                log.error("MinerU API 转换失败，回退到 PDFBox: {}", e.getMessage());
                log.debug("MinerU API 异常详情", e);
            }
        }

        String pdfboxText = extractTextFromPdfWithPdfbox(filePath);
        if (pdfboxText != null && pdfboxText.length() >= ocrConfig.getMinTextLength()) {
            log.info("PDFBox 提取成功: {} 字符", pdfboxText.length());
            return pdfboxText;
        }
        log.warn("PDFBox 提取文本过短 ({} 字符)", pdfboxText != null ? pdfboxText.length() : 0);

        if (text == null || (pdfboxText != null && pdfboxText.length() > text.length())) {
            text = pdfboxText;
        }

        if (ocrConfig.isEnabled() && (text == null || text.length() < ocrConfig.getMinTextLength())) {
            try {
                String ocrText = extractTextFromPdfWithOcr(filePath, ocrConfig);
                if (ocrText != null && ocrText.length() >= ocrConfig.getMinTextLength()) {
                    log.info("Tesseract OCR 提取成功: {} 字符", ocrText.length());
                    return ocrText;
                }
                log.warn("Tesseract OCR 提取文本仍不足: {} 字符", ocrText != null ? ocrText.length() : 0);
            } catch (Exception e) {
                log.error("Tesseract OCR 提取失败", e);
            }
        }

        return text;
    }

    /**
     * 页面级处理管道：逐页分类，按类型选择 PDFBox 或 OCR
     */
    private String extractTextWithPageLevelPipeline(String filePath,
                                                     RagConfig.DocumentConfig.PageLevelConfig pageLevelConfig,
                                                     RagConfig.DocumentConfig.OcrConfig ocrConfig) {
        try (org.apache.pdfbox.pdmodel.PDDocument document =
                     org.apache.pdfbox.Loader.loadPDF(new File(filePath))) {
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                return "";
            }

            PageLevelClassifier classifier = buildPageLevelClassifier(pageLevelConfig);
            StringBuilder result = new StringBuilder();
            int pdfboxPages = 0;
            int ocrPages = 0;
            int skippedPages = 0;

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                PageLevelResult pageResult = classifier.classify(document, pageIndex, totalPages);
                log.debug("第 {} 页分类: type={}, isKey={}, hasText={}, hasImages={}, textLen={}",
                        pageIndex + 1, pageResult.getPageType(), pageResult.isKeyPage(),
                        pageResult.isHasTextLayer(), pageResult.isHasImages(), pageResult.getTextLength());

                String pageText = null;
                switch (pageResult.getPageType()) {
                    case HAS_TEXT_LAYER:
                    case COMPLEX_TABLE_NON_KEY:
                        pageText = pageResult.getPageText();
                        pdfboxPages++;
                        break;
                    case NO_TEXT_KEY_PAGE:
                    case COMPLEX_TABLE_KEY:
                    case UNCERTAIN:
                        pageText = extractSinglePageTextWithOcr(document, pageIndex, ocrConfig);
                        ocrPages++;
                        break;
                    case NO_TEXT_NON_KEY:
                        if (!pageLevelConfig.isSkipNonKeyPages()) {
                            pageText = extractSinglePageTextWithOcr(document, pageIndex, ocrConfig);
                            ocrPages++;
                        } else {
                            skippedPages++;
                        }
                        break;
                }

                if (pageText != null && !pageText.isBlank()) {
                    if (!result.isEmpty()) {
                        result.append("\n\n");
                    }
                    result.append(pageText.trim());
                }
            }

            log.info("页面级处理完成: totalPages={}, pdfbox={}, ocr={}, skipped={}, textLength={}",
                    totalPages, pdfboxPages, ocrPages, skippedPages, result.length());
            return result.toString().trim();
        } catch (IOException e) {
            log.error("页面级处理失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 构建页面级分类器（处理自定义关键词）
     */
    private PageLevelClassifier buildPageLevelClassifier(
            RagConfig.DocumentConfig.PageLevelConfig config) {
        String customKeywords = config.getMedicalKeywords();
        if (customKeywords != null && !customKeywords.isBlank()) {
            java.util.Set<String> keywords = new java.util.HashSet<>();
            for (String kw : customKeywords.split(",")) {
                String trimmed = kw.trim();
                if (!trimmed.isEmpty()) {
                    keywords.add(trimmed);
                }
            }
            if (!keywords.isEmpty()) {
                return new PageLevelClassifier(config.getMinTextLayerLength(), keywords);
            }
        }
        return new PageLevelClassifier(config.getMinTextLayerLength(),
                java.util.Set.of("诊断", "结论", "用法用量", "禁忌", "检查结果",
                        "临床表现", "治疗", "手术", "药物", "剂量"));
    }

    /**
     * 单页 PDFBox 文本提取
     */
    private String extractSinglePageTextWithPdfbox(
            org.apache.pdfbox.pdmodel.PDDocument document, int pageIndex) {
        try {
            org.apache.pdfbox.text.PDFTextStripper stripper =
                    new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException e) {
            log.warn("PDFBox 第 {} 页提取失败: {}", pageIndex + 1, e.getMessage());
            return "";
        }
    }

    /**
     * 单页 Tesseract OCR 提取
     */
    private String extractSinglePageTextWithOcr(
            org.apache.pdfbox.pdmodel.PDDocument document, int pageIndex,
            RagConfig.DocumentConfig.OcrConfig ocrConfig) {
        try {
            net.sourceforge.tess4j.Tesseract tesseract = new net.sourceforge.tess4j.Tesseract();
            String tessdataPath = resolveTessdataPath();
            if (tessdataPath != null) {
                tesseract.setDatapath(tessdataPath);
            }
            tesseract.setLanguage(ocrConfig.getLanguage());

            org.apache.pdfbox.rendering.PDFRenderer renderer =
                    new org.apache.pdfbox.rendering.PDFRenderer(document);
            java.awt.image.BufferedImage image = renderer.renderImageWithDPI(pageIndex, ocrConfig.getRenderDpi());
            return tesseract.doOCR(image);
        } catch (Exception e) {
            log.warn("OCR 第 {} 页失败: {}", pageIndex + 1, e.getMessage());
            return "";
        }
    }

    /**
     * 执行文本后处理清洗
     */
    private String applyTextCleaning(String text,
                                      RagConfig.DocumentConfig.TextCleanerConfig config) {
        TextCleaner cleaner = new TextCleaner(config.getHeaderFooterRepeatThreshold());

        if (config.isRemoveMetadata()) {
            text = cleaner.removeMetadata(text);
        }
        if (config.isRemoveHeadersFooters()) {
            text = cleaner.removeHeadersFooters(text);
        }
        if (config.isRemoveCitations()) {
            text = cleaner.removeCitationMarkers(text);
        }
        if (config.isNormalizeWhitespace()) {
            text = cleaner.normalizeWhitespace(text);
        }
        if (config.isFixBrokenLines()) {
            text = cleaner.fixBrokenLines(text);
        }

        log.info("文本后处理完成: {} 字符", text != null ? text.length() : 0);
        return text;
    }

    /**
     * PDFBox 兜底提取（MinerU 禁用或失败时使用）
     */
    private String extractTextFromPdfWithPdfbox(String filePath) {
        try {
            org.apache.pdfbox.pdmodel.PDDocument document =
                    org.apache.pdfbox.Loader.loadPDF(new File(filePath));
            org.apache.pdfbox.text.PDFTextStripper stripper =
                    new org.apache.pdfbox.text.PDFTextStripper();
            String text = stripper.getText(document);
            document.close();
            return text;
        } catch (IOException e) {
            throw new RuntimeException("PDF文本提取失败", e);
        }
    }

    /**
     * Tesseract OCR 降级提取（扫描件 PDF 专用）
     * 将 PDF 每页渲染为图片后逐页 OCR
     */
    private String extractTextFromPdfWithOcr(String filePath, RagConfig.DocumentConfig.OcrConfig ocrConfig) {
        log.info("启动 Tesseract OCR 降级: filePath={}, language={}, dpi={}",
                filePath, ocrConfig.getLanguage(), ocrConfig.getRenderDpi());

        try {
            net.sourceforge.tess4j.Tesseract tesseract = new net.sourceforge.tess4j.Tesseract();

            // 自动检测或使用环境变量指定的 tessdata 路径
            String tessdataPath = resolveTessdataPath();
            if (tessdataPath != null) {
                tesseract.setDatapath(tessdataPath);
                log.info("Tesseract datapath: {}", tessdataPath);
            }

            tesseract.setLanguage(ocrConfig.getLanguage());

            org.apache.pdfbox.pdmodel.PDDocument document =
                    org.apache.pdfbox.Loader.loadPDF(new File(filePath));
            org.apache.pdfbox.rendering.PDFRenderer renderer =
                    new org.apache.pdfbox.rendering.PDFRenderer(document);

            StringBuilder result = new StringBuilder();
            int pageCount = document.getNumberOfPages();
            int ocrFailedPages = 0;
            String lastOcrError = null;
            log.info("PDF 共 {} 页，开始逐页 OCR", pageCount);

            for (int page = 0; page < pageCount; page++) {
                try {
                    java.awt.image.BufferedImage image = renderer.renderImageWithDPI(page, ocrConfig.getRenderDpi());
                    String pageText = tesseract.doOCR(image);
                    if (pageText != null && !pageText.isBlank()) {
                        result.append(pageText.trim()).append("\n\n");
                    }
                    if ((page + 1) % 10 == 0) {
                        log.info("OCR 进度: {}/{} 页", page + 1, pageCount);
                    }
                } catch (Exception e) {
                    ocrFailedPages++;
                    lastOcrError = e.getMessage();
                    log.warn("OCR 第 {} 页失败: {}", page + 1, e.getMessage());
                }
            }

            document.close();

            String ocrResult = result.toString().trim();
            if (ocrResult.isEmpty() && ocrFailedPages > 0) {
                throw new RuntimeException(String.format(
                        "Tesseract OCR 全部 %d 页均失败，最后错误: %s。请检查 Tesseract 是否安装且语言包 '%s' 已下载。",
                        ocrFailedPages, lastOcrError, ocrConfig.getLanguage()));
            }

            log.info("Tesseract OCR 完成: {} 页处理, {} 字符, {} 页失败",
                    pageCount, ocrResult.length(), ocrFailedPages);
            return ocrResult;
        } catch (IOException e) {
            throw new RuntimeException("Tesseract OCR 降级提取失败", e);
        }
    }

    /**
     * 解析 Tesseract tessdata 目录路径
     */
    private String resolveTessdataPath() {
        // 优先级：环境变量 > 系统属性 > 常见安装路径 > null（使用 Tess4J 默认）
        String envPath = System.getenv("TESSDATA_PREFIX");
        if (envPath != null && !envPath.isBlank()) {
            return envPath;
        }
        String sysProp = System.getProperty("TESSDATA_PREFIX");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        // Windows 常见安装路径
        String[] commonPaths = {
            "C:\\Program Files\\Tesseract-OCR\\tessdata",
            "C:\\Program Files (x86)\\Tesseract-OCR\\tessdata",
            "/usr/share/tesseract-ocr/5/tessdata",
            "/usr/share/tesseract-ocr/4/tessdata",
            "/usr/local/share/tessdata"
        };
        for (String path : commonPaths) {
            if (java.nio.file.Files.isDirectory(java.nio.file.Path.of(path))) {
                return path;
            }
        }
        // 返回 null，让 Tess4J 使用内置默认值
        return null;
    }

    /**
     * 文本分块：根据配置的策略选择分割方式
     */
    private List<DocumentChunk> chunkText(String text, Long documentId) {
        RagConfig.DocumentConfig.ChunkConfig chunkConfig = ragConfig.getDocument().getChunk();
        String strategy = chunkConfig.getStrategy();

        if ("medical".equals(strategy)) {
            return chunkTextMedical(text, documentId, chunkConfig);
        }

        List<String> segments;
        if ("recursive".equals(strategy) || "semantic".equals(strategy)) {
            RecursiveTextSplitter splitter = new RecursiveTextSplitter(
                    chunkConfig.getSize(),
                    chunkConfig.getOverlap(),
                    chunkConfig.getMinChunkSize(),
                    parseSeparators(chunkConfig.getSeparators()));
            segments = splitter.split(text);
        } else {
            segments = fixedSizeSplit(text, chunkConfig.getSize());
        }

        // 构建 DocumentChunk 列表
        List<DocumentChunk> chunks = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < segments.size(); i++) {
            String content = segments.get(i);
            int startChar = text.indexOf(content, offset);
            if (startChar == -1) {
                startChar = offset;
            }
            int endChar = startChar + content.length();
            offset = endChar;

            DocumentChunk chunk = DocumentChunk.builder()
                    .documentId(documentId)
                    .chunkIndex(i)
                    .content(content)
                    .metadata(String.format("{\"start_char\": %d, \"end_char\": %d}", startChar, endChar))
                    .build();
            chunks.add(chunk);
        }

        log.debug("文本分块完成: documentId={}, strategy={}, chunkCount={}",
                documentId, strategy, chunks.size());
        return chunks;
    }

    /**
     * 医学文档结构感知分块
     */
    private List<DocumentChunk> chunkTextMedical(String text, Long documentId,
                                                  RagConfig.DocumentConfig.ChunkConfig chunkConfig) {
        RagConfig.DocumentConfig.ChunkConfig.MedicalChunkConfig medicalConfig =
                chunkConfig.getMedical();
        if (medicalConfig == null) {
            medicalConfig = new RagConfig.DocumentConfig.ChunkConfig.MedicalChunkConfig();
        }

        MedicalDocumentSplitter splitter = new MedicalDocumentSplitter(
                chunkConfig.getSize(),
                chunkConfig.getOverlap(),
                chunkConfig.getMinChunkSize(),
                parseSeparators(chunkConfig.getSeparators()),
                medicalConfig.isSectionHierarchy(),
                medicalConfig.isTableProtection(),
                medicalConfig.isIcdDetection(),
                medicalConfig.isSiblingContext(),
                medicalConfig.getMinSectionHeaderLength(),
                medicalConfig.getMinTableRows(),
                medicalConfig.getSiblingMergeThreshold(),
                medicalConfig.getMaxDepth());

        List<MedicalDocumentSplitter.SegmentInfo> segmentInfos = splitter.splitWithMetadata(text);
        return buildChunksWithMetadata(segmentInfos, documentId);
    }

    /**
     * 从携带层级元数据的 SegmentInfo 构建 DocumentChunk 列表
     */
    private List<DocumentChunk> buildChunksWithMetadata(
            List<MedicalDocumentSplitter.SegmentInfo> segmentInfos, Long documentId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < segmentInfos.size(); i++) {
            MedicalDocumentSplitter.SegmentInfo info = segmentInfos.get(i);
            DocumentChunk chunk = DocumentChunk.builder()
                    .documentId(documentId)
                    .chunkIndex(i)
                    .content(info.getText())
                    .metadata(buildChunkMetadataJson(info))
                    .build();
            chunks.add(chunk);
        }
        log.debug("医学文档分块完成: documentId={}, strategy=medical, chunkCount={}",
                documentId, chunks.size());
        return chunks;
    }

    /**
     * 构建增强的 chunk metadata JSON（含层级元数据）
     */
    private String buildChunkMetadataJson(MedicalDocumentSplitter.SegmentInfo info) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"start_char\": ").append(info.getStartChar())
                .append(", \"end_char\": ").append(info.getEndChar());
        if (info.getSectionPath() != null && !info.getSectionPath().equals("[]")) {
            json.append(", \"section_path\": ").append(info.getSectionPath());
        }
        if (info.getBreadcrumb() != null) {
            json.append(", \"breadcrumb\": \"")
                    .append(MedicalDocumentSplitter.escapeJson(info.getBreadcrumb()))
                    .append("\"");
        }
        if (info.getSectionTitle() != null) {
            json.append(", \"section_title\": \"")
                    .append(MedicalDocumentSplitter.escapeJson(info.getSectionTitle()))
                    .append("\"");
        }
        json.append(", \"section_level\": ").append(info.getSectionLevel());
        if (info.getParentSection() != null) {
            json.append(", \"parent_section\": \"")
                    .append(MedicalDocumentSplitter.escapeJson(info.getParentSection()))
                    .append("\"");
        }
        if (info.getRootSection() != null) {
            json.append(", \"root_section\": \"")
                    .append(MedicalDocumentSplitter.escapeJson(info.getRootSection()))
                    .append("\"");
        }
        if (info.getParagraphNumber() != null) {
            json.append(", \"paragraph_number\": \"")
                    .append(MedicalDocumentSplitter.escapeJson(info.getParagraphNumber()))
                    .append("\"");
        }
        if (info.getPrevSibling() != null) {
            json.append(", \"prev_sibling\": \"")
                    .append(MedicalDocumentSplitter.escapeJson(info.getPrevSibling()))
                    .append("\"");
        }
        if (info.getNextSibling() != null) {
            json.append(", \"next_sibling\": \"")
                    .append(MedicalDocumentSplitter.escapeJson(info.getNextSibling()))
                    .append("\"");
        }
        if (info.getDocumentTopic() != null) {
            json.append(", \"document_topic\": \"")
                    .append(MedicalDocumentSplitter.escapeJson(info.getDocumentTopic()))
                    .append("\"");
        }
        json.append(", \"document_section_count\": ").append(info.getDocumentSectionCount());
        if (info.isTable()) {
            json.append(", \"is_table\": true");
        }
        if (info.getIcdCodes() != null && !info.getIcdCodes().isEmpty()) {
            json.append(", \"icd_codes\": [");
            for (int j = 0; j < info.getIcdCodes().size(); j++) {
                if (j > 0) json.append(", ");
                json.append("\"").append(info.getIcdCodes().get(j)).append("\"");
            }
            json.append("]");
        }
        json.append("}");
        return json.toString();
    }

    /**
     * 固定大小分割（原有逻辑保留）
     */
    private List<String> fixedSizeSplit(String text, int size) {
        List<String> result = new ArrayList<>();
        int length = text.length();
        int index = 0;
        while (index < length) {
            int end = Math.min(index + size, length);
            result.add(text.substring(index, end));
            index = end;
        }
        return result;
    }

    /**
     * 解析配置中的分隔符列表
     */
    private List<String> parseSeparators(String separatorsStr) {
        if (separatorsStr == null || separatorsStr.isBlank()) {
            return List.of("\n\n", "\n", "。", "；", "，", " ", "");
        }
        String[] parts = separatorsStr.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            // 还原转义字符 \n
            result.add(part.replace("\\n", "\n"));
        }
        return result;
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
        // 构建批量数据
        List<MilvusService.VectorData> dataList = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            DocumentChunk chunk = chunks.get(i);

            MilvusService.VectorData data = new MilvusService.VectorData();
            data.setChunkId(chunk.getId());
            data.setDocumentId(chunk.getDocumentId());
            data.setContent(chunk.getContent());
            data.setEmbedding(vectors.get(i));
            data.setMetadata(chunk.getMetadata());
            dataList.add(data);
        }

        // 批量插入
        List<Long> ids = milvusService.batchInsertVectors(dataList);
        if (ids == null || ids.size() != chunks.size()) {
            throw new RuntimeException("批量向量存储失败: 期望=" + chunks.size() + ", 实际=" + (ids != null ? ids.size() : 0));
        }

        // 持久化到磁盘，确保数据不丢失
        milvusService.flushCollection();

        List<String> vectorIds = new ArrayList<>();
        for (Long id : ids) {
            vectorIds.add(id.toString());
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