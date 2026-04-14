package com.student.service;

import com.student.entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 关键词检索服务实现类
 * 基于Lucene的BM25算法实现全文检索，支持医学术语同义词扩展
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class KeywordRetrievalServiceImpl implements KeywordRetrievalService {

    @Value("${imkqas.rag.keyword-index.path:lucene-index}")
    private String indexDirectoryPath;

    @Value("${imkqas.rag.keyword-index.synonyms.enabled:true}")
    private boolean synonymsEnabled;

    private Directory directory;
    private IndexWriter indexWriter;
    private IndexSearcher indexSearcher;
    private Analyzer analyzer;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // 医学术语同义词映射（简化实现，实际应从数据库或配置文件加载）
    private final Map<String, List<String>> medicalSynonyms = new ConcurrentHashMap<>();

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        try {
            // 确保索引目录存在
            Path indexPath = Paths.get(indexDirectoryPath);
            if (!Files.exists(indexPath)) {
                Files.createDirectories(indexPath);
            }

            // 初始化同义词词典
            initMedicalSynonyms();

            // 初始化Lucene组件
            analyzer = new StandardAnalyzer();
            directory = FSDirectory.open(indexPath);

            // 检查是否有现有索引，如果没有则创建新索引
            if (!DirectoryReader.indexExists(directory)) {
                createNewIndex();
            }

            // 创建索引读取器和搜索器
            refreshSearcher();

            log.info("关键词检索服务初始化完成，索引路径: {}", indexDirectoryPath);
        } catch (Exception e) {
            log.error("关键词检索服务初始化失败", e);
            throw new RuntimeException("关键词检索服务初始化失败", e);
        }
    }

    @Override
    public boolean buildIndex() {
        lock.writeLock().lock();
        try {
            log.info("开始构建全文索引");

            // 清理旧索引
            if (indexWriter != null) {
                indexWriter.close();
            }

            // 创建新索引
            createNewIndex();
            refreshSearcher();

            log.info("全文索引构建完成");
            return true;
        } catch (Exception e) {
            log.error("构建全文索引失败", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean addToIndex(DocumentChunk chunk) {
        lock.writeLock().lock();
        try {
            if (indexWriter == null) {
                refreshWriter();
            }

            org.apache.lucene.document.Document luceneDoc = convertToLuceneDocument(chunk);
            indexWriter.addDocument(luceneDoc);
            indexWriter.commit();

            log.debug("文档分块添加到索引: chunkId={}, documentId={}", chunk.getId(), chunk.getDocumentId());
            return true;
        } catch (Exception e) {
            log.error("添加文档分块到索引失败: chunkId={}", chunk.getId(), e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int addBatchToIndex(List<DocumentChunk> chunks) {
        lock.writeLock().lock();
        try {
            if (indexWriter == null) {
                refreshWriter();
            }

            int successCount = 0;
            for (DocumentChunk chunk : chunks) {
                try {
                    org.apache.lucene.document.Document luceneDoc = convertToLuceneDocument(chunk);
                    indexWriter.addDocument(luceneDoc);
                    successCount++;
                } catch (Exception e) {
                    log.warn("批量添加失败: chunkId={}", chunk.getId(), e);
                }
            }

            if (successCount > 0) {
                indexWriter.commit();
            }

            log.info("批量添加文档分块到索引完成: total={}, success={}", chunks.size(), successCount);
            return successCount;
        } catch (Exception e) {
            log.error("批量添加文档分块到索引失败", e);
            return 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean removeFromIndex(Long chunkId) {
        lock.writeLock().lock();
        try {
            if (indexWriter == null) {
                refreshWriter();
            }

            Term term = new Term("chunk_id", chunkId.toString());
            indexWriter.deleteDocuments(term);
            indexWriter.commit();

            log.debug("从索引中删除文档分块: chunkId={}", chunkId);
            return true;
        } catch (Exception e) {
            log.error("从索引中删除文档分块失败: chunkId={}", chunkId, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean updateInIndex(DocumentChunk chunk) {
        // 先删除后添加的方式实现更新
        removeFromIndex(chunk.getId());
        return addToIndex(chunk);
    }

    @Override
    public List<SearchResult> search(String query, int topK) {
        return search(query, List.of("content"), topK);
    }

    @Override
    public List<SearchResult> search(String query, List<String> fields, int topK) {
        lock.readLock().lock();
        try {
            if (indexSearcher == null) {
                refreshSearcher();
            }

            // 构建查询（支持多字段）
            BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
            for (String field : fields) {
                try {
                    QueryParser parser = new QueryParser(field, analyzer);
                    Query fieldQuery = parser.parse(query);
                    booleanQueryBuilder.add(fieldQuery, BooleanClause.Occur.SHOULD);
                } catch (Exception e) {
                    log.warn("字段查询解析失败: field={}, query={}", field, query, e);
                }
            }

            BooleanQuery booleanQuery = booleanQueryBuilder.build();
            return executeSearch(booleanQuery, topK);
        } catch (Exception e) {
            log.error("关键词检索失败: query={}", query, e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<SearchResult> searchWithSynonyms(String query, boolean expandSynonyms, int topK) {
        if (!expandSynonyms || !synonymsEnabled) {
            return search(query, topK);
        }

        try {
            // 扩展同义词查询
            String expandedQuery = expandQueryWithSynonyms(query);
            log.debug("同义词扩展查询: 原始={}, 扩展={}", query, expandedQuery);

            // 使用扩展后的查询进行检索
            return search(expandedQuery, topK);
        } catch (Exception e) {
            log.error("同义词扩展检索失败: query={}", query, e);
            return search(query, topK); // 降级为普通检索
        }
    }

    @Override
    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            if (directory == null) {
                init();
            }

            if (DirectoryReader.indexExists(directory)) {
                try (IndexReader reader = DirectoryReader.open(directory)) {
                    stats.put("total_documents", reader.numDocs());
                    stats.put("max_doc", reader.maxDoc());
                    stats.put("has_deletions", reader.hasDeletions());

                    // 获取字段信息
                    List<Map<String, Object>> fields = new ArrayList<>();
                    LeafReader leafReader = reader.leaves().get(0).reader();
                    FieldInfos fieldInfos = leafReader.getFieldInfos();
                    for (FieldInfo fieldInfo : fieldInfos) {
                        Map<String, Object> fieldStats = new HashMap<>();
                        fieldStats.put("name", fieldInfo.name);
                        fieldStats.put("has_norms", fieldInfo.hasNorms());
                        fieldStats.put("has_vectors", fieldInfo.hasVectors());
                        fields.add(fieldStats);
                    }
                    stats.put("fields", fields);
                }
            } else {
                stats.put("total_documents", 0);
                stats.put("index_exists", false);
            }

            stats.put("index_directory", indexDirectoryPath);
            stats.put("synonyms_enabled", synonymsEnabled);
            stats.put("synonyms_count", medicalSynonyms.size());

        } catch (Exception e) {
            log.error("获取索引统计信息失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    @Override
    public boolean clearIndex() {
        lock.writeLock().lock();
        try {
            log.info("开始清除索引数据");

            if (indexWriter != null) {
                indexWriter.deleteAll();
                indexWriter.commit();
                indexWriter.close();
                indexWriter = null;
            }

            // 重新创建空索引
            createNewIndex();
            refreshSearcher();

            log.info("索引数据清除完成");
            return true;
        } catch (Exception e) {
            log.error("清除索引数据失败", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 初始化医学术语同义词词典
     */
    private void initMedicalSynonyms() {
        // 这里加载医学术语同义词
        // 简化实现：硬编码一些常见医学术语同义词
        // 实际应从数据库或配置文件加载

        // 疾病同义词
        medicalSynonyms.put("高血压", List.of("血压高", "高血压病", "原发性高血压"));
        medicalSynonyms.put("糖尿病", List.of("消渴病", "糖尿病病", "高血糖"));
        medicalSynonyms.put("冠心病", List.of("冠状动脉粥样硬化性心脏病", "缺血性心脏病"));
        medicalSynonyms.put("肺炎", List.of("肺部感染", "肺感染", "肺部炎症"));

        // 症状同义词
        medicalSynonyms.put("发热", List.of("发烧", "体温升高", "发烧热"));
        medicalSynonyms.put("咳嗽", List.of("咳痰", "咳", "咳嗽咳"));
        medicalSynonyms.put("头痛", List.of("头疼", "头部疼痛", "头痛痛"));
        medicalSynonyms.put("腹痛", List.of("肚子疼", "腹部疼痛", "肚子痛"));

        // 药物同义词
        medicalSynonyms.put("阿司匹林", List.of("乙酰水杨酸", "阿斯匹林"));
        medicalSynonyms.put("青霉素", List.of("盘尼西林", "青霉素G"));
        medicalSynonyms.put("胰岛素", List.of("胰岛激素", "胰岛素制剂"));

        log.info("医学术语同义词词典初始化完成，共加载 {} 个术语", medicalSynonyms.size());
    }

    /**
     * 创建新索引
     */
    private void createNewIndex() throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        if (indexWriter != null) {
            indexWriter.close();
        }

        indexWriter = new IndexWriter(directory, config);
        indexWriter.commit(); // 提交空操作以创建段文件
        log.debug("创建新索引完成");
    }

    /**
     * 刷新索引写入器
     */
    private void refreshWriter() throws IOException {
        if (directory == null) {
            init();
        }

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        if (indexWriter != null) {
            indexWriter.close();
        }

        indexWriter = new IndexWriter(directory, config);
    }

    /**
     * 刷新索引搜索器
     */
    private void refreshSearcher() throws IOException {
        if (directory == null) {
            init();
        }

        if (indexSearcher != null) {
            indexSearcher.getIndexReader().close();
        }

        IndexReader reader = DirectoryReader.open(directory);
        indexSearcher = new IndexSearcher(reader);
    }

    /**
     * 将DocumentChunk转换为Lucene文档
     */
    private org.apache.lucene.document.Document convertToLuceneDocument(DocumentChunk chunk) {
        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();

        // 分块ID（存储但不分词）
        doc.add(new StringField("chunk_id", chunk.getId().toString(), Field.Store.YES));

        // 文档ID（存储但不分词）
        doc.add(new StringField("document_id", chunk.getDocumentId().toString(), Field.Store.YES));

        // 分块索引（存储但不分词）
        doc.add(new IntPoint("chunk_index", chunk.getChunkIndex()));
        doc.add(new StoredField("chunk_index", chunk.getChunkIndex()));

        // 分块内容（分词并存储）
        doc.add(new TextField("content", chunk.getContent(), Field.Store.YES));

        // 元数据（存储但不分词）
        String metadata = chunk.getMetadata() != null ? chunk.getMetadata() : "{}";
        doc.add(new StoredField("metadata", metadata));

        // 分块标识符（存储但不分词）
        doc.add(new StringField("chunk_identifier", chunk.getChunkIdentifier(), Field.Store.YES));

        // 向量ID（存储但不分词）
        if (chunk.isVectorized() && chunk.getVectorId() != null) {
            doc.add(new StringField("vector_id", chunk.getVectorId(), Field.Store.YES));
        }

        return doc;
    }

    /**
     * 执行搜索并返回结果
     */
    private List<SearchResult> executeSearch(Query query, int topK) throws IOException {
        if (indexSearcher == null) {
            refreshSearcher();
        }

        // 执行搜索
        TopDocs topDocs = indexSearcher.search(query, Math.min(topK, 1000)); // 限制最大结果数

        // 转换结果
        List<SearchResult> results = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            org.apache.lucene.document.Document doc = indexSearcher.doc(scoreDoc.doc);

            try {
                Long chunkId = Long.parseLong(doc.get("chunk_id"));
                Long documentId = Long.parseLong(doc.get("document_id"));
                Double score = (double) scoreDoc.score;
                String content = doc.get("content");
                String metadataStr = doc.get("metadata");

                // 解析元数据
                Map<String, Object> metadata = new HashMap<>();
                if (metadataStr != null && !metadataStr.trim().isEmpty()) {
                    try {
                        // 使用Jackson库解析JSON
                        metadata = parseMetadataJson(metadataStr);
                    } catch (Exception e) {
                        log.warn("元数据解析失败: {}", metadataStr, e);
                    }
                }

                SearchResult result = new SearchResult(chunkId, documentId, score, content, metadata);
                results.add(result);

                log.debug("检索结果: chunkId={}, score={}", chunkId, score);
            } catch (Exception e) {
                log.warn("搜索结果转换失败: docId={}", scoreDoc.doc, e);
            }
        }

        return results;
    }

    /**
     * 扩展查询词的同义词
     */
    private String expandQueryWithSynonyms(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        StringBuilder expandedQuery = new StringBuilder();
        String[] terms = query.split("\\s+");

        for (int i = 0; i < terms.length; i++) {
            String term = terms[i].trim();
            if (term.isEmpty()) {
                continue;
            }

            // 添加原始词
            expandedQuery.append(term);

            // 查找同义词
            List<String> synonyms = medicalSynonyms.get(term);
            if (synonyms != null && !synonyms.isEmpty()) {
                expandedQuery.append(" OR ");
                expandedQuery.append(String.join(" OR ", synonyms));
            }

            // 添加空格（最后一个词除外）
            if (i < terms.length - 1) {
                expandedQuery.append(" ");
            }
        }

        return expandedQuery.toString();
    }

    /**
     * 解析JSON格式的元数据
     */
    private Map<String, Object> parseMetadataJson(String metadataStr) throws IOException {
        if (metadataStr == null || metadataStr.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(metadataStr,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            log.warn("JSON解析失败，尝试回退到简单解析: {}", metadataStr, e);
            // 回退到简单解析
            return parseMetadataSimple(metadataStr);
        }
    }

    /**
     * 简单解析元数据（兼容旧格式）
     */
    private Map<String, Object> parseMetadataSimple(String metadataStr) {
        Map<String, Object> metadata = new HashMap<>();
        if (metadataStr == null || metadataStr.trim().isEmpty()) {
            return metadata;
        }

        // 简化解析，用于处理简单的键值对
        if (metadataStr.startsWith("{") && metadataStr.endsWith("}")) {
            String content = metadataStr.substring(1, metadataStr.length() - 1).trim();
            if (content.isEmpty()) {
                return metadata;
            }

            // 分割键值对
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "").replace("'", "");
                    String value = keyValue[1].trim().replace("\"", "").replace("'", "");
                    metadata.put(key, value);
                }
            }
        }

        return metadata;
    }

    /**
     * 服务销毁时清理资源
     */
    public void destroy() {
        try {
            if (indexWriter != null) {
                indexWriter.close();
            }
            if (indexSearcher != null) {
                indexSearcher.getIndexReader().close();
            }
            if (directory != null) {
                directory.close();
            }
            log.info("关键词检索服务资源清理完成");
        } catch (Exception e) {
            log.error("关键词检索服务资源清理失败", e);
        }
    }
}