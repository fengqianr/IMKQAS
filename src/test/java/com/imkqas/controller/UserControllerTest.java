package com.imkqas.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.imkqas.dto.user.HealthProfileRequest;
import com.imkqas.entity.User;
import com.imkqas.service.common.UserService;
import com.imkqas.service.his.FhirPatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private UserController userController;

    @Mock
    private UserService userService;

    @Mock
    private FhirPatientService fhirPatientService;

    // 带 JavaTimeModule 的 ObjectMapper：与生产注入一致，用于解析 identity 中的 LocalDate 出生日期
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        userController = new UserController(userService, fhirPatientService, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
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
    void testUpdateHealthProfile_IdentityFallback() throws Exception {
        // 准备测试数据：用户已有个人身份信息（含出生日期），健康档案待创建
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setIdentity("{\"name\":\"张三\",\"gender\":\"MALE\",\"birthDate\":\"2000-01-01\"}");

        // 请求仅提交病史，人口学字段缺失，应回退个人中心
        HealthProfileRequest request = new HealthProfileRequest();
        request.setAllergies(Arrays.asList("青霉素"));

        when(userService.getById(1L)).thenReturn(user);
        when(userService.updateById(any(User.class))).thenReturn(true);

        // 执行请求
        mockMvc.perform(put("/api/users/1/health-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证保存的健康档案已由 identity 兜底姓名/性别/年龄
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).updateById(captor.capture());
        JsonNode hp = objectMapper.readTree(captor.getValue().getHealthProfile());
        assertEquals("张三", hp.get("name").asText());
        assertEquals("MALE", hp.get("gender").asText());
        // 年龄由出生日期计算（2000-01-01 至今）
        int expectedAge = Period.between(LocalDate.of(2000, 1, 1), LocalDate.now()).getYears();
        assertEquals(expectedAge, hp.get("age").asInt());
    }

    @Test
    void testUpdateHealthProfile_OldProfileAgeFallback() throws Exception {
        // 准备测试数据：identity 无出生日期（个人中心未填），原档案手填年龄 30
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setIdentity("{\"name\":\"张三\",\"gender\":\"MALE\"}");
        user.setHealthProfile("{\"age\":30,\"name\":\"旧名\",\"gender\":\"MALE\"}");

        // 请求仅提交病史，人口学字段缺失
        HealthProfileRequest request = new HealthProfileRequest();
        request.setAllergies(Arrays.asList("青霉素"));

        when(userService.getById(1L)).thenReturn(user);
        when(userService.updateById(any(User.class))).thenReturn(true);

        // 执行请求
        mockMvc.perform(put("/api/users/1/health-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 姓名/性别来自 identity，年龄回退到原档案的 30
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).updateById(captor.capture());
        JsonNode hp = objectMapper.readTree(captor.getValue().getHealthProfile());
        assertEquals("张三", hp.get("name").asText());
        assertEquals("MALE", hp.get("gender").asText());
        assertEquals(30, hp.get("age").asInt());
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