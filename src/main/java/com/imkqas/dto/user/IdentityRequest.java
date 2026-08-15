package com.imkqas.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 个人身份信息请求
 * 患者本人维护的身份信息，保存后同步至 fhir_patient_cache 供医生端检索。
 * 注意：不接收手机号，phone 恒取 users.phone。
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Schema(description = "个人身份信息请求")
public class IdentityRequest {

    @Schema(description = "姓名", required = true)
    @NotBlank(message = "姓名不能为空")
    private String name;

    @Schema(description = "性别", allowableValues = {"MALE", "FEMALE", "OTHER"}, required = true)
    @NotBlank(message = "性别不能为空")
    private String gender;

    @Schema(description = "出生日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @Schema(description = "证件号（身份证）")
    private String idCard;

    @Schema(description = "家庭住址")
    private String address;
}
