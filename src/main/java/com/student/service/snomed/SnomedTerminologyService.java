package com.student.service.snomed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.service.RedisService;
import com.student.service.snomed.config.SnomedConfigProperties;
import com.student.service.snomed.config.SnomedFallbackMapper;
import com.student.service.snomed.dto.SnomedTermResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;

/**
 * SNOMED CT 术语服务实现类
 * 提供医学术语的SNOMED CT标准化功能，支持：
 * 1. 调用Snowstorm FHIR API查询术语
 * 2. Redis缓存查询结果
 * 3. 本地降级映射（当SNOMED服务不可用时）
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SnomedTerminologyService {

    private final RedisService redisService;
    private final SnomedConfigProperties configProperties;
    private final SnomedFallbackMapper fallbackMapper;

    private HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 缓存前缀
    private static final String CACHE_PREFIX = "snomed:term:";
    private static final String CACHE_PREFIX_BY_TERM = "snomed:byterm:";

    // 统计信息
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger snomedHits = new AtomicInteger(0);
    private final AtomicInteger fallbackHits = new AtomicInteger(0);
    private final AtomicInteger failedQueries = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    // 服务健康状态
    private volatile boolean snomedAvailable = true;
    private long lastHealthCheckTime = 0;
    private static final long HEALTH_CHECK_INTERVAL = 60000; // 1分钟

    /**
     * 初始化HttpClient
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(configProperties.getConnectTimeout()))
                .build();
    }

    /**
     * 术语化查询 - 将输入的查询文本转换为SNOMED CT标准化术语
     *
     * @param query 用户查询文本
     * @return 术语化后的查询，保留原始结构但在关键位置标注SNOMED CT术语
     */
    public String medicalize(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        totalQueries.incrementAndGet();
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder result = new StringBuilder();
            // 使用HanLP通用模式分词
            List<Term> termList = HanLP.segment(query);
            boolean modified = false;

            for (Term term : termList) {
                String word = term.word;

                // 优先检查本地降级映射（口语化表达）
                String[] colloquialMapping = fallbackMapper.getByColloquial(word);
                if (colloquialMapping != null) {
                    result.append(colloquialMapping[1]).append(" ");
                    modified = true;
                    log.debug("本地降级映射（口语）: {} -> {}", word, colloquialMapping[1]);
                    continue;
                }

                // 检查SNOMED CT服务
                if (configProperties.isEnabled()) {
                    SnomedTermResponse snomedTerm = lookupByTerm(word);
                    if (snomedTerm != null) {
                        result.append(snomedTerm.getTerm()).append(" ");
                        modified = true;
                        snomedHits.incrementAndGet();
                        continue;
                    }
                }

                // 检查本地医学术语映射
                String[] termMapping = fallbackMapper.getByMedicalTerm(word);
                if (termMapping != null) {
                    result.append(termMapping[1]).append(" ");
                    modified = true;
                    fallbackHits.incrementAndGet();
                    continue;
                }

                // 保留原词
                result.append(word).append(" ");
            }

            long responseTime = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(responseTime);

            if (modified) {
                log.debug("术语化完成: {} -> {}, time={}ms", query, result, responseTime);
            }

            return modified ? result.toString().trim() : query;

        } catch (Exception e) {
            failedQueries.incrementAndGet();
            log.error("术语化异常: query={}", query, e);
            return query;
        }
    }

    /**
     * 根据术语查询SNOMED CT概念
     *
     * @param term 术语名称
     * @return SNOMED CT响应，如果未找到返回null
     */
    public SnomedTermResponse lookupByTerm(String term) {
        if (term == null || term.trim().isEmpty()) {
            return null;
        }

        String cacheKey = CACHE_PREFIX_BY_TERM + term.toLowerCase();

        // 1. 检查Redis缓存
        Object cached = redisService.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            log.debug("缓存命中: term={}", term);
            return convertToResponse(cached);
        }

        // 2. 检查服务可用性
        if (!isSnomedAvailable()) {
            log.debug("SNOMED服务不可用，使用降级: term={}", term);
            return null;
        }

        // 3. 调用SNOMED CT FHIR API
        try {
            SnomedTermResponse response = querySnomedApi(term);
            if (response != null) {
                // 缓存结果
                redisService.set(cacheKey, convertToCacheObject(response),
                        (long) configProperties.getCacheExpirationHours() * 3600);
            }
            return response;
        } catch (Exception e) {
            log.warn("SNOMED API调用失败: term={}, error={}", term, e.getMessage());
            // 标记服务可能不可用
            checkSnomedHealth();
            return null;
        }
    }

    /**
     * 根据SNOMED CT ConceptID查询术语
     *
     * @param conceptId SNOMED CT概念ID
     * @return SNOMED CT响应，如果未找到返回null
     */
    public SnomedTermResponse lookupByConceptId(String conceptId) {
        if (conceptId == null || conceptId.trim().isEmpty()) {
            return null;
        }

        String cacheKey = CACHE_PREFIX + conceptId;

        // 1. 检查Redis缓存
        Object cached = redisService.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return convertToResponse(cached);
        }

        // 2. 检查服务可用性
        if (!isSnomedAvailable()) {
            return null;
        }

        // 3. 调用SNOMED CT FHIR API
        try {
            SnomedTermResponse response = querySnomedApiByConceptId(conceptId);
            if (response != null) {
                redisService.set(cacheKey, convertToCacheObject(response),
                        (long) configProperties.getCacheExpirationHours() * 3600);
            }
            return response;
        } catch (Exception e) {
            log.warn("SNOMED API调用失败: conceptId={}, error={}", conceptId, e.getMessage());
            checkSnomedHealth();
            return null;
        }
    }

    /**
     * 批量查询术语
     *
     * @param terms 术语列表
     * @return 术语到SNOMED CT响应的映射
     */
    public Map<String, SnomedTermResponse> lookupBatch(List<String> terms) {
        Map<String, SnomedTermResponse> results = new ConcurrentHashMap<>();

        // 并发查询
        List<CompletableFuture<Void>> futures = terms.stream()
                .map(term -> CompletableFuture.runAsync(() -> {
                    SnomedTermResponse response = lookupByTerm(term);
                    if (response != null) {
                        results.put(term, response);
                    }
                }))
                .toList();

        // 等待所有完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return results;
    }

    /**
     * 调用SNOMED CT FHIR API - 根据术语查询
     */
    private SnomedTermResponse querySnomedApi(String term) {
        try {
            String url = buildUrl("/CodeSystem/$lookup", Map.of(
                    "system", "http://snomed.info/sct",
                    "code", getConceptIdByTerm(term)
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(configProperties.getReadTimeout()))
                    .header("Accept", "application/fhir+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseFhirResponse(response.body(), term);
            }

            log.debug("SNOMED API响应: status={}, term={}", response.statusCode(), term);
            return null;

        } catch (Exception e) {
            log.error("SNOMED API调用异常: term={}", term, e);
            return null;
        }
    }

    /**
     * 调用SNOMED CT FHIR API - 根据ConceptID查询
     */
    private SnomedTermResponse querySnomedApiByConceptId(String conceptId) {
        try {
            String url = buildUrl("/CodeSystem/$lookup", Map.of(
                    "system", "http://snomed.info/sct",
                    "code", conceptId
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(configProperties.getReadTimeout()))
                    .header("Accept", "application/fhir+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseFhirResponse(response.body(), null);
            }

            return null;

        } catch (Exception e) {
            log.error("SNOMED API调用异常: conceptId={}", conceptId, e);
            return null;
        }
    }

    /**
     * 构建FHIR API URL
     */
    private String buildUrl(String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder(configProperties.getBaseUrl())
                .append(configProperties.getFhirPath())
                .append(path);

        StringBuilder query = new StringBuilder("?");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 1) {
                query.append("&");
            }
            query.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return url.append(query).toString();
    }

    /**
     * 解析FHIR响应
     */
    private SnomedTermResponse parseFhirResponse(String jsonBody, String originalTerm) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);

            // 提取参数
            String conceptId = null;
            String term = null;
            String termType = null;
            String semanticTag = null;
            List<String> synonyms = new ArrayList<>();

            JsonNode parameter = root.get("parameter");
            if (parameter != null && parameter.isArray()) {
                for (JsonNode param : parameter) {
                    String name = param.has("name") ? param.get("name").asText() : null;

                    if ("code".equals(name)) {
                        conceptId = param.has("valueCode") ? param.get("valueCode").asText() : null;
                    } else if ("display".equals(name)) {
                        term = param.has("valueString") ? param.get("valueString").asText() : null;
                    } else if ("name".equals(name)) {
                        termType = param.has("valueString") ? param.get("valueString").asText() : null;
                    } else if ("semanticTag".equals(name)) {
                        semanticTag = param.has("valueString") ? param.get("valueString").asText() : null;
                    }
                }
            }

            // 如果没有找到精确匹配，尝试从本地映射获取
            if (term == null && originalTerm != null) {
                String[] mapping = fallbackMapper.getByMedicalTerm(originalTerm);
                if (mapping != null) {
                    term = mapping[1];
                    conceptId = mapping[0];
                    termType = mapping[2];
                }
            }

            if (term == null) {
                return null;
            }

            return SnomedTermResponse.builder()
                    .conceptId(conceptId)
                    .term(term)
                    .termType(termType)
                    .semanticTag(semanticTag)
                    .synonyms(synonyms)
                    .active(true)
                    .definitionStatus("Defined")
                    .resourceType("CodeSystem")
                    .fallback(false)
                    .build();

        } catch (Exception e) {
            log.error("解析FHIR响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 简单的术语到ConceptID映射（用于演示，实际应调用搜索API）
     */
    private String getConceptIdByTerm(String term) {
        try {
            // 1. 构建搜索 URL（使用你配置的 Snowstorm 基础地址）
            String searchUrl = configProperties.getBaseUrl()
                    + "/browser/MAIN/concepts?term="
                    + java.net.URLEncoder.encode(term, java.nio.charset.StandardCharsets.UTF_8)
                    + "&activeFilter=true&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .timeout(Duration.ofMillis(configProperties.getReadTimeout()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.get("items");
                if (items != null && items.isArray() && !items.isEmpty()) {
                    String conceptId = items.get(0).get("conceptId").asText();
                    log.debug("概念搜索: term={} -> conceptId={}", term, conceptId);
                    return conceptId;
                }
            } else {
                log.warn("概念搜索失败: term={}, status={}", term, response.statusCode());
            }
        } catch (Exception e) {
            log.error("概念搜索异常: term={}", term, e);
        }
        return null;
    }

    /**
     * 检查SNOMED服务健康状态
     */
    private boolean isSnomedAvailable() {
        if (!configProperties.isEnabled()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastHealthCheckTime < HEALTH_CHECK_INTERVAL) {
            return snomedAvailable;
        }

        return checkSnomedHealth();
    }

    /**
     * 执行健康检查
     */
    private boolean checkSnomedHealth() {
        try {
            String healthUrl = configProperties.getBaseUrl() + "/health";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            snomedAvailable = response.statusCode() == 200;
            lastHealthCheckTime = System.currentTimeMillis();

            log.debug("SNOMED健康检查: available={}", snomedAvailable);
            return snomedAvailable;

        } catch (Exception e) {
            log.warn("SNOMED健康检查失败: {}", e.getMessage());
            snomedAvailable = false;
            lastHealthCheckTime = System.currentTimeMillis();
            return false;
        }
    }

    /**
     * 转换缓存对象到响应
     */
    @SuppressWarnings("unchecked")
    private SnomedTermResponse convertToResponse(Object cached) {
        if (cached instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) cached;
            return SnomedTermResponse.builder()
                    .conceptId((String) map.get("conceptId"))
                    .term((String) map.get("term"))
                    .termType((String) map.get("termType"))
                    .semanticTag((String) map.get("semanticTag"))
                    .synonyms((List<String>) map.get("synonyms"))
                    .active(map.get("active") != null && (Boolean) map.get("active"))
                    .definitionStatus((String) map.get("definitionStatus"))
                    .fallback(map.get("fallback") != null && (Boolean) map.get("fallback"))
                    .build();
        }
        return null;
    }

    /**
     * 转换响应到缓存对象
     */
    private Map<String, Object> convertToCacheObject(SnomedTermResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("conceptId", response.getConceptId());
        map.put("term", response.getTerm());
        map.put("termType", response.getTermType());
        map.put("semanticTag", response.getSemanticTag());
        map.put("synonyms", response.getSynonyms());
        map.put("active", response.isActive());
        map.put("definitionStatus", response.getDefinitionStatus());
        map.put("fallback", response.isFallback());
        return map;
    }

    /**
     * 获取服务统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalQueries", totalQueries.get());
        stats.put("cacheHits", cacheHits.get());
        stats.put("snomedHits", snomedHits.get());
        stats.put("fallbackHits", fallbackHits.get());
        stats.put("failedQueries", failedQueries.get());
        stats.put("snomedAvailable", snomedAvailable);

        int total = totalQueries.get();
        if (total > 0) {
            stats.put("cacheHitRate", String.format("%.2f%%", (double) cacheHits.get() / total * 100));
            stats.put("avgResponseTime", String.format("%.2fms",
                    (double) totalResponseTime.get() / total));
        }

        return stats;
    }

    /**
     * 强制刷新健康检查
     */
    public void refreshHealthCheck() {
        lastHealthCheckTime = 0;
        checkSnomedHealth();
    }
}