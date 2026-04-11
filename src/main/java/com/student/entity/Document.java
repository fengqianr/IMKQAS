package com.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 医学文档实体类
 * 对应数据库表: documents
 * 存储上传的医学文档信息
 *
 * @author 系统
 * @version 1.0
 */
@TableName("documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("title")
    private String title;

    @TableField("category")
    private String category; // 医学分类: 内科/外科/儿科/妇科/神经科/心血管科等

    @TableField("file_path")
    private String filePath; // 文件存储路径

    @TableField("chunk_count")
    private Integer chunkCount = 0; // 文档分块数量

    @TableField("status")
    private Status status = Status.UPLOADED;

    @TableField("uploaded_by")
    private Long uploadedBy; // 上传者用户ID

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted = 0;


    /**
     * 文档处理状态枚举
     */
    public enum Status {
        UPLOADED,    // 已上传
        PROCESSING,  // 处理中
        COMPLETED,   // 处理完成
        FAILED       // 处理失败
    }

    /**
     * 更新文档状态
     * @param status 新状态
     */
    public void updateStatus(Status status) {
        this.status = status;
    }

    /**
     * 增加分块计数
     * @param count 增加的数量
     */
    public void incrementChunkCount(int count) {
        this.chunkCount += count;
    }

    /**
     * 判断文档是否可查询
     * @return 是否可查询
     */
    public boolean isQueryable() {
        return this.status == Status.COMPLETED;
    }
}