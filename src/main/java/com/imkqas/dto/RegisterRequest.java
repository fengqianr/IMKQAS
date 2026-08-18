package com.imkqas.dto;

import com.imkqas.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户注册请求
 */
@Data
@Schema(description = "用户注册请求")
public class RegisterRequest {

    @Schema(description = "用户名", required = true)
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "真实姓名", required = true)
    @NotBlank(message = "真实姓名不能为空")
    private String name;

    @Schema(description = "手机号", required = true)
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "密码", required = true)
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "确认密码", required = true)
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    // 角色白名单：医生/管理员账号需由管理员在用户管理中创建，禁止自助注册
    @Schema(description = "用户角色（医生/管理员需由管理员创建，不可自助注册）",
            allowableValues = {"PATIENT", "STUDENT", "NURSE", "HEALTH_MANAGER"})
    private User.Role role;
}