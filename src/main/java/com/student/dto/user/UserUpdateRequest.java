package com.student.dto.user;

import com.student.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户更新请求
 */
@Data
@Schema(description = "用户更新请求")
public class UserUpdateRequest {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "用户角色", allowableValues = {"PATIENT", "STUDENT", "NURSE", "DOCTOR", "HEALTH_MANAGER", "ADMIN"})
    private User.Role role;
}