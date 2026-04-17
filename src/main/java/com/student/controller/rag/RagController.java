package com.student.controller.rag;

import com.student.dto.document.DocumentProcessResponse;
import com.student.entity.Document;
import com.student.service.rag.QaService;
import com.student.service.dataBase.MinioService;
import com.student.service.document.DocumentService;
import com.student.service.rag.DocumentProcessorService;
import java.util.concurrent.CompletableFuture;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * RAG管理控制器
 * 提供文档处理、RAG统计信息等管理API
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG管理", description = "RAG文档处理、统计信息等管理API")
public class RagController {

    private final QaService qaService;
    private final MinioService minioService;
    private final DocumentService documentService;
    private final DocumentProcessorService documentProcessorService;

    @PostMapping("/process-document")
    @Operation(summary = "文档处理", description = "上传并处理文档，提取文本、分块、生成嵌入向量")
    public ResponseEntity<DocumentProcessResponse> processDocument(
            @Parameter(description = "文档文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "文档标题", required = false)
            @RequestParam(required = false) String title,
            @Parameter(description = "文档分类", required = false)
            @RequestParam(required = false) String category) {
        log.info("文档处理请求: fileName={}, size={}, title={}, category={}",
                file.getOriginalFilename(), file.getSize(), title, category);

        try {
            // 1. 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(DocumentProcessResponse.error("文件不能为空"));
            }

            // 2. 保存文件到MinIO
            // 生成对象名称: documents/timestamp/originalFilename
            String originalFilename = file.getOriginalFilename();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String objectName = String.format("documents/%s/%s", timestamp, originalFilename);
            String filePath = minioService.uploadFile(file, objectName);

            // 3. 创建文档记录
            Document document = Document.builder()
                    .title(title != null ? title : originalFilename)
                    .filePath(filePath)
                    .category(category)
                    .status(Document.Status.UPLOADED)
                    .build();
            documentService.save(document);

            log.info("文档记录创建成功: documentId={}, filePath={}", document.getId(), filePath);

            // 4. 异步启动文档处理
            CompletableFuture.runAsync(() -> {
                try {
                    documentProcessorService.processDocument(document.getId());
                    log.info("文档处理完成: documentId={}", document.getId());
                } catch (Exception e) {
                    log.error("文档处理失败: documentId={}", document.getId(), e);
                    document.updateStatus(Document.Status.FAILED);
                    documentService.updateById(document);
                }
            });

            // 5. 返回响应
            return ResponseEntity.ok(DocumentProcessResponse.success(document.getId(), document));

        } catch (Exception e) {
            log.error("文档处理请求失败", e);
            return ResponseEntity.internalServerError()
                    .body(DocumentProcessResponse.error("文档处理失败: " + e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "RAG统计信息", description = "获取RAG系统统计信息，包括问答量、检索量等")
    public ResponseEntity<?> getStats() {
        log.info("获取RAG统计信息");

        // 获取问答统计信息
        QaService.QaStats stats = qaService.getStats();

        // 扩展更多RAG统计信息
        // TODO: 添加检索统计、向量库统计等

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "RAG服务健康检查")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("RagController is healthy");
    }
}