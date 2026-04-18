package com.student.service.export.impl;

import com.student.entity.Conversation;
import com.student.entity.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Markdown导出服务测试
 */
@SpringBootTest
class MarkdownExportServiceImplTest {

    @Autowired
    private MarkdownExportServiceImpl markdownExportService;

    @Test
    void testExportConversation_Markdown() {
        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setTitle("测试会话");
        conversation.setUserId(100L);
        conversation.setCreatedAt(LocalDateTime.now());

        Message message1 = new Message();
        message1.setRole(Message.Role.USER);
        message1.setContent("我最近头疼");
        message1.setCreatedAt(LocalDateTime.now());

        Message message2 = new Message();
        message2.setRole(Message.Role.ASSISTANT);
        message2.setContent("建议您多休息");
        message2.setCreatedAt(LocalDateTime.now());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertDoesNotThrow(() -> {
            markdownExportService.exportConversation(
                conversation,
                Arrays.asList(message1, message2),
                com.student.service.export.ConversationExportService.ExportFormat.MARKDOWN,
                outputStream
            );
        });

        String result = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("# 测试会话"));
        assertTrue(result.contains("**USER**"));
        assertTrue(result.contains("**ASSISTANT**"));
        assertTrue(result.contains("我最近头疼"));
        assertTrue(result.contains("建议您多休息"));
        assertTrue(result.contains("导出时间:"));
    }

    @Test
    void testExportConversations_Markdown() {
        Conversation conversation1 = new Conversation();
        conversation1.setId(1L);
        conversation1.setTitle("会话1");
        conversation1.setUserId(100L);
        conversation1.setCreatedAt(LocalDateTime.now());

        Conversation conversation2 = new Conversation();
        conversation2.setId(2L);
        conversation2.setTitle("会话2");
        conversation2.setUserId(101L);
        conversation2.setCreatedAt(LocalDateTime.now());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertDoesNotThrow(() -> {
            markdownExportService.exportConversations(
                Arrays.asList(conversation1, conversation2),
                com.student.service.export.ConversationExportService.ExportFormat.MARKDOWN,
                outputStream
            );
        });

        String result = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("# 医疗问答会话批量导出"));
        assertTrue(result.contains("共 2 个会话"));
        assertTrue(result.contains("## 1. 会话1"));
        assertTrue(result.contains("## 2. 会话2"));
    }

    @Test
    void testGetSupportedFormats() {
        var formats = markdownExportService.getSupportedFormats();
        assertEquals(1, formats.size());
        assertEquals(
            com.student.service.export.ConversationExportService.ExportFormat.MARKDOWN,
            formats.get(0)
        );
    }
}