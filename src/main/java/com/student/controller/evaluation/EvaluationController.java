package com.student.controller.evaluation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.student.entity.evaluation.*;
import com.student.service.evaluation.DatasetService;
import com.student.service.evaluation.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评估管理REST控制器
 * 提供离线评估和在线监控的API端点
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@Tag(name = "评估管理", description = "RAG系统离线评估和在线监控API")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final DatasetService datasetService;

    // ==================== 数据集管理 ====================

    @PostMapping("/datasets")
    @Operation(summary = "创建评估数据集")
    public ResponseEntity<Map<String, Object>> createDataset(@RequestBody EvalDataset dataset) {
        EvalDataset created = datasetService.createDataset(dataset);
        return ResponseEntity.ok(Map.of("success", true, "id", created.getId()));
    }

    @GetMapping("/datasets")
    @Operation(summary = "查询数据集列表")
    public ResponseEntity<Page<EvalDataset>> listDatasets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(datasetService.listDatasets(page, size, domain, status));
    }

    @GetMapping("/datasets/{id}")
    @Operation(summary = "查询数据集详情")
    public ResponseEntity<EvalDataset> getDataset(@PathVariable Long id) {
        return ResponseEntity.ok(datasetService.getDataset(id));
    }

    @PutMapping("/datasets/{id}/status")
    @Operation(summary = "更新数据集状态")
    public ResponseEntity<Map<String, Object>> updateDatasetStatus(
            @PathVariable Long id, @RequestParam String status) {
        datasetService.updateDatasetStatus(id, status);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/datasets/{id}/items")
    @Operation(summary = "分页查询数据项")
    public ResponseEntity<Page<EvalDatasetItem>> listDatasetItems(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(datasetService.listItems(id, page, size));
    }

    @PostMapping("/datasets/{id}/items")
    @Operation(summary = "添加标注数据项")
    public ResponseEntity<Map<String, Object>> addDatasetItem(
            @PathVariable Long id, @RequestBody EvalDatasetItem item) {
        EvalDatasetItem added = datasetService.addItem(id, item);
        return ResponseEntity.ok(Map.of("success", true, "id", added.getId()));
    }

    @PostMapping("/datasets/{id}/items/batch")
    @Operation(summary = "批量导入标注数据项")
    public ResponseEntity<Map<String, Object>> batchImportItems(
            @PathVariable Long id, @RequestBody List<EvalDatasetItem> items) {
        int count = datasetService.batchImportItems(id, items);
        return ResponseEntity.ok(Map.of("success", true, "importedCount", count));
    }

    // ==================== 评估运行 ====================

    @PostMapping("/runs")
    @Operation(summary = "启动离线评估运行")
    public ResponseEntity<Map<String, Object>> startEvaluation(@RequestBody StartEvalRequest request) {
        Long runId = evaluationService.startEvaluation(
                request.getDatasetId(), request.getRunName(), request.getEvalDimensions(),
                request.isSkipLlmEval(), request.getSampleSize());
        return ResponseEntity.ok(Map.of("success", true, "runId", runId));
    }

    @GetMapping("/runs")
    @Operation(summary = "查询评估运行历史")
    public ResponseEntity<Page<EvalRun>> listRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(evaluationService.listRuns(page, size));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "查询评估运行详情和汇总指标")
    public ResponseEntity<EvalRun> getRunDetail(@PathVariable Long id) {
        return ResponseEntity.ok(evaluationService.getRunSummary(id));
    }

    @GetMapping("/runs/{id}/queries")
    @Operation(summary = "查询逐查询评估结果")
    public ResponseEntity<Page<EvalQueryResult>> listQueryResults(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(evaluationService.listQueryResults(id, page, size));
    }

    @GetMapping("/runs/{runId}/pipeline/{queryResultId}")
    @Operation(summary = "查询管线快照")
    public ResponseEntity<List<EvalPipelineSnapshot>> getPipelineSnapshot(
            @PathVariable Long runId, @PathVariable Long queryResultId) {
        return ResponseEntity.ok(evaluationService.getPipelineSnapshot(runId, queryResultId));
    }

    @PostMapping("/runs/{id}/stop")
    @Operation(summary = "停止正在进行的评估")
    public ResponseEntity<Map<String, Object>> stopEvaluation(@PathVariable Long id) {
        evaluationService.stopEvaluation(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ==================== 请求体 ====================

    @lombok.Data
    public static class StartEvalRequest {
        private Long datasetId;
        private String runName;
        private List<String> evalDimensions;
        private boolean skipLlmEval;
        private Integer sampleSize;
    }
}
