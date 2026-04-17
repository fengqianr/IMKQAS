package com.student.entity.drug;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 药品实体类
 * 对应数据库表: drugs
 *
 * @author 系统
 * @version 1.0
 */
@TableName("drugs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Drug {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("generic_name")
    private String genericName;

    @TableField("brand_name")
    private String brandName;

    @TableField("english_name")
    private String englishName;

    @TableField("drug_class")
    private String drugClass;

    @TableField("dosage_form")
    private String dosageForm;

    @TableField("specification")
    private String specification;

    @TableField("manufacturer")
    private String manufacturer;

    /**
     * 适应症，JSON数组格式存储
     * 示例：["缓解轻度或中度疼痛", "用于感冒、流感等发热疾病的退热"]
     */
    @TableField("indications")
    private String indications;

    /**
     * 禁忌症，JSON数组格式存储
     */
    @TableField("contraindications")
    private String contraindications;

    /**
     * 不良反应，JSON数组格式存储
     */
    @TableField("adverse_reactions")
    private String adverseReactions;

    @TableField("dosage")
    private String dosage;

    @TableField("precautions")
    private String precautions;

    @TableField("storage")
    private String storage;

    @TableField("approval_number")
    private String approvalNumber;

    @TableField("has_interactions")
    private Integer hasInteractions;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /**
     * 判断是否有相互作用信息
     * @return 是否有相互作用信息
     */
    public boolean hasInteractions() {
        return hasInteractions != null && hasInteractions == 1;
    }
}