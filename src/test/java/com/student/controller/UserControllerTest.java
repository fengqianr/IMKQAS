package com.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.dto.user.HealthProfileRequest;
import com.student.entity.User;
import com.student.service.common.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController单元测试
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private UserController userController;

    @Mock
    private UserService userService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testUpdateHealthProfile_Success() throws Exception {
        // 准备测试数据
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        HealthProfileRequest request = new HealthProfileRequest();
        request.setAge(30);
        request.setGender("MALE");
        request.setAllergies(Arrays.asList("青霉素", "海鲜"));

        when(userService.getById(1L)).thenReturn(user);
        when(userService.updateById(any(User.class))).thenReturn(true);

        // 执行请求
        mockMvc.perform(put("/api/users/1/health-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("健康档案更新成功"));
    }

    @Test
    void testUpdateHealthProfile_UserNotFound() throws Exception {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setAge(30);
        request.setGender("MALE");

        when(userService.getById(999L)).thenReturn(null);

        mockMvc.perform(put("/api/users/999/health-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHealthProfile_Exists() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setHealthProfile("{\"age\":30,\"gender\":\"MALE\"}");

        when(userService.getById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1/health-profile")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHealthProfile").value(true))
                .andExpect(jsonPath("$.healthProfile.age").value(30))
                .andExpect(jsonPath("$.healthProfile.gender").value("MALE"));
    }

    @Test
    void testGetHealthProfile_NoHealthProfile() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setHealthProfile(null);

        when(userService.getById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1/health-profile")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHealthProfile").value(false))
                .andExpect(jsonPath("$.message").value("用户未设置健康档案"));
    }

    @Test
    void testGetHealthProfile_UserNotFound() throws Exception {
        when(userService.getById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/users/999/health-profile")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteHealthProfile_Success() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(userService.getById(1L)).thenReturn(user);
        when(userService.updateById(any(User.class))).thenReturn(true);

        mockMvc.perform(delete("/api/users/1/health-profile")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("健康档案删除成功"));
    }

    @Test
    void testDeleteHealthProfile_UserNotFound() throws Exception {
        when(userService.getById(999L)).thenReturn(null);

        mockMvc.perform(delete("/api/users/999/health-profile")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}