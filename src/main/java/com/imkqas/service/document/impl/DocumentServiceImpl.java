package com.imkqas.service.document.impl;

import com.imkqas.entity.Document;
import com.imkqas.mapper.DocumentChunkMapper;
import com.imkqas.mapper.DocumentMapper;
import com.imkqas.service.dataBase.MilvusService;
import com.imkqas.service.dataBase.MinioService;
import com.imkqas.service.document.DocumentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 文档服务实现类
 * 实现文档相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private final DocumentChunkMapper documentChunkMapper;
    private final MilvusService milvusService;
    private final MinioService minioService;

    @Override
    @Transactional
    public boolean deleteWithCascade(Long id) {
        Document document = getById(id);
        if (document == null) {
            return false;
        }

        // 1. 物理删除 MySQL 分块记录
        int deletedChunks = documentChunkMapper.physicalDeleteByDocumentId(id);
        log.info("级联删除文档分块: documentId={}, deletedChunks={}", id, deletedChunks);

        // 2. 删除 Milvus 向量
        boolean milvusDeleted = milvusService.deleteByDocumentId(id);
        log.info("级联删除 Milvus 向量: documentId={}, success={}", id, milvusDeleted);

        // 3. 删除 MinIO 文件
        String objectName = extractObjectNameFromUrl(document.getFilePath());
        if (objectName != null) {
            boolean fileDeleted = minioService.deleteFile(objectName);
            log.info("级联删除 MinIO 文件: documentId={}, objectName={}, success={}", id, objectName, fileDeleted);
        } else {
            log.warn("无法提取对象名称，跳过 MinIO 文件删除: documentId={}, filePath={}", id, document.getFilePath());
        }

        // 4. 物理删除文档记录
        return removeById(id);
    }

    /**
     * 从 MinIO 预签名 URL 中提取对象名称
     */
    private String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;

        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
                int firstSlash = path.indexOf('/');
                if (firstSlash > 0) {
                    String objectName = path.substring(firstSlash + 1);
                    try {
                        return URLDecoder.decode(objectName, StandardCharsets.UTF_8.name());
                    } catch (Exception e) {
                        return objectName;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析URL失败，尝试备用方法: {}", url, e);
        }

        // 备用方法
        try {
            String path = url;
            int queryIndex = path.indexOf('?');
            if (queryIndex > 0) path = path.substring(0, queryIndex);

            if (path.contains("://")) {
                path = path.substring(path.indexOf("://") + 3);
                int slashIndex = path.indexOf('/');
                if (slashIndex > 0) {
                    path = path.substring(slashIndex + 1);
                }
            }

            int firstSlash = path.indexOf('/');
            if (firstSlash > 0) {
                path = path.substring(firstSlash + 1);
            }

            try {
                return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return path;
            }
        } catch (Exception e) {
            log.error("从URL提取对象名称失败: {}", url, e);
            return null;
        }
    }
}
