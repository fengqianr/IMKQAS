package com.student.controller;

import com.student.entity.Document;
import com.student.service.document.DocumentService;
import com.student.service.dataBase.MinioService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.student.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文档控制器
 * 提供文档相关的RESTful API接口
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService service;
    private final MinioService minioService;

    /**
     * 创建文档
     * @param entity 文档实体
     * @return 创建的文档
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document create(@RequestBody Document entity) {
        service.save(entity);
        return entity;
    }

    /**
     * 更新文档
     * @param id 文档ID
     * @param entity 文档实体
     * @return 更新后的文档
     */
    @PutMapping("/{id}")
    public Document update(@PathVariable Long id, @RequestBody Document entity) {
        entity.setId(id);
        service.updateById(entity);
        return entity;
    }

    /**
     * 删除文档
     * @param id 文档ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    /**
     * 根据ID查询文档
     * @param id 文档ID
     * @return 文档实体
     */
    @GetMapping("/{id}")
    public Document getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 分页查询文档列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<ApiResponse.Pagination<List<Document>>> list(@RequestParam(defaultValue = "1") int current,
                                                                    @RequestParam(defaultValue = "10") int size) {
        Page<Document> page = new Page<>(current, size);
        Page<Document> result = service.page(page);
        return ApiResponse.pagination(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 搜索文档
     * @param keyword 搜索关键词
     * @param category 文档分类
     * @param status 文档状态
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/search")
    public ApiResponse<ApiResponse.Pagination<List<Document>>> search(@RequestParam(required = false) String keyword,
                                                                      @RequestParam(required = false) String category,
                                                                      @RequestParam(required = false) String status,
                                                                      @RequestParam(defaultValue = "1") int current,
                                                                      @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Document> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("title", keyword);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        Page<Document> result = service.page(new Page<>(current, size), wrapper);
        return ApiResponse.pagination(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 文档预览 - 流式返回原始文件内容
     * 根据文件类型自动设置 Content-Type，支持浏览器内预览
     *
     * @param id 文档ID
     * @param response HTTP响应
     */
    @GetMapping("/{id}/preview")
    public void preview(@PathVariable Long id, HttpServletResponse response) {
        Document document = service.getById(id);
        if (document == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String filePath = document.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            log.warn("文档文件路径为空: documentId={}", id);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            // 从MinIO预签名URL中提取对象名称
            String objectName = extractObjectNameFromUrl(filePath);
            if (objectName == null) {
                log.error("无法从文件路径提取对象名称: documentId={}, filePath={}", id, filePath);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            // 获取文件扩展名并设置Content-Type
            String fileExtension = getFileExtension(filePath);
            String contentType = getContentType(fileExtension);
            response.setContentType(contentType);
            String filename = document.getTitle();
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "inline; filename*=UTF-8''" + encodedFilename);

            // 从MinIO下载并流式返回
            try (InputStream inputStream = minioService.downloadFile(objectName)) {
                StreamUtils.copy(inputStream, response.getOutputStream());
            }
            response.flushBuffer();
        } catch (Exception e) {
            log.error("文档预览失败: documentId={}", id, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 文档文本预览 - 提取纯文本内容
     * 适用于不支持浏览器原生预览的格式（如DOCX），返回纯文本
     *
     * @param id 文档ID
     * @param response HTTP响应
     */
    @GetMapping("/{id}/preview/text")
    public void previewText(@PathVariable Long id, HttpServletResponse response) {
        Document document = service.getById(id);
        if (document == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            String text = extractPreviewText(document);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(text);
        } catch (Exception e) {
            log.error("文档文本预览失败: documentId={}", id, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // ========== 预览辅助方法 ==========

    /**
     * 提取预览文本（仅支持 PDF）
     */
    private String extractPreviewText(Document document) throws Exception {
        String filePath = document.getFilePath();
        String fileExtension = getFileExtension(filePath);
        String objectName = extractObjectNameFromUrl(filePath);

        if (objectName == null) {
            throw new RuntimeException("无法提取对象名称");
        }

        if (!"pdf".equalsIgnoreCase(fileExtension)) {
            throw new UnsupportedOperationException("仅支持 PDF 文件预览，不支持的类型: " + fileExtension);
        }

        // 下载文件到临时文件
        java.io.File tempFile = null;
        try (InputStream inputStream = minioService.downloadFile(objectName)) {
            String tempDir = System.getProperty("java.io.tmpdir");
            String tempFileName = "preview_" + System.currentTimeMillis() + "_" + document.getId();
            tempFile = new java.io.File(tempDir, tempFileName);
            java.nio.file.Files.copy(inputStream, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return extractTextFromPdf(tempFile);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 从PDF文件提取文本
     */
    private String extractTextFromPdf(java.io.File file) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filePath) {
        if (filePath == null) return "";
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
     * 根据文件扩展名获取Content-Type
     */
    private String getContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain;charset=UTF-8";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    /**
     * 从MinIO预签名URL中提取对象名称
     */
    private String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;

        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
                int firstSlash = path.indexOf('/');
                if (firstSlash > 0) {
                    String objectName = path.substring(firstSlash + 1);
                    try {
                        return URLDecoder.decode(objectName, StandardCharsets.UTF_8.name());
                    } catch (Exception e) {
                        return objectName;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析URL失败，尝试备用方法: {}", url, e);
        }

        // 备用方法
        try {
            String path = url;
            int queryIndex = path.indexOf('?');
            if (queryIndex > 0) path = path.substring(0, queryIndex);

            if (path.contains("://")) {
                path = path.substring(path.indexOf("://") + 3);
                int slashIndex = path.indexOf('/');
                if (slashIndex > 0) {
                    path = path.substring(slashIndex + 1);
                }
            }

            int firstSlash = path.indexOf('/');
            if (firstSlash > 0) {
                path = path.substring(firstSlash + 1);
            }

            try {
                return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return path;
            }
        } catch (Exception e) {
            log.error("从URL提取对象名称失败: {}", url, e);
            return null;
        }
    }
}