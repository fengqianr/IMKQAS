package com.student.service.document.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.student.entity.Document;
import com.student.entity.DocumentChunk;
import com.student.mapper.DocumentChunkMapper;
import com.student.service.dataBase.MilvusService;
import com.student.service.document.DocumentChunkService;
import com.student.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文档分块服务实现类
 * 实现文档分块相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunk> implements DocumentChunkService {

    private final DocumentService documentService;
    private final MilvusService milvusService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearAllChunks() {
        log.info("开始清除所有文档分块数据");

        // 1. 查询有分块的文档ID（仅重置这些文档，避免全表锁）
        QueryWrapper<Document> docQuery = new QueryWrapper<>();
        docQuery.gt("chunk_count", 0);
        List<Document> documentsWithChunks = documentService.list(docQuery);
        log.info("需要重置分块计数的文档数量: {}", documentsWithChunks.size());

        // 2. 删除 MySQL 中所有分块记录
        int deletedCount = baseMapper.delete(new QueryWrapper<>());
        log.info("MySQL 分块记录已删除: count={}", deletedCount);

        // 3. 逐条更新文档的分块计数和状态（避免全表锁）
        Document documentUpdate;
        for (Document doc : documentsWithChunks) {
            documentUpdate = Document.builder()
                    .id(doc.getId())
                    .chunkCount(0)
                    .status(Document.Status.UPLOADED)
                    .build();
            documentService.updateById(documentUpdate);
        }
        log.info("所有文档分块计数已重置");

        // 4. 删除 Milvus 中所有向量数据（不在事务中执行，避免长时间占用连接）
        try {
            boolean milvusResult = milvusService.deleteAllVectors();
            if (milvusResult) {
                log.info("Milvus 向量数据已删除");
            } else {
                log.warn("Milvus 向量数据删除可能未完全成功，请手动检查");
            }
        } catch (Exception e) {
            log.error("Milvus 向量数据删除异常", e);
        }

        log.info("所有文档分块数据清除完成: deletedCount={}", deletedCount);
        return deletedCount;
    }
}