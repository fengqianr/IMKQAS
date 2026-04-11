package com.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 对话会话实体类
 * 对应数据库表: conversations
 * 存储用户的对话会话信息
 *
 * @author 系统
 * @version 1.0
 */
@TableName("conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId; // 用户ID

    @TableField("title")
    private String title; // 会话标题

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted = 0;


    /**
     * 更新会话标题
     * @param title 新标题
     */
    public void updateTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title;
        }
    }

    /**
     * 获取格式化创建时间
     * @return 格式化时间字符串
     */
    public String getFormattedCreatedAt() {
        return createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}