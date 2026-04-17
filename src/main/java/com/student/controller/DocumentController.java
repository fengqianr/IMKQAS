package com.student.controller;

import com.student.entity.Document;
import com.student.service.document.DocumentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

/**
 * 文档控制器
 * 提供文档相关的RESTful API接口
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService service;

    /**
     * 创建文档
     * @param entity 文档实体
     * @return 创建的文档
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document create(@RequestBody Document entity) {
        service.save(entity);
        return entity;
    }

    /**
     * 更新文档
     * @param id 文档ID
     * @param entity 文档实体
     * @return 更新后的文档
     */
    @PutMapping("/{id}")
    public Document update(@PathVariable Long id, @RequestBody Document entity) {
        entity.setId(id);
        service.updateById(entity);
        return entity;
    }

    /**
     * 删除文档
     * @param id 文档ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    /**
     * 根据ID查询文档
     * @param id 文档ID
     * @return 文档实体
     */
    @GetMapping("/{id}")
    public Document getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 分页查询文档列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public Page<Document> list(@RequestParam(defaultValue = "1") int current,
                               @RequestParam(defaultValue = "10") int size) {
        Page<Document> page = new Page<>(current, size);
        return service.page(page);
    }

    /**
     * 搜索文档
     * @param keyword 搜索关键词
     * @param category 文档分类
     * @param status 文档状态
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/search")
    public Page<Document> search(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "1") int current,
                                 @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Document> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("title", keyword);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        return service.page(new Page<>(current, size), wrapper);
    }
}