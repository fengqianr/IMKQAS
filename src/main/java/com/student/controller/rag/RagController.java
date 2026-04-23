package com.student.controller.rag;

import com.student.dto.document.DocumentProcessResponse;
import com.student.entity.Document;
import com.student.entity.User;
import com.student.service.rag.QaService;
import com.student.service.dataBase.MinioService;
import com.student.service.document.DocumentService;
import com.student.service.rag.DocumentProcessorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

            // 获取当前用户ID
            Long currentUserId = getCurrentUserId();
            log.info("文档上传用户ID: currentUserId={}", currentUserId);
            if (currentUserId == null) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .body(DocumentProcessResponse.error("用户未登录或会话已过期"));
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
                    .uploadedBy(currentUserId)
                    .build();
            documentService.save(document);

            log.info("文档记录创建成功: documentId={}, filePath={}", document.getId(), filePath);

            // 4. 异步启动文档处理，添加超时机制
            Long newDocId = document.getId();
            Document newDocument = document;
            CompletableFuture<Void> processFuture = CompletableFuture.runAsync(() -> {
                try {
                    documentProcessorService.processDocument(newDocId);
                    log.info("文档处理完成: documentId={}", newDocId);
                } catch (Exception e) {
                    log.error("文档处理失败: documentId={}", newDocId, e);
                    Document failedDoc = documentService.getById(newDocId);
                    if (failedDoc != null) {
                        failedDoc.updateStatus(Document.Status.FAILED);
                        documentService.updateById(failedDoc);
                    }
                }
            });

            // 添加超时处理（5分钟超时）
            processFuture.orTimeout(5, TimeUnit.MINUTES).exceptionally(throwable -> {
                log.error("文档处理超时: documentId={}", newDocId, throwable);
                Document failedDoc = documentService.getById(newDocId);
                if (failedDoc != null && failedDoc.getStatus() == Document.Status.PROCESSING) {
                    failedDoc.updateStatus(Document.Status.FAILED);
                    documentService.updateById(failedDoc);
                }
                return null;
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

    @PostMapping("/chunk-document/{documentId}")
    @Operation(summary = "对现有文档进行分块处理", description = "对已有文档进行文本提取、分块、生成嵌入向量等处理")
    public ResponseEntity<DocumentProcessResponse> chunkDocument(
            @Parameter(description = "文档ID", required = true)
            @PathVariable String documentId) {
        log.info("文档分块处理请求: documentId={}", documentId);

        try {
            // 将字符串ID转换为Long
            Long docId;
            try {
                docId = Long.parseLong(documentId);
            } catch (NumberFormatException e) {
                log.error("文档ID格式错误: documentId={}", documentId, e);
                return ResponseEntity.badRequest()
                        .body(DocumentProcessResponse.error("文档ID格式错误: " + documentId));
            }

            // 验证文档是否存在
            Document document = documentService.getById(docId);
            if (document == null) {
                return ResponseEntity.badRequest()
                        .body(DocumentProcessResponse.error("文档不存在或已被删除: " + documentId));
            }

            // 异步启动文档处理，添加超时机制
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    documentProcessorService.processDocument(docId);
                    log.info("文档分块处理完成: documentId={}", docId);
                } catch (Exception e) {
                    log.error("文档分块处理失败: documentId={}", docId, e);
                    document.updateStatus(Document.Status.FAILED);
                    documentService.updateById(document);
                }
            });

            // 添加超时处理（5分钟超时）
            future.orTimeout(5, TimeUnit.MINUTES).exceptionally(throwable -> {
                log.error("文档分块处理超时: documentId={}", docId, throwable);
                Document failedDoc = documentService.getById(docId);
                if (failedDoc != null && failedDoc.getStatus() == Document.Status.PROCESSING) {
                    failedDoc.updateStatus(Document.Status.FAILED);
                    documentService.updateById(failedDoc);
                }
                return null;
            });

            // 返回响应
            return ResponseEntity.ok(DocumentProcessResponse.success(docId, document));

        } catch (Exception e) {
            log.error("文档分块处理请求失败", e);
            return ResponseEntity.internalServerError()
                    .body(DocumentProcessResponse.error("文档分块处理失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前登录用户ID
     * @return 当前用户ID，如果未登录则返回默认值1（开发环境用）
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("认证信息: authentication={}, isAuthenticated={}, principal={}, principalClass={}",
                authentication,
                authentication != null ? authentication.isAuthenticated() : null,
                authentication != null ? authentication.getPrincipal() : null,
                authentication != null && authentication.getPrincipal() != null ?
                    authentication.getPrincipal().getClass().getName() : "null");

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            log.info("获取到当前用户ID: {}", user.getId());
            return user.getId();
        } else {
            // 开发环境下，如果没有认证信息，使用默认用户ID（admin用户）
            log.warn("无法获取当前用户ID，使用默认用户ID=1（开发环境）");
            return 1L;
        }
    }
}