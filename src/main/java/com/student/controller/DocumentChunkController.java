package com.student.controller;

import com.student.entity.DocumentChunk;
import com.student.service.document.DocumentChunkService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.student.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

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
    public ApiResponse<DocumentChunk> create(@RequestBody DocumentChunk entity) {
        service.save(entity);
        return ApiResponse.success(entity);
    }

    /**
     * 更新文档分块
     * @param id 文档分块ID
     * @param entity 文档分块实体
     * @return 更新后的文档分块
     */
    @PutMapping("/{id}")
    public ApiResponse<DocumentChunk> update(@PathVariable Long id, @RequestBody DocumentChunk entity) {
        entity.setId(id);
        service.updateById(entity);
        return ApiResponse.success(entity);
    }

    /**
     * 删除文档分块
     * @param id 文档分块ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return ApiResponse.success();
    }

    /**
     * 根据ID查询文档分块
     * @param id 文档分块ID
     * @return 文档分块实体
     */
    @GetMapping("/{id}")
    public ApiResponse<DocumentChunk> getById(@PathVariable Long id) {
        DocumentChunk chunk = service.getById(id);
        if (chunk != null) {
            return ApiResponse.success(chunk);
        }
        return ApiResponse.error("文档分块不存在", (DocumentChunk) null);
    }

    /**
     * 分页查询文档分块列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<ApiResponse.Pagination<List<DocumentChunk>>> list(@RequestParam(defaultValue = "1") int current,
                                                                         @RequestParam(defaultValue = "10") int size) {
        Page<DocumentChunk> page = new Page<>(current, size);
        Page<DocumentChunk> result = service.page(page);
        return ApiResponse.pagination(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 根据文档ID查询分块
     * @param documentId 文档ID
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/by-document/{documentId}")
    public ApiResponse<ApiResponse.Pagination<List<DocumentChunk>>> listByDocument(@PathVariable Long documentId,
                                                                                   @RequestParam(defaultValue = "1") int current,
                                                                                   @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<DocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId);
        wrapper.orderByAsc("chunk_index");
        Page<DocumentChunk> result = service.page(new Page<>(current, size), wrapper);
        return ApiResponse.pagination(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 清除所有文档分块数据
     * 同时删除 MySQL 中的分块记录、Milvus 中的向量数据，并重置文档的分块计数
     *
     * @return 删除的分块数量
     */
    @DeleteMapping("/clear-all")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Integer> clearAll() {
        int deletedCount = service.clearAllChunks();
        return ApiResponse.success("已清除所有文档分块数据，共 " + deletedCount + " 条", deletedCount);
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
    public ApiResponse<ApiResponse.Pagination<List<DocumentChunk>>> search(@RequestParam(required = false) String keyword,
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
        Page<DocumentChunk> result = service.page(new Page<>(current, size), wrapper);
        return ApiResponse.pagination(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }
}