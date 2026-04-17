package com.student.service.document.impl;

import com.student.entity.DocumentChunk;
import com.student.mapper.DocumentChunkMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.student.service.document.DocumentChunkService;
import org.springframework.stereotype.Service;

/**
 * 文档分块服务实现类
 * 实现文档分块相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunk> implements DocumentChunkService {
    // 如果有额外的业务逻辑，在这里实现
}