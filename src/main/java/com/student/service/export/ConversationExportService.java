package com.student.service.export;

import com.student.entity.Conversation;
import com.student.entity.Message;
import java.io.OutputStream;
import java.util.List;

/**
 * 会话导出服务接口
 */
public interface ConversationExportService {

    /**
     * 导出格式枚举
     */
    enum ExportFormat {
        PDF,
        MARKDOWN,
        TXT
    }

    /**
     * 导出单个会话
     * @param conversation 会话
     * @param messages 消息列表
     * @param format 导出格式
     * @param outputStream 输出流
     */
    void exportConversation(Conversation conversation, List<Message> messages,
                           ExportFormat format, OutputStream outputStream);

    /**
     * 导出多个会话
     * @param conversations 会话列表
     * @param format 导出格式
     * @param outputStream 输出流
     */
    void exportConversations(List<Conversation> conversations, ExportFormat format,
                            OutputStream outputStream);

    /**
     * 获取支持的导出格式
     * @return 格式列表
     */
    List<ExportFormat> getSupportedFormats();
}