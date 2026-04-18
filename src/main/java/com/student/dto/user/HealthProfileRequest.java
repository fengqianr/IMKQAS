package com.student.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 健康档案请求
 */
@Data
@Schema(description = "健康档案请求")
public class HealthProfileRequest {

    @Schema(description = "年龄", minimum = "0", maximum = "150")
    @Min(0)
    @NotNull
    private Integer age;

    @Schema(description = "性别", allowableValues = {"MALE", "FEMALE", "OTHER"})
    @NotBlank
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