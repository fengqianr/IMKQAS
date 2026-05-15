package com.student.entity.synonym;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 未映射词条记录实体
 * 存储SNOMED CT也无法匹配的口语表达，等待人工审核
 *
 * @author 系统
 * @version 1.0
 */
@Data
@TableName("unmapped_term_queue")
public class UnmappedTermRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 未映射的词条 */
    @TableField("term")
    private String term;

    /** 出现的上下文（原始查询） */
    @TableField("context_query")
    private String contextQuery;

    /** 推测的实体类型 */
    @TableField("guessed_entity_type")
    private String guessedEntityType;

    /** LLM临时推断结果（可能为空） */
    @TableField("llm_guess")
    private String llmGuess;

    /** LLM推断置信度 */
    @TableField("llm_confidence")
    private Double llmConfidence;

    /** 出现次数 */
    @TableField("occurrence_count")
    private Integer occurrenceCount;

    /** 状态：PENDING(待审核)/APPROVED(已加入映射表)/REJECTED(已拒绝)/AUTO_RESOLVED(自动解决) */
    @TableField("status")
    private String status;

    /** 审核人 */
    @TableField("reviewer")
    private String reviewer;

    /** 审核备注 */
    @TableField("review_note")
    private String reviewNote;

    /** 首次出现时间 */
    @TableField(value = "first_seen_at", fill = FieldFill.INSERT)
    private LocalDateTime firstSeenAt;

    /** 最近出现时间 */
    @TableField(value = "last_seen_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastSeenAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
