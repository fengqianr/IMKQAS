package com.student.entity.his;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * FHIR观察资源缓存实体
 * 对应数据库表: fhir_observation_cache
 *
 * @author 系统
 * @version 1.0
 */
@TableName("fhir_observation_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirObservationCache {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("fhir_id")
    private String fhirId;

    @TableField("patient_fhir_id")
    private String patientFhirId;

    @TableField("code_system")
    private String codeSystem;

    @TableField("code_value")
    private String codeValue;

    @TableField("code_display")
    private String codeDisplay;

    @TableField("category")
    private String category;

    @TableField("value_type")
    private String valueType;

    @TableField("value_quantity")
    private Double valueQuantity;

    @TableField("value_unit")
    private String valueUnit;

    @TableField("value_string")
    private String valueString;

    @TableField("value_code")
    private String valueCode;

    @TableField("effective_date_time")
    private LocalDateTime effectiveDateTime;

    @TableField("status")
    private String status;

    @TableField("resource_json")
    private String resourceJson;

    @TableField("version_id")
    private String versionId;

    @TableField("last_updated")
    private LocalDateTime lastUpdated;

    @TableField("source")
    private String source;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
