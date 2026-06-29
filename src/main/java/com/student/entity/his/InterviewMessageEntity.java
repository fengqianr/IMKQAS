package com.student.entity.his;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 访谈对话消息持久化实体
 * 对应数据库表: interview_messages
 *
 * @author 系统
 * @version 1.0
 */
@TableName("interview_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private Long userId;

    @TableField("message_type")
    private String messageType;

    @TableField("sequence_num")
    private Integer sequenceNum;

    @TableField("questionnaire_id")
    private String questionnaireId;

    @TableField("questionnaire_title")
    private String questionnaireTitle;

    @TableField("message_data")
    private String messageData;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
