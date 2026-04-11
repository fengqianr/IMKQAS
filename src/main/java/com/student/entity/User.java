package com.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 对应数据库表: users
 *
 * @author 系统
 * @version 1.0
 */
@TableName("users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("phone")
    private String phone;

    @TableField("role")
    private Role role = Role.USER;

    @TableField("health_profile")
    private String healthProfile; // 健康档案JSON: {age, gender, allergies, chronic_diseases}

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted = 0;


    /**
     * 用户角色枚举
     */
    public enum Role {
        USER,       // 普通用户
        ADMIN       // 管理员
    }

    /**
     * 更新健康档案
     * @param healthProfile 健康档案JSON字符串
     */
    public void updateHealthProfile(String healthProfile) {
        this.healthProfile = healthProfile;
    }

    /**
     * 判断是否为管理员
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }
}