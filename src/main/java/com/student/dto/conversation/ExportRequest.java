package com.student.dto.conversation;

import com.student.service.export.ConversationExportService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话导出请求
 */
@Data
@Schema(description = "会话导出请求")
public class ExportRequest {

    @Schema(description = "会话ID", required = true)
    private Long conversationId;

    @Schema(description = "导出格式", allowableValues = {"PDF", "MARKDOWN", "TXT"}, defaultValue = "PDF")
    private ConversationExportService.ExportFormat format = ConversationExportService.ExportFormat.PDF;

    @Schema(description = "是否包含消息时间戳", defaultValue = "true")
    private boolean includeTimestamps = true;

    @Schema(description = "是否包含元数据", defaultValue = "true")
    private boolean includeMetadata = true;
}