package com.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.dto.user.HealthProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController集成测试
 * 测试用户健康档案管理API
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"spring.security.enabled=false"})
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testUpdateHealthProfile_Integration() throws Exception {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setAge(30);
        request.setGender("MALE");
        request.setAllergies(Arrays.asList("青霉素", "海鲜"));

        // 注意：需要先有用户ID 1存在
        mockMvc.perform(put("/api/users/1/health-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("健康档案更新成功"));
    }

    @Test
    void testGetHealthProfile_Integration() throws Exception {
        // 先创建健康档案
        HealthProfileRequest request = new HealthProfileRequest();
        request.setAge(30);
        request.setGender("MALE");

        mockMvc.perform(put("/api/users/1/health-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 获取健康档案
        mockMvc.perform(get("/api/users/1/health-profile")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHealthProfile").value(true))
                .andExpect(jsonPath("$.healthProfile.age").value(30))
                .andExpect(jsonPath("$.healthProfile.gender").value("MALE"));
    }

    @Test
    void testDeleteHealthProfile_Integration() throws Exception {
        // 先创建健康档案
        HealthProfileRequest request = new HealthProfileRequest();
        request.setAge(30);
        request.setGender("MALE");

        mockMvc.perform(put("/api/users/1/health-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 删除健康档案
        mockMvc.perform(delete("/api/users/1/health-profile")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("健康档案删除成功"));

        // 验证已删除
        mockMvc.perform(get("/api/users/1/health-profile")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHealthProfile").value(false));
    }

    @Test
    void testHealthProfile_UserNotFound() throws Exception {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setAge(30);
        request.setGender("MALE");

        // 使用不存在的用户ID
        mockMvc.perform(put("/api/users/99999/health-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}