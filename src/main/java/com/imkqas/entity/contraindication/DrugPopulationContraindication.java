package com.imkqas.entity.contraindication;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 药物-人群禁忌规则实体类
 * 对应数据库表: drug_population_contraindication
 * 来源于药品说明书和临床指南的权威禁忌知识
 *
 * @author 系统
 * @version 1.0
 */
@TableName("drug_population_contraindication")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugPopulationContraindication {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("drug_name")
    private String drugName;

    @TableField("population_name")
    private String populationName;

    @TableField("contraindication_type")
    private String contraindicationType;

    @TableField("evidence_level")
    private String evidenceLevel;

    @TableField("description")
    private String description;

    @TableField("source")
    private String source;

    @TableField("is_active")
    private Integer isActive;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 禁忌类型枚举 */
    public enum ContraType {
        ABSOLUTE("绝对禁用"),
        RELATIVE("相对禁忌"),
        CAUTION("慎用"),
        UNCLEAR("尚不明确");

        private final String label;

        ContraType(String label) { this.label = label; }
        public String getLabel() { return label; }

        /** 返回降权因子：0=过滤, 0.3=强降权, 0.5=弱降权 */
        public double getDowngradeFactor() {
            return switch (this) {
                case ABSOLUTE -> 0.0;
                case RELATIVE -> 0.3;
                case CAUTION -> 0.5;
                case UNCLEAR -> 0.7;
            };
        }
    }

    /** 证据等级枚举 */
    public enum EvidenceLevel {
        GUIDELINE("临床指南"),
        DRUG_LABEL("药品说明书"),
        RCT("随机对照试验"),
        META_ANALYSIS("荟萃分析"),
        EXPERT_CONSENSUS("专家共识"),
        CASE_REPORT("病例报告");

        private final String label;

        EvidenceLevel(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** 是否激活 */
    public boolean isActive() {
        return isActive != null && isActive == 1;
    }

    /** 将禁忌类型字符串转为枚举 */
    public ContraType getContraType() {
        if (contraindicationType == null) return ContraType.UNCLEAR;
        try {
            return ContraType.valueOf(contraindicationType);
        } catch (IllegalArgumentException e) {
            return ContraType.UNCLEAR;
        }
    }
}
