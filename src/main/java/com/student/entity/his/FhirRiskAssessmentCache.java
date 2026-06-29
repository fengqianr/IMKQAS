package com.student.entity.his;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * FHIR风险评估资源缓存实体
 * 对应数据库表: fhir_risk_assessment_cache
 *
 * @author 系统
 * @version 1.0
 */
@TableName("fhir_risk_assessment_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirRiskAssessmentCache {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("fhir_id")
    private String fhirId;

    @TableField("patient_fhir_id")
    private String patientFhirId;

    @TableField("session_id")
    private String sessionId;

    @TableField("questionnaire_response_ref")
    private String questionnaireResponseRef;

    @TableField("status")
    private String status;

    @TableField("occurrence_date")
    private LocalDateTime occurrenceDate;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("risk_description")
    private String riskDescription;

    @TableField("requires_urgent_attention")
    private Integer requiresUrgentAttention;

    @TableField("resource_json")
    private String resourceJson;

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
