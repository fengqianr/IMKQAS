package com.student.service.export.impl;

import com.student.entity.Conversation;
import com.student.entity.Message;
import com.student.service.export.ConversationExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF导出服务实现（使用PDFBox）
 */
@Service
@Slf4j
public class PdfExportServiceImpl implements ConversationExportService {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final float MARGIN = 50;
    private static final float FONT_SIZE_TITLE = 18;
    private static final float FONT_SIZE_HEADER = 12;
    private static final float FONT_SIZE_BODY = 10;
    private static final float LINE_HEIGHT = 15;

    @Override
    public void exportConversation(Conversation conversation, List<Message> messages,
                                  ExportFormat format, OutputStream outputStream) {
        PDDocument document = null;
        PDPageContentStream contentStream = null;

        try {
            document = new PDDocument();
            PDPage page = new PDPage();
            document.addPage(page);

            contentStream = new PDPageContentStream(document, page);
            float yPosition = page.getMediaBox().getHeight() - MARGIN;

            // 标题
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), FONT_SIZE_TITLE);
            String title = "Medical Q&A Session Export";
            float titleWidth = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD).getStringWidth(title) / 1000 * FONT_SIZE_TITLE;
            float titleX = (page.getMediaBox().getWidth() - titleWidth) / 2;
            contentStream.beginText();
            contentStream.newLineAtOffset(titleX, yPosition);
            contentStream.showText(title);
            contentStream.endText();

            yPosition -= LINE_HEIGHT * 2;

            // 会话信息
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE_HEADER);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, yPosition);
            contentStream.showText("Session Title: " + conversation.getTitle());
            contentStream.endText();
            yPosition -= LINE_HEIGHT;

            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, yPosition);
            contentStream.showText("Created At: " + conversation.getCreatedAt().format(DATE_FORMATTER));
            contentStream.endText();
            yPosition -= LINE_HEIGHT;

            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, yPosition);
            contentStream.showText("User ID: " + conversation.getUserId());
            contentStream.endText();
            yPosition -= LINE_HEIGHT * 2;

            // 消息标题
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), FONT_SIZE_HEADER);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, yPosition);
            contentStream.showText("Conversation Records:");
            contentStream.endText();
            yPosition -= LINE_HEIGHT;

            // 消息内容
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE_BODY);
            for (Message message : messages) {
                if (yPosition < MARGIN + 50) {
                    // 创建新页面
                    contentStream.close();
                    page = new PDPage();
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    yPosition = page.getMediaBox().getHeight() - MARGIN;
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE_BODY);
                }

                String roleTime = message.getRole() + " (" + message.getCreatedAt().format(DATE_FORMATTER) + ")";
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN, yPosition);
                contentStream.showText(roleTime);
                contentStream.endText();
                yPosition -= LINE_HEIGHT;

                // 消息内容（可能需要换行）
                String content = message.getContent();
                String[] lines = splitText(content, 80);
                for (String line : lines) {
                    if (yPosition < MARGIN + 30) {
                        contentStream.close();
                        page = new PDPage();
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        yPosition = page.getMediaBox().getHeight() - MARGIN;
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE_BODY);
                    }
                    contentStream.beginText();
                    contentStream.newLineAtOffset(MARGIN + 20, yPosition);
                    contentStream.showText(line);
                    contentStream.endText();
                    yPosition -= LINE_HEIGHT;
                }
                yPosition -= LINE_HEIGHT * 0.5; // 消息间间距
            }

            // 页脚
            yPosition = MARGIN;
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
            String footer = "Export Time: " + java.time.LocalDateTime.now().format(DATE_FORMATTER);
            float footerWidth = new PDType1Font(Standard14Fonts.FontName.HELVETICA).getStringWidth(footer) / 1000 * 9;
            float footerX = page.getMediaBox().getWidth() - MARGIN - footerWidth;
            contentStream.beginText();
            contentStream.newLineAtOffset(footerX, yPosition);
            contentStream.showText(footer);
            contentStream.endText();

            contentStream.close();
            document.save(outputStream);
            log.info("PDF导出完成: conversationId={}, messageCount={}",
                    conversation.getId(), messages.size());

        } catch (Exception e) {
            log.error("PDF导出失败", e);
            throw new RuntimeException("PDF导出失败: " + e.getMessage(), e);
        } finally {
            // 手动关闭资源
            if (contentStream != null) {
                try { contentStream.close(); } catch (Exception e) { log.warn("关闭内容流失败", e); }
            }
            if (document != null) {
                try { document.close(); } catch (Exception e) { log.warn("关闭文档失败", e); }
            }
        }
    }

    private String[] splitText(String text, int maxLineLength) {
        if (text == null || text.isEmpty()) {
            return new String[]{""};
        }

        // 简单换行逻辑
        java.util.List<String> lines = new java.util.ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLineLength, text.length());
            if (end < text.length() && text.charAt(end) != ' ') {
                // 回溯到上一个空格
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            lines.add(text.substring(start, end).trim());
            start = end;
        }
        return lines.toArray(new String[0]);
    }

    @Override
    public void exportConversations(List<Conversation> conversations, ExportFormat format,
                                   OutputStream outputStream) {
        // 简化实现：逐个导出
        if (conversations.isEmpty()) {
            return;
        }

        // 如果有多个会话，创建带目录的PDF（简化版只导出第一个）
        if (conversations.size() == 1) {
            // 需要获取消息，这里简化处理
            exportConversation(conversations.get(0), List.of(), format, outputStream);
        } else {
            // 多会话导出（待完善）
            throw new UnsupportedOperationException("多会话PDF导出暂未实现");
        }
    }

    @Override
    public List<ExportFormat> getSupportedFormats() {
        return List.of(ExportFormat.PDF);
    }
}