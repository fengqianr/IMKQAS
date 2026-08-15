package com.imkqas.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 个人身份信息响应
 * GET 身份信息时返回，phone 为只读字段，由后端从 users.phone 注入。
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Schema(description = "个人身份信息响应")
public class IdentityResponse {

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别: MALE/FEMALE/OTHER")
    private String gender;

    @Schema(description = "出生日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @Schema(description = "证件号（身份证）")
    private String idCard;

    @Schema(description = "家庭住址")
    private String address;

    @Schema(description = "联系电话（只读，取自 users.phone）")
    private String phone;
}
