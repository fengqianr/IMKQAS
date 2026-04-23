package com.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 对话消息实体类
 * 对应数据库表: messages
 * 存储对话中的用户提问和系统回答
 *
 * @author 系统
 * @version 1.0
 */
@TableName("messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @TableField("conversation_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long conversationId; // 对话ID

    @TableField("role")
    private Role role;

    @TableField("content")
    private String content; // 消息内容

    @TableField("source_references")
    private String sourceReferences; // 引用的文档chunk IDs: [{"chunkId": 1, "documentId": 1, "page": 42}]

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted = 0;


    /**
     * 消息角色枚举
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Role {
        USER("user"),      // 用户提问
        ASSISTANT("assistant");  // 系统回答

        private final String value;

        Role(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Role fromString(String value) {
            if (value == null) {
                return null;
            }
            // 忽略大小写匹配
            for (Role role : Role.values()) {
                if (role.value.equalsIgnoreCase(value)) {
                    return role;
                }
            }
            // 如果直接匹配枚举名（大写）
            try {
                return Role.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("无效的角色类型: " + value);
            }
        }
    }

    /**
     * 判断是否为用户消息
     * @return 是否为用户消息
     */
    public boolean isUserMessage() {
        return this.role == Role.USER;
    }

    /**
     * 判断是否为系统消息
     * @return 是否为系统消息
     */
    public boolean isAssistantMessage() {
        return this.role == Role.ASSISTANT;
    }

    /**
     * 获取格式化创建时间
     * @return 格式化时间字符串
     */
    public String getFormattedCreatedAt() {
        return createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 判断是否有来源引用
     * @return 是否有来源引用
     */
    public boolean hasSourceReferences() {
        return sourceReferences != null && !sourceReferences.trim().isEmpty();
    }

    /**
     * 添加来源引用
     * @param chunkId 文档分块ID
     * @param documentId 文档ID
     * @param page 页码
     */
    public void addSourceReference(Long chunkId, Long documentId, Integer page) {
        // 简化实现，实际应使用JSON库构建JSON数组
        if (sourceReferences == null || sourceReferences.trim().isEmpty()) {
            sourceReferences = "[";
        } else if (!sourceReferences.endsWith("]")) {
            sourceReferences = sourceReferences.substring(0, sourceReferences.length() - 1) + ",";
        } else {
            sourceReferences = sourceReferences.substring(0, sourceReferences.length() - 1) + ",";
        }

        String reference = String.format(
            "{\"chunkId\":%d,\"documentId\":%d,\"page\":%d}",
            chunkId, documentId, page
        );

        if (sourceReferences.equals("[")) {
            sourceReferences += reference + "]";
        } else {
            sourceReferences += reference + "]";
        }
    }
}