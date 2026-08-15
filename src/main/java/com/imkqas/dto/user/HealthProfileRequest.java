package com.imkqas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 健康档案请求
 *
 * 人口学字段（姓名/年龄/性别）为可选：由个人中心（users.identity）统一维护，
 * 健康档案页仅编辑病史。请求缺省人口学字段时，后端以 identity 兜底填充
 * （详见 UserController.updateHealthProfile）。
 */
@Data
@Schema(description = "健康档案请求")
public class HealthProfileRequest {

    @Schema(description = "姓名（可选，缺省时以个人中心身份信息兜底）")
    private String name;

    @Schema(description = "年龄（可选，缺省时以个人中心出生日期计算兜底）")
    @Min(0)
    private Integer age;

    @Schema(description = "性别（可选，缺省时以个人中心身份信息兜底）", allowableValues = {"MALE", "FEMALE", "OTHER"})
    private String gender;

    @Schema(description = "过敏史")
    private List<String> allergies;

    @Schema(description = "慢性病史")
    private List<String> chronicDiseases;

    @Schema(description = "用药史")
    private List<String> medicationHistory;

    @Schema(description = "手术史")
    private List<String> surgicalHistory;

    @Schema(description = "家族病史")
    private List<String> familyHistory;
}