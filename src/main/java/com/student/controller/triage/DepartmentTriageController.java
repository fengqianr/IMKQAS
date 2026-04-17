package com.student.controller.triage;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.student.dto.triage.BatchTriageRequest;
import com.student.dto.triage.TriageRequest;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.TriageStats;
import com.student.service.triage.TriageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * 科室导诊REST控制器
 * 提供症状分流相关API接口，支持单次分流、批量分流、统计查询等功能
 *
 * @author 系统生成
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/triage")
@Tag(name = "科室导诊服务", description = "医疗症状智能分流服务，根据症状描述推荐就诊科室")
public class DepartmentTriageController {

    private final TriageService triageService;

    @Autowired
    public DepartmentTriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    /**
     * 单次症状分流接口
     * 根据单个症状描述推荐最合适的就诊科室
     *
     * @param request 分流请求，包含症状描述和用户ID
     * @return 分流结果，包含科室推荐、置信度和急诊检测结果
     */
    @Operation(
        summary = "单次症状分流",
        description = "根据症状描述智能推荐就诊科室，支持急诊症状检测和分级"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "分流成功"),
        @ApiResponse(responseCode = "400", description = "请求参数无效"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PostMapping
    public ResponseEntity<DepartmentTriageResult> triage(
            @Parameter(description = "分流请求参数", required = true)
            @Valid @RequestBody TriageRequest request) {
        log.info("接收到单次分流请求: userId={}, symptoms='{}'",
            request.getUserId(), request.getMaskedSymptoms());

        try {
            if (!triageService.isAvailable()) {
                log.warn("分流服务不可用，拒绝请求");
                return ResponseEntity.status(503)
                    .body(createServiceUnavailableResult(request));
            }

            DepartmentTriageResult result = triageService.triage(request);
            log.info("单次分流请求处理完成: userId={}, confidence={}, source={}",
                request.getUserId(), result.getConfidence(), result.getSource());

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("请求参数无效: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(createErrorResult(request.getSymptoms(), e.getMessage(), request.getUserId()));
        } catch (Exception e) {
            log.error("分流处理异常: userId={}", request.getUserId(), e);
            return ResponseEntity.internalServerError()
                .body(createErrorResult(request.getSymptoms(), "服务器内部错误", request.getUserId()));
        }
    }

    /**
     * 批量症状分流接口
     * 同时处理多个症状描述，提高处理效率
     *
     * @param request 批量分流请求，包含症状列表
     * @return 分流结果列表，每个症状对应一个结果
     */
    @Operation(
        summary = "批量症状分流",
        description = "批量处理多个症状描述，返回对应的科室推荐结果"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "批量分流成功"),
        @ApiResponse(responseCode = "400", description = "请求参数无效"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PostMapping("/batch")
    public ResponseEntity<List<DepartmentTriageResult>> batchTriage(
            @Parameter(description = "批量分流请求参数", required = true)
            @Valid @RequestBody BatchTriageRequest request) {
        log.info("接收到批量分流请求: userId={}, batchSize={}",
            request.getUserId(), request.getBatchSize());

        try {
            if (!triageService.isAvailable()) {
                log.warn("分流服务不可用，拒绝批量请求");
                return ResponseEntity.status(503)
                    .body(List.of(createServiceUnavailableResultForBatch(request)));
            }

            List<DepartmentTriageResult> results = triageService.batchTriage(request);
            log.info("批量分流请求处理完成: userId={}, batchSize={}, successCount={}",
                request.getUserId(), request.getBatchSize(), results.size());

            return ResponseEntity.ok(results);

        } catch (IllegalArgumentException e) {
            log.warn("批量请求参数无效: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("批量分流处理异常: userId={}", request.getUserId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取服务统计信息
     * 包括请求总数、成功率、响应时间等指标
     *
     * @return 服务统计信息
     */
    @Operation(
        summary = "获取服务统计信息",
        description = "查看分流服务的运行统计，包括请求量、成功率、响应时间等"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取统计成功"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @GetMapping("/stats")
    public ResponseEntity<TriageStats> getStats() {
        log.debug("接收到统计查询请求");

        try {
            TriageStats stats = triageService.getStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取统计信息异常", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 服务健康检查接口
     * 检查分流服务及其依赖组件的可用状态
     *
     * @return 服务健康状态
     */
    @Operation(
        summary = "服务健康检查",
        description = "检查分流服务及其依赖组件（规则引擎、LLM等）的可用状态"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "服务健康"),
        @ApiResponse(responseCode = "503", description = "服务不可用")
    })
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> healthCheck() {
        log.debug("接收到健康检查请求");

        boolean serviceAvailable = triageService.isAvailable();
        TriageStats stats = triageService.getStats();

        HealthResponse response = new HealthResponse();
        response.setServiceAvailable(serviceAvailable);
        response.setTotalRequests(stats.getTotalRequests());
        response.setSuccessRate(stats.getSuccessRate());
        response.setAverageResponseTime(stats.getAvgProcessingTime());
        response.setTimestamp(java.time.LocalDateTime.now());

        if (serviceAvailable) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(503).body(response);
        }
    }

    /**
     * 创建服务不可用结果
     */
    private DepartmentTriageResult createServiceUnavailableResult(TriageRequest request) {
        DepartmentTriageResult result = new DepartmentTriageResult();
        result.setSymptoms(request.getSymptoms());
        result.setRecommendations(List.of());
        result.setConfidence(0.0);
        result.setSource("FALLBACK");
        result.setAdvice("分流服务暂时不可用，请稍后重试或咨询导诊台");
        result.setUserId(request.getUserId());
        result.setProcessingTimeMs(0L);
        return result;
    }

    /**
     * 创建批量服务不可用结果
     */
    private DepartmentTriageResult createServiceUnavailableResultForBatch(BatchTriageRequest request) {
        DepartmentTriageResult result = new DepartmentTriageResult();
        result.setSymptoms("");
        result.setRecommendations(List.of());
        result.setConfidence(0.0);
        result.setSource("FALLBACK");
        result.setAdvice("分流服务暂时不可用，请稍后重试或咨询导诊台");
        result.setUserId(request.getUserId());
        result.setProcessingTimeMs(0L);
        return result;
    }

    /**
     * 创建错误结果
     */
    private DepartmentTriageResult createErrorResult(String symptoms, String errorMessage, Long userId) {
        DepartmentTriageResult result = new DepartmentTriageResult();
        result.setSymptoms(symptoms);
        result.setRecommendations(List.of());
        result.setConfidence(0.0);
        result.setSource("FALLBACK");
        result.setAdvice("分流处理失败: " + errorMessage);
        result.setUserId(userId);
        result.setProcessingTimeMs(0L);
        return result;
    }

    /**
     * 健康检查响应类
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HealthResponse {
        private boolean serviceAvailable;
        private int totalRequests;
        private double successRate;
        private double averageResponseTime;
        private java.time.LocalDateTime timestamp;
    }
}