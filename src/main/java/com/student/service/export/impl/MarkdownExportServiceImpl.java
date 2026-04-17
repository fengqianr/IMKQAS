package com.student.service.export.impl;

import com.student.entity.Conversation;
import com.student.entity.Message;
import com.student.service.export.ConversationExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Markdown导出服务实现
 */
@Service
@Slf4j
public class MarkdownExportServiceImpl implements ConversationExportService {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void exportConversation(Conversation conversation, List<Message> messages,
                                  ExportFormat format, OutputStream outputStream) {
        try (PrintWriter writer = new PrintWriter(outputStream)) {

            // 标题
            writer.println("# " + conversation.getTitle());
            writer.println();

            // 元数据
            writer.println("**会话信息**");
            writer.println("- 创建时间: " + conversation.getCreatedAt().format(DATE_FORMATTER));
            writer.println("- 用户ID: " + conversation.getUserId());
            writer.println();

            // 消息
            writer.println("## 对话记录");
            writer.println();

            for (Message message : messages) {
                String rolePrefix = "**" + message.getRole() + "**";
                String timeStr = message.getCreatedAt().format(DATE_FORMATTER);

                writer.println("### " + rolePrefix + " (" + timeStr + ")");
                writer.println();
                writer.println(message.getContent());
                writer.println();
            }

            // 页脚
            writer.println("---");
            writer.println("*导出时间: " +
                java.time.LocalDateTime.now().format(DATE_FORMATTER) + "*");

            writer.flush();
            log.info("Markdown导出完成: conversationId={}, messageCount={}",
                    conversation.getId(), messages.size());

        } catch (Exception e) {
            log.error("Markdown导出失败", e);
            throw new RuntimeException("Markdown导出失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void exportConversations(List<Conversation> conversations, ExportFormat format,
                                   OutputStream outputStream) {
        try (PrintWriter writer = new PrintWriter(outputStream)) {

            writer.println("# 医疗问答会话批量导出");
            writer.println();
            writer.println("共 " + conversations.size() + " 个会话");
            writer.println();

            for (int i = 0; i < conversations.size(); i++) {
                Conversation conv = conversations.get(i);
                writer.println("## " + (i + 1) + ". " + conv.getTitle());
                writer.println("- 创建时间: " + conv.getCreatedAt().format(DATE_FORMATTER));
                writer.println("- 用户ID: " + conv.getUserId());
                writer.println();
            }

            writer.flush();

        } catch (Exception e) {
            log.error("批量Markdown导出失败", e);
            throw new RuntimeException("批量Markdown导出失败", e);
        }
    }

    @Override
    public List<ExportFormat> getSupportedFormats() {
        return List.of(ExportFormat.MARKDOWN);
    }
}