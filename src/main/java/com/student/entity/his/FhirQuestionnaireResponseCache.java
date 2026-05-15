package com.student.entity.his;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * FHIR问卷回答资源缓存实体
 * 对应数据库表: fhir_questionnaire_response_cache
 *
 * @author 系统
 * @version 1.0
 */
@TableName("fhir_questionnaire_response_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirQuestionnaireResponseCache {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("fhir_id")
    private String fhirId;

    @TableField("patient_fhir_id")
    private String patientFhirId;

    @TableField("questionnaire_id")
    private String questionnaireId;

    @TableField("questionnaire_title")
    private String questionnaireTitle;

    @TableField("status")
    private String status;

    @TableField("authored_date")
    private LocalDateTime authoredDate;

    @TableField("total_score")
    private Double totalScore;

    @TableField("score_interpretation")
    private String scoreInterpretation;

    @TableField("item_count")
    private Integer itemCount;

    @TableField("answered_count")
    private Integer answeredCount;

    @TableField("resource_json")
    private String resourceJson;

    @TableField("version_id")
    private String versionId;

    @TableField("last_updated")
    private LocalDateTime lastUpdated;

    @TableField("source")
    private String source;

    @TableField("local_user_id")
    private Long localUserId;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
