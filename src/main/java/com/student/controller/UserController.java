package com.student.controller;

import com.student.entity.User;
import com.student.service.common.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.dto.user.HealthProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * 用户控制器
 * 提供用户相关的RESTful API接口
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    /**
     * 创建用户
     * @param entity 用户实体
     * @return 创建的用户
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@RequestBody User entity) {
        service.save(entity);
        return entity;
    }

    /**
     * 更新用户
     * @param id 用户ID
     * @param entity 用户实体
     * @return 更新后的用户
     */
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User entity) {
        entity.setId(id);
        service.updateById(entity);
        return entity;
    }

    /**
     * 删除用户
     * @param id 用户ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户实体
     */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 分页查询用户列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public Page<User> list(@RequestParam(defaultValue = "1") int current,
                           @RequestParam(defaultValue = "10") int size) {
        Page<User> page = new Page<>(current, size);
        return service.page(page);
    }

    /**
     * 搜索用户
     * @param keyword 搜索关键词
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/search")
    public Page<User> search(@RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "1") int current,
                             @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("username", keyword)
                   .or().like("phone", keyword);
        }
        return service.page(new Page<>(current, size), wrapper);
    }

    /**
     * 更新用户健康档案
     * @param userId 用户ID
     * @param request 健康档案请求
     * @return 更新结果
     */
    @PutMapping("/{userId}/health-profile")
    public ResponseEntity<?> updateHealthProfile(
            @PathVariable Long userId,
            @Valid @RequestBody HealthProfileRequest request) {
        User user = service.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // 将健康档案转换为JSON字符串
        ObjectMapper mapper = new ObjectMapper();
        try {
            String healthProfileJson = mapper.writeValueAsString(request);
            user.updateHealthProfile(healthProfileJson);
            service.updateById(user);

            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "健康档案更新成功"
            ));
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "健康档案格式错误"
            ));
        }
    }

    /**
     * 获取用户健康档案
     * @param userId 用户ID
     * @return 健康档案
     */
    @GetMapping("/{userId}/health-profile")
    public ResponseEntity<?> getHealthProfile(@PathVariable Long userId) {
        User user = service.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (user.getHealthProfile() == null || user.getHealthProfile().isEmpty()) {
            return ResponseEntity.ok().body(Map.of(
                "hasHealthProfile", false,
                "message", "用户未设置健康档案"
            ));
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Object healthProfile = mapper.readValue(user.getHealthProfile(), Object.class);

            return ResponseEntity.ok().body(Map.of(
                "hasHealthProfile", true,
                "healthProfile", healthProfile
            ));
        } catch (JsonProcessingException e) {
            return ResponseEntity.ok().body(Map.of(
                "hasHealthProfile", false,
                "message", "健康档案解析失败"
            ));
        }
    }

    /**
     * 删除用户健康档案
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{userId}/health-profile")
    public ResponseEntity<?> deleteHealthProfile(@PathVariable Long userId) {
        User user = service.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.updateHealthProfile(null);
        service.updateById(user);

        return ResponseEntity.ok().body(Map.of(
            "success", true,
            "message", "健康档案删除成功"
        ));
    }
}