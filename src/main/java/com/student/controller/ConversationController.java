package com.student.controller;

import com.student.entity.Conversation;
import com.student.service.common.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

/**
 * 对话会话控制器
 * 提供对话会话相关的RESTful API接口
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService service;

    /**
     * 创建对话会话
     * @param entity 对话会话实体
     * @return 创建的对话会话
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation create(@RequestBody Conversation entity) {
        service.save(entity);
        return entity;
    }

    /**
     * 更新对话会话
     * @param id 对话会话ID
     * @param entity 对话会话实体
     * @return 更新后的对话会话
     */
    @PutMapping("/{id}")
    public Conversation update(@PathVariable Long id, @RequestBody Conversation entity) {
        entity.setId(id);
        service.updateById(entity);
        return entity;
    }

    /**
     * 删除对话会话
     * @param id 对话会话ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    /**
     * 根据ID查询对话会话
     * @param id 对话会话ID
     * @return 对话会话实体
     */
    @GetMapping("/{id}")
    public Conversation getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 分页查询对话会话列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public Page<Conversation> list(@RequestParam(defaultValue = "1") int current,
                                   @RequestParam(defaultValue = "10") int size) {
        Page<Conversation> page = new Page<>(current, size);
        return service.page(page);
    }

    /**
     * 根据用户ID查询对话会话
     * @param userId 用户ID
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/by-user/{userId}")
    public Page<Conversation> listByUser(@PathVariable Long userId,
                                         @RequestParam(defaultValue = "1") int current,
                                         @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByDesc("created_at");
        return service.page(new Page<>(current, size), wrapper);
    }

    /**
     * 搜索对话会话
     * @param keyword 搜索关键词
     * @param userId 用户ID
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/search")
    public Page<Conversation> search(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(defaultValue = "1") int current,
                                     @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("title", keyword);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByDesc("created_at");
        return service.page(new Page<>(current, size), wrapper);
    }
}