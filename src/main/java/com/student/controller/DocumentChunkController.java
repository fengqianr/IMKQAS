package com.student.controller;

import com.student.entity.DocumentChunk;
import com.student.service.document.DocumentChunkService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

/**
 * 文档分块控制器
 * 提供文档分块相关的RESTful API接口
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/document-chunks")
@RequiredArgsConstructor
public class DocumentChunkController {

    private final DocumentChunkService service;

    /**
     * 创建文档分块
     * @param entity 文档分块实体
     * @return 创建的文档分块
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentChunk create(@RequestBody DocumentChunk entity) {
        service.save(entity);
        return entity;
    }

    /**
     * 更新文档分块
     * @param id 文档分块ID
     * @param entity 文档分块实体
     * @return 更新后的文档分块
     */
    @PutMapping("/{id}")
    public DocumentChunk update(@PathVariable Long id, @RequestBody DocumentChunk entity) {
        entity.setId(id);
        service.updateById(entity);
        return entity;
    }

    /**
     * 删除文档分块
     * @param id 文档分块ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    /**
     * 根据ID查询文档分块
     * @param id 文档分块ID
     * @return 文档分块实体
     */
    @GetMapping("/{id}")
    public DocumentChunk getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 分页查询文档分块列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public Page<DocumentChunk> list(@RequestParam(defaultValue = "1") int current,
                                    @RequestParam(defaultValue = "10") int size) {
        Page<DocumentChunk> page = new Page<>(current, size);
        return service.page(page);
    }

    /**
     * 根据文档ID查询分块
     * @param documentId 文档ID
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/by-document/{documentId}")
    public Page<DocumentChunk> listByDocument(@PathVariable Long documentId,
                                              @RequestParam(defaultValue = "1") int current,
                                              @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId);
        wrapper.orderByAsc("chunk_index");
        return service.page(new Page<>(current, size), wrapper);
    }

    /**
     * 搜索文档分块
     * @param keyword 搜索关键词
     * @param documentId 文档ID
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/search")
    public Page<DocumentChunk> search(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Long documentId,
                                      @RequestParam(defaultValue = "1") int current,
                                      @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("content", keyword);
        }
        if (documentId != null) {
            wrapper.eq("document_id", documentId);
        }
        return service.page(new Page<>(current, size), wrapper);
    }
}