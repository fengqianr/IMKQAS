package com.student.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传请求
 */
@Data
@Schema(description = "文档上传请求")
public class DocumentUploadRequest {

    @Schema(description = "文档文件", required = true)
    @NotNull(message = "文档文件不能为空")
    private MultipartFile file;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档分类")
    private String category;

    @Schema(description = "文档描述")
    private String description;
}