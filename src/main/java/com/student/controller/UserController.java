package com.student.controller;

import com.student.entity.User;
import com.student.service.common.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

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
}