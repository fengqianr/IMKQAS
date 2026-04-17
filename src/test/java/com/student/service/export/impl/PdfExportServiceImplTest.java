package com.student.service.export.impl;

import com.student.entity.Conversation;
import com.student.entity.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PdfExportServiceImplTest {

    @Autowired
    private PdfExportServiceImpl pdfExportService;

    @Test
    void testExportConversation_Pdf() {
        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setTitle("Test Session");
        conversation.setUserId(100L);
        conversation.setCreatedAt(LocalDateTime.now());

        Message message1 = new Message();
        message1.setRole(Message.Role.USER);
        message1.setContent("I have a headache recently");
        message1.setCreatedAt(LocalDateTime.now());

        Message message2 = new Message();
        message2.setRole(Message.Role.ASSISTANT);
        message2.setContent("Suggest you rest more");
        message2.setCreatedAt(LocalDateTime.now());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertDoesNotThrow(() -> {
            pdfExportService.exportConversation(
                conversation,
                Arrays.asList(message1, message2),
                com.student.service.export.ConversationExportService.ExportFormat.PDF,
                outputStream
            );
        });

        assertTrue(outputStream.size() > 0);
    }
}