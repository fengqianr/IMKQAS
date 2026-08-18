package com.imkqas.service.document;

import com.imkqas.entity.Document;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 文档服务接口
 * 提供文档相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
public interface DocumentService extends IService<Document> {

    /**
     * 级联删除文档：物理删除 MySQL 分块、Milvus 向量、MinIO 文件及文档记录本身
     * @param id 文档ID
     * @return 是否删除成功
     */
    boolean deleteWithCascade(Long id);
}