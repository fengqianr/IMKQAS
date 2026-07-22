package com.imkqas.controller.rag;

import com.imkqas.config.RagConfig;
import com.imkqas.dto.rag.RetrievalResultItem;
import com.imkqas.dto.rag.RetrievalTestRequest;
import com.imkqas.dto.rag.RetrievalTestResponse;
import com.imkqas.entity.DocumentChunk;
import com.imkqas.service.dataBase.MilvusService;
import com.imkqas.service.document.DocumentChunkService;
import com.imkqas.service.rag.*;
import com.imkqas.service.rag.MultiRetrievalService.RetrievalResult;
import com.imkqas.service.rag.MultiRetrievalService.RetrievalSource;
import com.imkqas.service.rag.impl.MultiFactorRerankServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 检索K值调优测试控制器
 * 提供可配置K值的检索端点，用于Python脚本进行K值扫描和最优K值确定
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/rag/retrieval-test")
@RequiredArgsConstructor
@Tag(name = "检索K值调优", description = "用于K值调优的可配置检索端点")
@Slf4j
public class RetrievalTestController {

    private final MultiRetrievalService multiRetrievalService;
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final KeywordRetrievalService keywordRetrievalService;
    private final DocumentChunkService documentChunkService;
    private final RagConfig ragConfig;
    private final MultiFactorRerankServiceImpl multiFactorRerankService;
    private final CrossEncoderRerankService crossEncoderRerankService;

    private final ExecutorService pathwayExecutor = Executors.newFixedThreadPool(3);

    /**
     * 单次检索测试
     * 对指定通路以指定K值执行单次检索，返回结果和精确计时的延迟数据
     */
    @PostMapping("/single")
    @Operation(summary = "单次检索测试", description = "对指定通路以指定K值执行单次检索，返回分项延迟")
    public ResponseEntity<RetrievalTestResponse> testSingle(@Valid @RequestBody RetrievalTestRequest request) {
        String pathway = request.getPathway().toUpperCase();
        String query = request.getQuery();
        int topK = request.getTopK();

        long t0 = System.nanoTime();
        double embeddingLatencyMs = 0;
        double searchLatencyMs;
        List<RetrievalResult> results;

        try {
            switch (pathway) {
                case "VECTOR" -> {
                    long embedStart = System.nanoTime();
                    List<Float> queryVector = embeddingService.embed(query);
                    long embedEnd = System.nanoTime();
                    embeddingLatencyMs = (embedEnd - embedStart) / 1_000_000.0;

                    long searchStart = System.nanoTime();
                    results = multiRetrievalService.vectorRetrieval(query, topK);
                    searchLatencyMs = (System.nanoTime() - searchStart) / 1_000_000.0;
                }
                case "KEYWORD" -> {
                    long searchStart = System.nanoTime();
                    results = multiRetrievalService.keywordRetrieval(query, topK);
                    searchLatencyMs = (System.nanoTime() - searchStart) / 1_000_000.0;
                }
                case "HYBRID" -> {
                    int perSideK = request.getHybridPerSideK() != null
                            ? request.getHybridPerSideK() : ragConfig.getRetrieval().getInitialTopK();
                    double vecWeight = request.getVectorWeight() != null
                            ? request.getVectorWeight() : ragConfig.getRetrieval().getWeights().getVector();
                    double kwWeight = request.getKeywordWeight() != null
                            ? request.getKeywordWeight() : ragConfig.getRetrieval().getWeights().getKeyword();

                    long searchStart = System.nanoTime();
                    results = multiRetrievalService.hybridRetrievalWithPerSideK(
                            query, topK, perSideK, vecWeight, kwWeight);
                    searchLatencyMs = (System.nanoTime() - searchStart) / 1_000_000.0;
                }
                default -> {
                    return ResponseEntity.badRequest().body(RetrievalTestResponse.builder()
                            .success(false).error("不支持的通路类型: " + pathway
                                    + "，支持: VECTOR, KEYWORD, HYBRID").build());
                }
            }
        } catch (Exception e) {
            log.error("检索测试异常: pathway={}, query={}, topK={}", pathway, query, topK, e);
            return ResponseEntity.ok(RetrievalTestResponse.builder()
                    .success(false).error(e.getMessage()).pathway(pathway).query(query).build());
        }

        double totalLatencyMs = (System.nanoTime() - t0) / 1_000_000.0;

        List<RetrievalResultItem> resultItems = results != null ? results.stream()
                .map(r -> RetrievalResultItem.builder()
                        .chunkId(r.getChunkId())
                        .documentId(r.getDocumentId())
                        .score(r.getScore())
                        .content(r.getContent())
                        .vectorScore(r.getVectorScore())
                        .keywordScore(r.getKeywordScore())
                        .source(r.getSource().name())
                        .build())
                .collect(Collectors.toList()) : Collections.emptyList();

        return ResponseEntity.ok(RetrievalTestResponse.builder()
                .success(true)
                .pathway(pathway)
                .query(query)
                .requestedTopK(topK)
                .actualCount(resultItems.size())
                .latencyMs(Math.round(totalLatencyMs * 100.0) / 100.0)
                .embeddingLatencyMs(Math.round(embeddingLatencyMs * 100.0) / 100.0)
                .searchLatencyMs(Math.round(searchLatencyMs * 100.0) / 100.0)
                .results(resultItems)
                .build());
    }

    /**
     * 批量K值测试
     * 对同一查询以多个K值测试同一条通路，减少网络往返
     */
    @PostMapping("/batch")
    @Operation(summary = "批量K值测试", description = "对同一查询以多个K值测试同一条通路")
    public ResponseEntity<Map<String, Object>> testBatch(@RequestBody Map<String, Object> body) {
        String pathway = ((String) body.get("pathway")).toUpperCase();
        String query = (String) body.get("query");
        @SuppressWarnings("unchecked")
        List<Integer> kValues = (List<Integer>) body.get("kValues");
        Integer hybridPerSideK = body.containsKey("hybridPerSideK")
                ? ((Number) body.get("hybridPerSideK")).intValue() : null;

        if (kValues == null || kValues.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "kValues不能为空"));
        }

        List<RetrievalTestResponse> responses = new ArrayList<>();
        for (int k : kValues) {
            RetrievalTestRequest req = new RetrievalTestRequest();
            req.setPathway(pathway);
            req.setQuery(query);
            req.setTopK(k);
            req.setHybridPerSideK(hybridPerSideK);

            ResponseEntity<RetrievalTestResponse> resp = testSingle(req);
            if (resp.getBody() != null) {
                responses.add(resp.getBody());
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "pathway", pathway,
                "query", query,
                "kValues", kValues,
                "totalCalls", responses.size(),
                "results", responses
        ));
    }

    /**
     * 全通路并行检索
     * 对同一查询并行执行三条通路，用于构建Ground Truth候选池
     */
    @PostMapping("/all-pathways")
    @Operation(summary = "全通路并行检索", description = "并行执行VECTOR/KEYWORD/HYBRID三条通路")
    public ResponseEntity<Map<String, Object>> testAllPathways(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 500;
        int hybridPerSideK = body.containsKey("hybridPerSideK")
                ? ((Number) body.get("hybridPerSideK")).intValue() : topK;

        try {
            CompletableFuture<RetrievalTestResponse> vectorFuture = CompletableFuture.supplyAsync(() -> {
                RetrievalTestRequest req = new RetrievalTestRequest();
                req.setPathway("VECTOR");
                req.setQuery(query);
                req.setTopK(topK);
                return testSingle(req).getBody();
            }, pathwayExecutor);

            CompletableFuture<RetrievalTestResponse> keywordFuture = CompletableFuture.supplyAsync(() -> {
                RetrievalTestRequest req = new RetrievalTestRequest();
                req.setPathway("KEYWORD");
                req.setQuery(query);
                req.setTopK(topK);
                return testSingle(req).getBody();
            }, pathwayExecutor);

            CompletableFuture<RetrievalTestResponse> hybridFuture = CompletableFuture.supplyAsync(() -> {
                RetrievalTestRequest req = new RetrievalTestRequest();
                req.setPathway("HYBRID");
                req.setQuery(query);
                req.setTopK(topK);
                req.setHybridPerSideK(hybridPerSideK);
                return testSingle(req).getBody();
            }, pathwayExecutor);

            CompletableFuture.allOf(vectorFuture, keywordFuture, hybridFuture).join();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "query", query,
                    "topK", topK,
                    "vector", vectorFuture.get(),
                    "keyword", keywordFuture.get(),
                    "hybrid", hybridFuture.get()
            ));
        } catch (Exception e) {
            log.error("全通路检索异常: query={}", query, e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取集合/索引统计信息
     */
    @GetMapping("/collection-info")
    @Operation(summary = "集合信息", description = "返回Milvus和Lucene索引的文档统计信息")
    public ResponseEntity<Map<String, Object>> getCollectionInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        try {
            long milvusCount = milvusService.getCollectionCount();
            info.put("milvusDocumentCount", milvusCount);
            info.put("milvusStatus", "connected");
        } catch (Exception e) {
            info.put("milvusDocumentCount", -1);
            info.put("milvusStatus", "error: " + e.getMessage());
        }

        try {
            Map<String, Object> keywordStats = keywordRetrievalService.getIndexStats();
            info.put("keywordIndexStats", keywordStats);
        } catch (Exception e) {
            info.put("keywordIndexStats", Map.of("error", e.getMessage()));
        }

        info.put("ragConfig", Map.of(
                "initialTopK", ragConfig.getRetrieval().getInitialTopK(),
                "rerankTopK", ragConfig.getRetrieval().getRerankTopK(),
                "rrfK", ragConfig.getRetrieval().getRrfK(),
                "vectorWeight", ragConfig.getRetrieval().getWeights().getVector(),
                "keywordWeight", ragConfig.getRetrieval().getWeights().getKeyword(),
                "mode", ragConfig.getRetrieval().getMode().name()
        ));

        return ResponseEntity.ok(info);
    }

    /**
     * 多因子分解端点，用于离线权重网格搜索。
     * 对查询执行检索+多因子重排序，返回每篇文档的五因子分数。
     */
    @PostMapping("/factor-breakdown")
    @Operation(summary = "多因子分解", description = "返回每篇检索文档的五因子分数，用于网格搜索权重调优")
    public ResponseEntity<Map<String, Object>> factorBreakdown(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 20;

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "query不能为空"));
        }

        try {
            // 1. 混合检索
            int perSideK = ragConfig.getRetrieval().getInitialTopK();
            List<RetrievalResult> retrieved = multiRetrievalService.hybridRetrievalWithPerSideK(
                    query, topK, perSideK,
                    ragConfig.getRetrieval().getWeights().getVector(),
                    ragConfig.getRetrieval().getWeights().getKeyword());

            // 2. 交叉编码器获取语义分数
            List<RetrievalResult> reranked = crossEncoderRerankService.rerank(
                    query, retrieved, Math.min(retrieved.size(), 20));
            Map<String, Double> semanticMap = new HashMap<>();
            for (RetrievalResult r : reranked) {
                String key = r.getChunkId() != null ? String.valueOf(r.getChunkId()) : r.getContent();
                semanticMap.put(key, r.getScore() != null ? r.getScore() : 0.5);
            }

            // 3. 逐文档计算五因子分解
            List<Map<String, Object>> docs = new ArrayList<>();
            for (RetrievalResult r : retrieved) {
                String key = r.getChunkId() != null ? String.valueOf(r.getChunkId()) : r.getContent();
                double semantic = semanticMap.getOrDefault(key, 0.5);
                Map<String, Double> factors = multiFactorRerankService.getFactorBreakdown(query, r, semantic);

                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("chunkId", r.getChunkId());
                doc.put("content", r.getContent() != null
                        ? r.getContent().substring(0, Math.min(r.getContent().length(), 300)) : "");
                doc.putAll(factors);
                docs.add(doc);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "query", query,
                    "docCount", docs.size(),
                    "docs", docs
            ));
        } catch (Exception e) {
            log.error("因子分解异常: query={}", query, e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 重建关键词索引
     * 从MySQL读取所有文档分块，重新构建Lucene全文索引
     */
    @PostMapping("/rebuild-keyword-index")
    @Operation(summary = "重建关键词索引", description = "从MySQL读取全部分块重新构建Lucene索引")
    public ResponseEntity<Map<String, Object>> rebuildKeywordIndex() {
        try {
            List<DocumentChunk> allChunks = documentChunkService.list();
            if (allChunks.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "数据库中没有文档分块，已创建空索引",
                        "indexedCount", 0
                ));
            }

            keywordRetrievalService.buildIndex();
            int count = keywordRetrievalService.addBatchToIndex(allChunks);

            log.info("关键词索引重建完成: total={}, indexed={}", allChunks.size(), count);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "索引重建完成",
                    "totalChunks", allChunks.size(),
                    "indexedCount", count
            ));
        } catch (Exception e) {
            log.error("关键词索引重建失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
