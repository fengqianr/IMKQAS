package com.student.entity.synonym;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 医学同义词映射表实体
 * 存储口语表达到标准医学术语的映射关系
 *
 * @author 系统
 * @version 1.0
 */
@Data
@TableName("medical_synonym_mapping")
public class MedicalSynonymMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 口语化表达（如：扑热息痛） */
    @TableField("colloquial_term")
    private String colloquialTerm;

    /** 标准医学术语（如：对乙酰氨基酚） */
    @TableField("standard_term")
    private String standardTerm;

    /** SNOMED CT 概念ID */
    @TableField("snomed_concept_id")
    private String snomedConceptId;

    /** 实体类型：DRUG/DISEASE/SYMPTOM/POPULATION/BODY_PART/EXAMINATION/TREATMENT */
    @TableField("entity_type")
    private String entityType;

    /** 来源：MANUAL(人工标注)/HISTORY_LOG(历史日志)/PUBLIC_DATASET(公开数据集)/LLM_INFERRED(LLM推断) */
    @TableField("source")
    private String source;

    /** 审核状态：PENDING(待审核)/APPROVED(已通过)/REJECTED(已拒绝) */
    @TableField("status")
    private String status;

    /** 审核人 */
    @TableField("reviewer")
    private String reviewer;

    /** 置信度 0.0-1.0 */
    @TableField("confidence")
    private Double confidence;

    /** 使用次数 */
    @TableField("usage_count")
    private Integer usageCount;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
