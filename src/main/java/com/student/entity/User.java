package com.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

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
public class User implements UserDetails {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("phone")
    private String phone;

    @TableField("role")
    private Role role = Role.USER;

    @TableField("password")
    private String password;

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

    // UserDetails接口方法实现

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 将角色转换为GrantedAuthority格式
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        // 账户是否未过期
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 账户是否未锁定
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // 凭证是否未过期
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 账户是否启用（未删除）
        return this.deleted == 0;
    }

    /**
     * UserDetails接口要求的方法，返回用户名
     * 这里使用username字段
     */
    @Override
    public String getUsername() {
        return this.username;
    }
}