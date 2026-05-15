package com.student.entity.his;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * FHIR患者资源缓存实体
 * 对应数据库表: fhir_patient_cache
 *
 * @author 系统
 * @version 1.0
 */
@TableName("fhir_patient_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirPatientCache {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("fhir_id")
    private String fhirId;

    @TableField("identifier_system")
    private String identifierSystem;

    @TableField("identifier_value")
    private String identifierValue;

    @TableField("family_name")
    private String familyName;

    @TableField("given_name")
    private String givenName;

    @TableField("gender")
    private String gender;

    @TableField("birth_date")
    private LocalDate birthDate;

    @TableField("phone")
    private String phone;

    @TableField("address_text")
    private String addressText;

    @TableField("marital_status")
    private String maritalStatus;

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

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
