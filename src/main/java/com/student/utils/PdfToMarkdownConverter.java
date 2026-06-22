package com.student.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

/**
 * PDF 转 Markdown 转换器
 * 通过 HTTP 调用 MinerU 云端 API 将 PDF 转换为 Markdown 格式文本。
 * 段落间以 \n\n 分隔，保留标题、表格、列表等结构信息。
 * <p>
 * 使用 MinerU 轻量 API（无需认证）：≤10MB / ≤20页
 *
 * @author 系统
 * @version 2.0
 */
@Slf4j
public class PdfToMarkdownConverter {

    /** MinerU 云端 API 端点 */
    private final String apiEndpoint;

    /** HTTP 请求超时时间（秒） */
    private final int timeoutSeconds;

    /** 默认 API 端点（MinerU 轻量 API） */
    private static final String DEFAULT_API_ENDPOINT = "https://mineru.net/api/v1/agent/parse/file";

    /** 默认超时（秒） */
    private static final int DEFAULT_TIMEOUT = 300;

    /** 文件大小上限（字节），轻量 API 限制 10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** 页数上限，轻量 API 限制 20 页 */
    private static final int MAX_PAGE_COUNT = 20;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 使用默认配置构造转换器
     */
    public PdfToMarkdownConverter() {
        this(DEFAULT_API_ENDPOINT, DEFAULT_TIMEOUT);
    }

    /**
     * 使用自定义配置构造转换器
     *
     * @param apiEndpoint   MinerU 云端 API 端点
     * @param timeoutSeconds HTTP 请求超时时间（秒）
     */
    public PdfToMarkdownConverter(String apiEndpoint, int timeoutSeconds) {
        this.apiEndpoint = (apiEndpoint != null && !apiEndpoint.isBlank())
                ? apiEndpoint : DEFAULT_API_ENDPOINT;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用 MinerU 云端 API 将 PDF 转换为 Markdown
     *
     * @param pdfFile PDF 文件（仅支持 .pdf）
     * @return Markdown 文本
     * @throws IllegalArgumentException 非 PDF 文件
     * @throws MinerUException          MinerU API 调用失败
     */
    public String convert(File pdfFile) {
        if (pdfFile == null) {
            throw new IllegalArgumentException("PDF 文件不能为 null");
        }

        String fileName = pdfFile.getName().toLowerCase();
        if (!fileName.endsWith(".pdf")) {
            throw new IllegalArgumentException("仅支持 PDF 文件，当前文件: " + pdfFile.getName());
        }

        if (!pdfFile.exists() || !pdfFile.isFile()) {
            throw new IllegalArgumentException("PDF 文件不存在或不可读: " + pdfFile.getAbsolutePath());
        }

        // 检查文件大小
        long fileSize = pdfFile.length();
        if (fileSize > MAX_FILE_SIZE) {
            throw new MinerUException(String.format(
                    "PDF 文件过大（%.1f MB），MinerU 轻量 API 限制 ≤10MB: %s",
                    fileSize / (1024.0 * 1024.0), pdfFile.getName()));
        }

        // 检查页数
        int pageCount = getPageCount(pdfFile);
        if (pageCount > MAX_PAGE_COUNT) {
            throw new MinerUException(String.format(
                    "PDF 页数过多（%d 页），MinerU 轻量 API 限制 ≤20 页: %s",
                    pageCount, pdfFile.getName()));
        }

        log.info("调用 MinerU 云端 API: file={}, size={}bytes, pages={}", pdfFile.getName(), fileSize, pageCount);

        try {
            // 构建 multipart/form-data 请求体
            String boundary = "MinerU----" + System.currentTimeMillis();
            byte[] body = buildMultipartBody(pdfFile, boundary);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiEndpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int statusCode = response.statusCode();
            String responseBody = response.body();

            log.info("MinerU API 响应: HTTP {}, bodyLength={}", statusCode,
                    responseBody != null ? responseBody.length() : 0);
            if (responseBody != null && responseBody.length() > 0) {
                int previewLen = Math.min(500, responseBody.length());
                log.info("MinerU API 响应内容预览: {}", responseBody.substring(0, previewLen));
            }

            if (statusCode != 200) {
                log.error("MinerU API 返回错误: HTTP {}, 响应: {}", statusCode, responseBody);
                throw new MinerUException(String.format(
                        "MinerU API 返回错误 (HTTP %d): %s", statusCode, pdfFile.getName()));
            }

            String markdown = extractMarkdown(responseBody);
            if (markdown == null || markdown.isBlank()) {
                log.error("MinerU API 返回空内容，原始响应: {}",
                        responseBody != null ? responseBody.substring(0, Math.min(1000, responseBody.length())) : "null");
                throw new MinerUException("MinerU API 未返回有效的 Markdown 内容: " + pdfFile.getName());
            }

            log.info("MinerU API 转换完成: {} -> {} 字符", pdfFile.getName(), markdown.length());
            return markdown;

        } catch (MinerUException e) {
            throw e;
        } catch (IOException e) {
            throw new MinerUException("MinerU API 网络异常: " + pdfFile.getName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MinerUException("MinerU API 调用被中断: " + pdfFile.getName(), e);
        }
    }

    /**
     * 构建 multipart/form-data 请求体
     */
    private byte[] buildMultipartBody(File pdfFile, String boundary) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(pdfFile.getName()).append("\"\r\n");
        sb.append("Content-Type: application/pdf\r\n\r\n");

        byte[] header = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] fileContent = Files.readAllBytes(pdfFile.toPath());

        byte[] result = new byte[header.length + fileContent.length + footer.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(fileContent, 0, result, header.length, fileContent.length);
        System.arraycopy(footer, 0, result, header.length + fileContent.length, footer.length);

        return result;
    }

    /**
     * 从 API 响应中提取 markdown 文本
     * 支持多种响应格式：纯文本、JSON {data: {content/markdown/text: "..."}} 等
     */
    private String extractMarkdown(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        String trimmed = responseBody.trim();

        // 尝试作为 JSON 解析
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);

                // 常见格式：{data: {content/markdown/text: "..."}}
                JsonNode data = root.get("data");
                if (data != null && !data.isNull()) {
                    for (String key : new String[]{"content", "markdown", "text", "md"}) {
                        JsonNode node = data.get(key);
                        if (node != null && !node.isNull() && !node.asText().isBlank()) {
                            return node.asText();
                        }
                    }
                }

                // 直接字段
                for (String key : new String[]{"content", "markdown", "text", "md"}) {
                    JsonNode node = root.get(key);
                    if (node != null && !node.isNull() && !node.asText().isBlank()) {
                        return node.asText();
                    }
                }

                log.warn("MinerU API 返回未知 JSON 格式，尝试整体作为文本使用");
            } catch (Exception e) {
                log.debug("JSON 解析失败，将响应作为纯文本 markdown 处理");
            }
        }

        return trimmed;
    }

    /**
     * 快速获取 PDF 页数（仅读取文档结构，不解析内容）
     */
    private int getPageCount(File pdfFile) {
        try {
            try (org.apache.pdfbox.pdmodel.PDDocument doc =
                         org.apache.pdfbox.Loader.loadPDF(pdfFile)) {
                return doc.getNumberOfPages();
            }
        } catch (Exception e) {
            log.warn("无法读取 PDF 页数，跳过页数检查: {}", e.getMessage());
            return 0;
        }
    }
}
