package com.student.entity.drug;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 药物相互作用实体类
 * 对应数据库表: drug_interactions
 *
 * @author 系统
 * @version 1.0
 */
@TableName("drug_interactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugInteraction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("drug_a_id")
    private Long drugAId;

    @TableField("drug_b_id")
    private Long drugBId;

    /**
     * 相互作用类型枚举
     */
    public enum InteractionType {
        CONTRAINDICATED("禁忌合用"),
        SEVERE("严重相互作用"),
        MODERATE("中等相互作用"),
        MILD("轻度相互作用"),
        MONITOR("需要监测"),
        UNKNOWN("未知相互作用");

        private final String description;

        InteractionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @TableField("interaction_type")
    private InteractionType interactionType;

    /**
     * 严重程度枚举
     */
    public enum Severity {
        HIGH("高"),
        MODERATE("中"),
        LOW("低");

        private final String description;

        Severity(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @TableField("severity")
    private Severity severity;

    @TableField("description")
    private String description;

    @TableField("mechanism")
    private String mechanism;

    @TableField("recommendation")
    private String recommendation;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /**
     * 获取相互作用的严重程度描述
     * @return 严重程度描述
     */
    public String getSeverityDescription() {
        return severity != null ? severity.getDescription() : "";
    }

    /**
     * 获取相互作用类型描述
     * @return 相互作用类型描述
     */
    public String getInteractionTypeDescription() {
        return interactionType != null ? interactionType.getDescription() : "";
    }
}