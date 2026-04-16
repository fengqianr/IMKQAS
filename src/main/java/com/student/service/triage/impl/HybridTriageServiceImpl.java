package com.student.service.triage.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.student.dto.triage.BatchTriageRequest;
import com.student.dto.triage.TriageRequest;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.EmergencyCheckResult;
import com.student.model.triage.TriageStats;
import com.student.service.triage.TriageService;
import com.student.service.triage.config.TriageConfig;
import com.student.service.triage.stats.TriageStatsCollector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 混合分流服务实现
 * 核心协调器，管理规则引擎、LLM适配器和急诊检测器的协同工作
 *
 * @author 系统生成
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class HybridTriageServiceImpl implements TriageService {

    private final SymptomNormalizer symptomNormalizer;
    private final RuleBasedTriageEngine ruleEngine;
    private final EmergencySymptomDetector emergencyDetector;
    private final LlmTriageAdapter llmAdapter;
    private final TriageConfig config;
    private final ExecutorService executorService;
    private final TriageStatsCollector statsCollector;

    // 服务状态
    private volatile boolean serviceAvailable = true;
    private final AtomicInteger totalRequestsProcessed = new AtomicInteger(0);
    private final AtomicLong totalServiceUptime = new AtomicLong(System.currentTimeMillis());

    /**
     * 单次症状分流
     */
    @Override
    public DepartmentTriageResult triage(TriageRequest request) {
        long startTime = System.currentTimeMillis();
        totalRequestsProcessed.incrementAndGet();

        try {
            validateRequest(request);

            // 1. 症状标准化
            String normalizedSymptoms = symptomNormalizer.normalize(request.getSymptoms());
            log.debug("分流请求处理开始: userId={}, symptoms='{}', normalized='{}'",
                request.getUserId(), request.getMaskedSymptoms(), normalizedSymptoms);

            // 2. 急诊检测（如果启用）
            EmergencyCheckResult emergencyResult = null;
            if (request.isIncludeEmergencyCheck()) {
                long emergencyStartTime = System.currentTimeMillis();
                emergencyResult = emergencyDetector.detect(normalizedSymptoms, executorService);
                long emergencyTime = System.currentTimeMillis() - emergencyStartTime;
                statsCollector.recordEngineProcessingTime("EMERGENCY", emergencyTime);
            }

            // 3. 混合分流决策
            DepartmentTriageResult triageResult = executeHybridTriage(normalizedSymptoms, emergencyResult);

            // 4. 设置处理时间和用户ID
            long processingTime = System.currentTimeMillis() - startTime;
            triageResult.setProcessingTimeMs(processingTime);
            triageResult.setUserId(request.getUserId());

            // 5. 记录统计信息
            statsCollector.recordTriageResult(triageResult, processingTime);

            log.info("分流请求处理完成: userId={}, symptoms='{}', confidence={}, source={}, time={}ms",
                request.getUserId(), request.getMaskedSymptoms(),
                triageResult.getConfidence(), triageResult.getSource(), processingTime);

            return triageResult;

        } catch (IllegalArgumentException e) {
            // 验证异常直接抛出，不记录为处理失败
            throw e;
        } catch (Exception e) {
            log.error("分流处理异常: userId={}, symptoms='{}'",
                request.getUserId(), request.getMaskedSymptoms(), e);

            // 记录失败统计
            statsCollector.recordTriageResult(null, System.currentTimeMillis() - startTime);

            return createFallbackResult(request.getSymptoms(),
                "分流处理失败: " + e.getMessage(), request.getUserId());
        }
    }

    /**
     * 批量症状分流
     */
    @Override
    public List<DepartmentTriageResult> batchTriage(BatchTriageRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            validateBatchRequest(request);

            int batchSize = request.getBatchSize();
            log.info("批量分流处理开始: userId={}, batchSize={}", request.getUserId(), batchSize);

            // 限制批量大小
            if (batchSize > config.getBatchProcessingMaxSize()) {
                throw new IllegalArgumentException(
                    String.format("批量大小超过限制: %d > %d",
                        batchSize, config.getBatchProcessingMaxSize()));
            }

            List<CompletableFuture<DepartmentTriageResult>> futures = new ArrayList<>();

            // 为每个症状创建异步处理任务
            for (String symptoms : request.getSymptomsList()) {
                TriageRequest singleRequest = new TriageRequest();
                singleRequest.setSymptoms(symptoms);
                singleRequest.setUserId(request.getUserId());
                singleRequest.setIncludeEmergencyCheck(request.isIncludeEmergencyCheck());

                CompletableFuture<DepartmentTriageResult> future = CompletableFuture.supplyAsync(
                    () -> triage(singleRequest),
                    executorService
                );

                futures.add(future);
            }

            // 等待所有任务完成（带超时）
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );

            try {
                allFutures.get(config.getBatchProcessingTimeout(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("批量处理超时或异常", e);
                statsCollector.recordTimeout();
            }

            // 收集结果
            List<DepartmentTriageResult> results = new ArrayList<>();
            for (CompletableFuture<DepartmentTriageResult> future : futures) {
                try {
                    if (future.isDone() && !future.isCompletedExceptionally()) {
                        results.add(future.get());
                    } else {
                        results.add(createFallbackResult("", "批量处理超时或失败", request.getUserId()));
                    }
                } catch (Exception e) {
                    results.add(createFallbackResult("", "任务执行异常: " + e.getMessage(), request.getUserId()));
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("批量分流处理完成: userId={}, batchSize={}, successCount={}, time={}ms",
                request.getUserId(), batchSize, results.size(), processingTime);

            return results;

        } catch (IllegalArgumentException e) {
            // 验证异常直接抛出，不记录为处理失败
            throw e;
        } catch (Exception e) {
            log.error("批量分流处理异常: userId={}", request.getUserId(), e);
            return createBatchFallbackResults(request);
        }
    }

    /**
     * 异步症状分流
     */
    @Override
    public CompletableFuture<DepartmentTriageResult> triageAsync(TriageRequest request) {
        return CompletableFuture.supplyAsync(() -> triage(request), executorService);
    }

    /**
     * 获取服务统计信息
     */
    @Override
    public TriageStats getStats() {
        return statsCollector.getStats();
    }

    /**
     * 检查服务是否可用
     */
    @Override
    public boolean isAvailable() {
        // 检查核心组件状态
        boolean ruleEngineAvailable = true; // 规则引擎通常总是可用
        boolean llmAvailable = llmAdapter.isAvailable();
        // 如果急诊检测被禁用，视为检测器可用（不需要检测）
        boolean emergencyDetectorAvailable = !config.isEnableEmergencyDetection() || emergencyDetector != null;

        serviceAvailable = ruleEngineAvailable && emergencyDetectorAvailable;

        if (!serviceAvailable) {
            log.warn("分流服务不可用: ruleEngine={}, llm={}, emergencyDetector={}",
                ruleEngineAvailable, llmAvailable, emergencyDetectorAvailable);
        }

        return serviceAvailable;
    }

    /**
     * 执行混合分流决策
     * 顺序执行+条件触发策略：
     * 1. 先执行规则引擎
     * 2. 如果置信度低于阈值，触发LLM分析
     * 3. 融合结果
     */
    private DepartmentTriageResult executeHybridTriage(String normalizedSymptoms,
                                                      EmergencyCheckResult emergencyResult) {
        long ruleEngineStartTime = System.currentTimeMillis();

        // 1. 执行规则引擎（第一步）
        DepartmentTriageResult ruleResult = ruleEngine.analyze(normalizedSymptoms);
        long ruleEngineTime = System.currentTimeMillis() - ruleEngineStartTime;
        statsCollector.recordEngineProcessingTime("RULE_ENGINE", ruleEngineTime);

        // 2. 检查规则引擎置信度
        if (ruleResult.getConfidence() >= config.getRuleEngineThreshold()) {
            // 规则引擎置信度高，直接使用
            ruleResult.setEmergencyCheck(emergencyResult);
            ruleResult.setSource("RULE_ENGINE");
            log.debug("规则引擎置信度高({}≥{})，直接使用规则引擎结果",
                ruleResult.getConfidence(), config.getRuleEngineThreshold());
            return ruleResult;
        }

        log.debug("规则引擎置信度低({}<{})，触发LLM分析",
            ruleResult.getConfidence(), config.getRuleEngineThreshold());

        // 3. 置信度低，触发LLM分析（条件触发）
        if (llmAdapter.isAvailable()) {
            try {
                long llmStartTime = System.currentTimeMillis();

                // 异步执行LLM分析，带超时控制
                CompletableFuture<DepartmentTriageResult> llmFuture = CompletableFuture.supplyAsync(
                    () -> llmAdapter.analyze(normalizedSymptoms, false),
                    executorService
                );

                DepartmentTriageResult llmResult = llmFuture.get(
                    config.getLlmTimeout(),
                    TimeUnit.MILLISECONDS
                );

                long llmTime = System.currentTimeMillis() - llmStartTime;
                statsCollector.recordEngineProcessingTime("LLM", llmTime);

                if (llmResult != null && llmResult.getConfidence() > 0) {
                    // 4. 融合规则引擎和LLM结果
                    DepartmentTriageResult fusedResult = fuseResults(ruleResult, llmResult);
                    fusedResult.setEmergencyCheck(emergencyResult);
                    fusedResult.setSource("HYBRID");
                    log.debug("LLM分析成功，使用融合结果");
                    return fusedResult;
                } else {
                    log.warn("LLM分析返回无效结果，使用规则引擎结果");
                }

            } catch (Exception e) {
                log.warn("LLM分析失败: {}", e.getMessage());
                statsCollector.recordFallback("LLM分析失败: " + e.getMessage());
            }
        } else {
            log.debug("LLM服务不可用，跳过LLM分析");
        }

        // 5. LLM不可用或失败，返回规则引擎结果（即使置信度低）
        ruleResult.setEmergencyCheck(emergencyResult);
        ruleResult.setSource("RULE_ENGINE_FALLBACK");
        return ruleResult;
    }

    /**
     * 融合规则引擎和LLM结果
     */
    private DepartmentTriageResult fuseResults(DepartmentTriageResult ruleResult,
                                              DepartmentTriageResult llmResult) {
        // 权重配置
        double ruleWeight = config.getRuleEngineWeight();
        double llmWeight = config.getLlmWeight();

        // 确保权重和为1
        double totalWeight = ruleWeight + llmWeight;
        if (Math.abs(totalWeight - 1.0) > 0.001) {
            ruleWeight = ruleWeight / totalWeight;
            llmWeight = llmWeight / totalWeight;
        }

        // 计算融合置信度
        double fusedConfidence = ruleResult.getConfidence() * ruleWeight
                               + llmResult.getConfidence() * llmWeight;

        // 构建融合结果
        DepartmentTriageResult fusedResult = new DepartmentTriageResult();
        fusedResult.setSymptoms(ruleResult.getSymptoms());
        fusedResult.setConfidence(fusedConfidence);

        // 合并推荐列表（去重，按置信度排序）
        List<com.student.model.triage.DepartmentRecommendation> fusedRecommendations =
            mergeRecommendations(ruleResult.getRecommendations(), llmResult.getRecommendations());
        fusedResult.setRecommendations(fusedRecommendations);

        // 生成融合建议
        fusedResult.setAdvice(generateFusedAdvice(ruleResult, llmResult, fusedConfidence));

        log.debug("结果融合完成: ruleConfidence={}, llmConfidence={}, fusedConfidence={}, ruleWeight={}, llmWeight={}",
            ruleResult.getConfidence(), llmResult.getConfidence(),
            fusedConfidence, ruleWeight, llmWeight);

        return fusedResult;
    }

    /**
     * 合并推荐列表
     */
    private List<com.student.model.triage.DepartmentRecommendation> mergeRecommendations(
            List<com.student.model.triage.DepartmentRecommendation> ruleRecs,
            List<com.student.model.triage.DepartmentRecommendation> llmRecs) {
        List<com.student.model.triage.DepartmentRecommendation> merged = new ArrayList<>();

        // 添加规则引擎推荐
        if (ruleRecs != null) {
            merged.addAll(ruleRecs);
        }

        // 添加LLM推荐（去重）
        if (llmRecs != null) {
            for (com.student.model.triage.DepartmentRecommendation llmRec : llmRecs) {
                boolean duplicate = false;
                for (com.student.model.triage.DepartmentRecommendation existing : merged) {
                    if (existing.getDepartmentName().equals(llmRec.getDepartmentName())) {
                        duplicate = true;
                        // 更新置信度为两者平均值
                        existing.setConfidence((existing.getConfidence() + llmRec.getConfidence()) / 2);
                        break;
                    }
                }
                if (!duplicate) {
                    merged.add(llmRec);
                }
            }
        }

        // 按置信度排序
        merged.sort((r1, r2) -> Double.compare(r2.getConfidence(), r1.getConfidence()));

        // 限制数量
        if (merged.size() > config.getMaxRecommendations()) {
            merged = merged.subList(0, config.getMaxRecommendations());
        }

        return merged;
    }

    /**
     * 生成融合建议
     */
    private String generateFusedAdvice(DepartmentTriageResult ruleResult,
                                      DepartmentTriageResult llmResult,
                                      double fusedConfidence) {
        StringBuilder advice = new StringBuilder();

        if (fusedConfidence >= 0.7) {
            advice.append("基于规则和AI分析，");
        } else {
            advice.append("综合分析建议，");
        }

        if (!ruleResult.getRecommendations().isEmpty()) {
            String primaryDept = ruleResult.getRecommendations().get(0).getDepartmentName();
            advice.append("建议优先考虑").append(primaryDept);
        }

        if (!llmResult.getRecommendations().isEmpty()) {
            String llmPrimaryDept = llmResult.getRecommendations().get(0).getDepartmentName();
            if (!advice.toString().contains(llmPrimaryDept)) {
                advice.append("，AI补充推荐").append(llmPrimaryDept);
            }
        }

        advice.append("。请结合实际情况判断。");

        return advice.toString();
    }

    /**
     * 验证请求
     */
    private void validateRequest(TriageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (request.getSymptoms() == null || request.getSymptoms().trim().isEmpty()) {
            throw new IllegalArgumentException("症状描述不能为空");
        }
        if (request.getSymptoms().length() > 500) {
            throw new IllegalArgumentException("症状描述不能超过500字符");
        }
    }

    /**
     * 验证批量请求
     */
    private void validateBatchRequest(BatchTriageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("批量请求不能为空");
        }
        if (request.getSymptomsList() == null || request.getSymptomsList().isEmpty()) {
            throw new IllegalArgumentException("症状列表不能为空");
        }
        if (request.getSymptomsList().size() > config.getBatchProcessingMaxSize()) {
            throw new IllegalArgumentException(
                String.format("症状列表大小超过限制: %d > %d",
                    request.getSymptomsList().size(), config.getBatchProcessingMaxSize()));
        }
    }

    /**
     * 创建降级结果
     */
    private DepartmentTriageResult createFallbackResult(String symptoms, String errorMessage, Long userId) {
        DepartmentTriageResult result = new DepartmentTriageResult();
        result.setSymptoms(symptoms);
        result.setRecommendations(new ArrayList<>());
        result.setConfidence(0.0);
        result.setSource("FALLBACK");
        result.setAdvice(config.getFallbackAdvice() + "。错误: " + errorMessage);
        result.setUserId(userId);
        result.setProcessingTimeMs(0L);
        return result;
    }

    /**
     * 创建批量降级结果
     */
    private List<DepartmentTriageResult> createBatchFallbackResults(BatchTriageRequest request) {
        List<DepartmentTriageResult> results = new ArrayList<>();
        for (String symptoms : request.getSymptomsList()) {
            results.add(createFallbackResult(symptoms, "批量处理失败", request.getUserId()));
        }
        return results;
    }

    /**
     * 获取服务运行时间（毫秒）
     */
    public long getServiceUptime() {
        return System.currentTimeMillis() - totalServiceUptime.get();
    }

    /**
     * 获取处理请求总数
     */
    public int getTotalRequestsProcessed() {
        return totalRequestsProcessed.get();
    }

    /**
     * 重置服务状态
     */
    public void resetService() {
        totalRequestsProcessed.set(0);
        totalServiceUptime.set(System.currentTimeMillis());
        serviceAvailable = true;
        log.info("分流服务状态已重置");
    }
}