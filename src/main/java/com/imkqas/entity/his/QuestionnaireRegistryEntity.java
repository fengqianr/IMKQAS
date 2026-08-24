package com.imkqas.entity.his;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 问卷注册表实体
 * 对应数据库表: questionnaire_registry
 * 承载问卷匹配关键词与FHIR访问路径，问卷完整内容由FHIR资源承载
 *
 * @author 系统
 * @version 1.0
 */
@TableName("questionnaire_registry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireRegistryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("questionnaire_id")
    private String questionnaireId;

    @TableField("title")
    private String title;

    @TableField("category")
    private String category;

    @TableField("keywords")
    private String keywords;

    @TableField("fhir_path")
    private String fhirPath;

    @TableField("priority")
    private Integer priority;

    @TableField("status")
    private String status;

    @TableField("item_count")
    private Integer itemCount;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
