package com.student.service;

import com.student.entity.User;
import com.student.mapper.UserMapper;
import com.student.service.impl.UserServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 用户服务单元测试
 * 测试UserService的各种方法
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .phone("13800000000")
                .role(User.Role.USER)
                .healthProfile("{\"age\": 30, \"gender\": \"male\"}")
                .deleted(0)
                .build();

        // 设置baseMapper，使MyBatis Plus的Service能够使用mock的mapper
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    @Test
    void testFindByPhone_Success() {
        // 准备
        String phone = "13800000000";
        when(userMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(testUser);

        // 执行
        User result = userService.findByPhone(phone);

        // 验证
        assertNotNull(result);
        assertEquals(phone, result.getPhone());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void testFindByPhone_NotFound() {
        // 准备
        String phone = "13900000000";
        when(userMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        // 执行
        User result = userService.findByPhone(phone);

        // 验证
        assertNull(result);
    }

    @Test
    void testFindByPhone_NullPhone() {
        // 执行
        User result = userService.findByPhone(null);

        // 验证
        assertNull(result);
    }

    @Test
    void testFindByPhone_EmptyPhone() {
        // 执行
        User result = userService.findByPhone("");

        // 验证
        assertNull(result);
    }

    @Test
    void testFindByUsername_Success() {
        // 准备
        String username = "testuser";
        when(userMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(testUser);

        // 执行
        User result = userService.findByUsername(username);

        // 验证
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertEquals("13800000000", result.getPhone());
    }

    @Test
    void testIsPhoneRegistered_True() {
        // 准备
        String phone = "13800000000";
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        // 执行
        boolean result = userService.isPhoneRegistered(phone);

        // 验证
        assertTrue(result);
    }

    @Test
    void testIsPhoneRegistered_False() {
        // 准备
        String phone = "13900000000";
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        // 执行
        boolean result = userService.isPhoneRegistered(phone);

        // 验证
        assertFalse(result);
    }

    @Test
    void testIsUsernameExists_True() {
        // 准备
        String username = "testuser";
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        // 执行
        boolean result = userService.isUsernameExists(username);

        // 验证
        assertTrue(result);
    }

    @Test
    void testIsUsernameExists_False() {
        // 准备
        String username = "nonexistent";
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        // 执行
        boolean result = userService.isUsernameExists(username);

        // 验证
        assertFalse(result);
    }

    @Test
    void testGetById_Success() {
        // 准备
        Long userId = 1L;
        when(userMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(testUser);

        // 执行
        User result = userService.getById(userId);

        // 验证
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void testGetById_NotFound() {
        // 准备
        Long userId = 999L;
        when(userMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        // 执行
        User result = userService.getById(userId);

        // 验证
        assertNull(result);
    }

    @Test
    void testGetById_NullId() {
        // 执行
        User result = userService.getById(null);

        // 验证
        assertNull(result);
    }
}