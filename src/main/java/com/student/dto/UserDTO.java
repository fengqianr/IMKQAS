package com.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户信息DTO
 * 用于返回用户信息，不包含敏感数据
 *
 * @author 系统
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 用户角色
     */
    private String role;

    /**
     * 健康档案（简化版）
     */
    private String healthProfile;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 从User实体创建UserDTO
     * @param user User实体
     * @return UserDTO
     */
    public static UserDTO fromEntity(com.student.entity.User user) {
        if (user == null) {
            return null;
        }
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getPhone(),
                user.getRole().name(),
                user.getHealthProfile(),
                user.getCreatedAt()
        );
    }

    /**
     * 更新用户信息（用于更新操作）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String username;
        private String phone;
        private String healthProfile;
    }

    /**
     * 创建用户请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String username;
        private String phone;
        private String password;
        private String role;
        private String healthProfile;
    }
}