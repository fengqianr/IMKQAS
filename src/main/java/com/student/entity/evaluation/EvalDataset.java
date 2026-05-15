package com.student.entity.evaluation;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评估数据集实体
 * 存储评估用的标注数据集元信息
 *
 * @author 系统
 * @version 1.0
 */
@TableName("eval_dataset")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalDataset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("version")
    private String version;

    @TableField("description")
    private String description;

    @TableField("domain")
    private String domain;

    @TableField("difficulty")
    private String difficulty;

    @TableField("total_items")
    private Integer totalItems;

    @TableField("source")
    private String source;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 数据集状态常量 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_REVIEWED = "REVIEWED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 数据集来源常量 */
    public static final String SOURCE_MANUAL = "MANUAL_ANNOTATION";
    public static final String SOURCE_HISTORY = "HISTORY_IMPORT";
    public static final String SOURCE_LLM = "LLM_GENERATED";
}
