package com.student.controller;

import com.student.entity.Message;
import com.student.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

/**
 * 消息控制器
 * 提供消息相关的RESTful API接口
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService service;

    /**
     * 创建消息
     * @param entity 消息实体
     * @return 创建的消息
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Message create(@RequestBody Message entity) {
        service.save(entity);
        return entity;
    }

    /**
     * 更新消息
     * @param id 消息ID
     * @param entity 消息实体
     * @return 更新后的消息
     */
    @PutMapping("/{id}")
    public Message update(@PathVariable Long id, @RequestBody Message entity) {
        entity.setId(id);
        service.updateById(entity);
        return entity;
    }

    /**
     * 删除消息
     * @param id 消息ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    /**
     * 根据ID查询消息
     * @param id 消息ID
     * @return 消息实体
     */
    @GetMapping("/{id}")
    public Message getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 分页查询消息列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public Page<Message> list(@RequestParam(defaultValue = "1") int current,
                              @RequestParam(defaultValue = "10") int size) {
        Page<Message> page = new Page<>(current, size);
        return service.page(page);
    }

    /**
     * 根据对话ID查询消息
     * @param conversationId 对话ID
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/by-conversation/{conversationId}")
    public Page<Message> listByConversation(@PathVariable Long conversationId,
                                            @RequestParam(defaultValue = "1") int current,
                                            @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId);
        wrapper.orderByAsc("created_at");
        return service.page(new Page<>(current, size), wrapper);
    }

    /**
     * 搜索消息
     * @param keyword 搜索关键词
     * @param conversationId 对话ID
     * @param role 消息角色
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/search")
    public Page<Message> search(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) Long conversationId,
                                @RequestParam(required = false) String role,
                                @RequestParam(defaultValue = "1") int current,
                                @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("content", keyword);
        }
        if (conversationId != null) {
            wrapper.eq("conversation_id", conversationId);
        }
        if (role != null && !role.isEmpty()) {
            wrapper.eq("role", role);
        }
        wrapper.orderByAsc("created_at");
        return service.page(new Page<>(current, size), wrapper);
    }
}