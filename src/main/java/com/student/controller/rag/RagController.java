package com.student.controller.rag;

import com.student.service.rag.QaService;
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

    @PostMapping("/process-document")
    @Operation(summary = "文档处理", description = "上传并处理文档，提取文本、分块、生成嵌入向量")
    public ResponseEntity<?> processDocument(
            @Parameter(description = "文档文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "文档标题", required = false)
            @RequestParam(required = false) String title,
            @Parameter(description = "文档分类", required = false)
            @RequestParam(required = false) String category) {
        log.info("文档处理请求: fileName={}, size={}, title={}, category={}",
                file.getOriginalFilename(), file.getSize(), title, category);

        // TODO: 实现文档处理逻辑
        // 1. 保存文件到临时位置
        // 2. 提取文本内容
        // 3. 分块处理
        // 4. 生成嵌入向量
        // 5. 存储到向量数据库

        return ResponseEntity.ok("文档处理请求已接收，功能待实现");
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