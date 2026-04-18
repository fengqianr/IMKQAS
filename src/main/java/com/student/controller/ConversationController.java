package com.student.controller;

import com.student.entity.Conversation;
import com.student.entity.Message;
import com.student.service.common.ConversationService;
import com.student.service.common.MessageService;
import com.student.service.export.ConversationExportService;
import com.student.service.export.impl.PdfExportServiceImpl;
import com.student.service.export.impl.MarkdownExportServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 对话会话控制器
 * 提供对话会话相关的RESTful API接口
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService service;
    private final MessageService messageService;
    private final PdfExportServiceImpl pdfExportService;
    private final MarkdownExportServiceImpl markdownExportService;

    /**
     * 创建对话会话
     * @param entity 对话会话实体
     * @return 创建的对话会话
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation create(@RequestBody Conversation entity) {
        service.save(entity);
        return entity;
    }

    /**
     * 更新对话会话
     * @param id 对话会话ID
     * @param entity 对话会话实体
     * @return 更新后的对话会话
     */
    @PutMapping("/{id}")
    public Conversation update(@PathVariable Long id, @RequestBody Conversation entity) {
        entity.setId(id);
        service.updateById(entity);
        return entity;
    }

    /**
     * 删除对话会话
     * @param id 对话会话ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    /**
     * 根据ID查询对话会话
     * @param id 对话会话ID
     * @return 对话会话实体
     */
    @GetMapping("/{id}")
    public Conversation getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 分页查询对话会话列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public Page<Conversation> list(@RequestParam(defaultValue = "1") int current,
                                   @RequestParam(defaultValue = "10") int size) {
        Page<Conversation> page = new Page<>(current, size);
        return service.page(page);
    }

    /**
     * 根据用户ID查询对话会话
     * @param userId 用户ID
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/by-user/{userId}")
    public Page<Conversation> listByUser(@PathVariable Long userId,
                                         @RequestParam(defaultValue = "1") int current,
                                         @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByDesc("created_at");
        return service.page(new Page<>(current, size), wrapper);
    }

    /**
     * 搜索对话会话
     * @param keyword 搜索关键词
     * @param userId 用户ID
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/search")
    public Page<Conversation> search(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(defaultValue = "1") int current,
                                     @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("title", keyword);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByDesc("created_at");
        return service.page(new Page<>(current, size), wrapper);
    }

    /**
     * 导出会话
     * @param conversationId 会话ID
     * @param format 导出格式 (pdf, markdown, txt)
     * @param response HTTP响应
     */
    @GetMapping("/{conversationId}/export")
    public void exportConversation(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "pdf") String format,
            HttpServletResponse response) {

        Conversation conversation = service.getById(conversationId);
        if (conversation == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 获取会话消息
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId);
        wrapper.orderByAsc("created_at");
        List<Message> messages = messageService.list(wrapper);

        // 确定导出格式
        ConversationExportService.ExportFormat exportFormat;
        try {
            exportFormat = ConversationExportService.ExportFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            exportFormat = ConversationExportService.ExportFormat.PDF;
        }

        // 设置响应头
        String contentType;
        String fileName = String.format("conversation-%d-%s.%s",
            conversationId,
            conversation.getTitle().replaceAll("[^a-zA-Z0-9]", "-"),
            format.toLowerCase());

        switch (exportFormat) {
            case PDF:
                contentType = "application/pdf";
                break;
            case MARKDOWN:
                contentType = "text/markdown";
                break;
            case TXT:
                contentType = "text/plain";
                break;
            default:
                contentType = "application/octet-stream";
        }

        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + fileName + "\"");

        try {
            // 选择合适的导出服务
            ConversationExportService exportService;
            if (exportFormat == ConversationExportService.ExportFormat.PDF) {
                exportService = pdfExportService;
            } else {
                exportService = markdownExportService;
            }

            exportService.exportConversation(conversation, messages, exportFormat,
                response.getOutputStream());

        } catch (Exception e) {
            log.error("会话导出失败: conversationId={}, format={}", conversationId, format, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}