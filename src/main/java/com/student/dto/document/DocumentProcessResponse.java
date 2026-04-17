package com.student.dto.document;

import com.student.entity.Document;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文档处理响应
 */
@Data
@Schema(description = "文档处理响应")
public class DocumentProcessResponse {

    @Schema(description = "处理是否成功")
    private boolean success;

    @Schema(description = "处理消息")
    private String message;

    @Schema(description = "文档ID")
    private Long documentId;

    @Schema(description = "文档信息")
    private Document document;

    @Schema(description = "处理状态")
    private Document.Status status;

    public static DocumentProcessResponse success(Long documentId, Document document) {
        DocumentProcessResponse response = new DocumentProcessResponse();
        response.setSuccess(true);
        response.setMessage("文档处理已启动");
        response.setDocumentId(documentId);
        response.setDocument(document);
        response.setStatus(Document.Status.PROCESSING);
        return response;
    }

    public static DocumentProcessResponse error(String message) {
        DocumentProcessResponse response = new DocumentProcessResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}