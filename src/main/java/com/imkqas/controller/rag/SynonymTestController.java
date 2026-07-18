package com.imkqas.controller.rag;

import com.imkqas.service.rag.SynonymExpansionService;
import com.imkqas.service.rag.SynonymExpansionService.TermMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 同义词扩展置信度调优测试控制器
 * 提供术语查询和强制LLM推断端点，供Python脚本进行离线置信度阈值调优
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/rag/synonym-test")
@RequiredArgsConstructor
@Tag(name = "同义词扩展置信度调优", description = "用于LLM置信度阈值离线调优的测试端点")
@Slf4j
public class SynonymTestController {

    private final SynonymExpansionService synonymExpansionService;

    /**
     * 单术语查询（标准三级管线：LOCAL → SNOMED_CT → LLM）
     * 返回null表示该术语在所有来源中均未找到映射
     */
    @GetMapping("/lookup")
    @Operation(summary = "术语查询", description = "查询单个术语的标准名称映射，返回来源和置信度")
    public ResponseEntity<Map<String, Object>> lookupTerm(@RequestParam String term) {
        TermMapping mapping = synonymExpansionService.lookupTerm(term);
        if (mapping == null) {
            return ResponseEntity.ok(Map.of(
                    "term", term,
                    "found", false,
                    "source", "NONE"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "term", mapping.getColloquialTerm(),
                "standardTerm", mapping.getStandardTerm(),
                "conceptId", mapping.getConceptId() != null ? mapping.getConceptId() : "",
                "source", mapping.getSource(),
                "confidence", mapping.getConfidence(),
                "found", true
        ));
    }

    /**
     * 强制LLM推断（绕过本地映射和SNOMED CT）
     * 始终返回LLM的原始置信度，用于离线阈值扫描
     */
    @PostMapping("/llm-infer")
    @Operation(summary = "强制LLM推断", description = "绕过缓存直接调用LLM获取术语标准化结果和置信度")
    public ResponseEntity<Map<String, Object>> forceLlmInfer(@RequestBody Map<String, String> body) {
        String term = body.get("term");
        String contextQuery = body.get("contextQuery");

        if (term == null || term.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "term不能为空"));
        }
        if (contextQuery == null || contextQuery.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "contextQuery不能为空"));
        }

        TermMapping result = synonymExpansionService.lookupTermForceLlm(term, contextQuery);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("term", result.getColloquialTerm());
        response.put("standardTerm", result.getStandardTerm());
        response.put("confidence", result.getConfidence());
        response.put("source", result.getSource());
        return ResponseEntity.ok(response);
    }

    /**
     * 批量强制LLM推断
     * 减少HTTP往返，提高数据采集效率
     */
    @PostMapping("/batch-llm-infer")
    @Operation(summary = "批量强制LLM推断", description = "批量调用LLM获取术语标准化结果，减少网络往返")
    public ResponseEntity<Map<String, Object>> batchForceLlmInfer(@RequestBody List<Map<String, String>> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "请求列表不能为空"));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (Map<String, String> pair : pairs) {
            String term = pair.get("term");
            String contextQuery = pair.get("contextQuery");

            if (term == null || term.isBlank() || contextQuery == null || contextQuery.isBlank()) {
                results.add(Map.of(
                        "term", term != null ? term : "",
                        "standardTerm", null,
                        "confidence", 0.0,
                        "error", "term或contextQuery为空"
                ));
                failCount++;
                continue;
            }

            try {
                TermMapping result = synonymExpansionService.lookupTermForceLlm(term, contextQuery);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("term", result.getColloquialTerm());
                item.put("standardTerm", result.getStandardTerm());
                item.put("confidence", result.getConfidence());
                item.put("contextQuery", contextQuery);
                results.add(item);
                successCount++;
            } catch (Exception e) {
                log.debug("批量LLM推断单条失败: term={}", term, e);
                results.add(Map.of(
                        "term", term,
                        "standardTerm", null,
                        "confidence", 0.0,
                        "error", e.getMessage()
                ));
                failCount++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", pairs.size(),
                "successCount", successCount,
                "failCount", failCount,
                "results", results
        ));
    }
}
