package com.student.service.impl;

import com.student.entity.Document;
import com.student.mapper.DocumentMapper;
import com.student.service.DocumentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 文档服务实现类
 * 实现文档相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {
    // 如果有额外的业务逻辑，在这里实现
}