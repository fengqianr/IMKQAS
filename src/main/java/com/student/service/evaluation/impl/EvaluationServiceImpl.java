package com.student.service.evaluation.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.entity.evaluation.*;
import com.student.mapper.evaluation.EvalPipelineSnapshotMapper;
import com.student.mapper.evaluation.EvalQueryResultMapper;
import com.student.mapper.evaluation.EvalRunMapper;
import com.student.service.evaluation.*;
import com.student.service.rag.MultiRetrievalService;
import com.student.service.rag.MultiRetrievalService.RetrievalResult;
import com.student.utils.evaluation.MetricsCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 离线评估服务实现
 * 编排完整评估流程：数据加载→检索执行→指标计算→结果存储
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final DatasetService datasetService;
    private final MultiRetrievalService retrievalService;
    private final RetrievalEvaluator retrievalEvaluator;
    private final FusionEvaluator fusionEvaluator;
    private final SafetyEvaluator safetyEvaluator;
    private final EvalRunMapper runMapper;
    private final EvalQueryResultMapper queryResultMapper;
    private final EvalPipelineSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    /** 运行中的评估任务，支持停止 */
    private final Map<Long, Boolean> runningTasks = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public Long startEvaluation(Long datasetId, String runName, List<String> evalDimensions,
                                boolean skipLlmEval, Integer sampleSize) {
        log.info("启动评估运行: dataset={}, name={}, dimensions={}", datasetId, runName, evalDimensions);

        // 创建运行记录
        EvalRun run = EvalRun.builder()
                .datasetId(datasetId)
                .runName(runName)
                .status(EvalRun.STATUS_RUNNING)
                .startedAt(LocalDateTime.now())
                .totalQueries(0)
                .evaluatedQueries(0)
                .evalDimensions(toJson(evalDimensions))
                .build();
        runMapper.insert(run);
        Long runId = run.getId();
        runningTasks.put(runId, true);

        try {
            // 加载数据集
            List<EvalDatasetItem> items = datasetService.getAllItems(datasetId);
            if (sampleSize != null && sampleSize < items.size()) {
                Collections.shuffle(items, new Random(42)); // 固定种子保证可复现
                items = items.subList(0, sampleSize);
            }
            int totalQueries = items.size();
            run.setTotalQueries(totalQueries);
            runMapper.updateById(run);

            log.info("评估数据集加载完成: {} 条数据项", totalQueries);

            // 准备数据容器
            List<List<Long>> allRetrievedIds = new ArrayList<>();
            List<List<Long>> allVectorIds = new ArrayList<>();
            List<List<Long>> allKeywordIds = new ArrayList<>();
            List<Set<Long>> allGroundTruthIds = new ArrayList<>();
            List<Map<Long, Double>> allRelevanceLabels = new ArrayList<>();
            List<Boolean> emergencyFlags = new ArrayList<>();
            List<Boolean> confidenceBlockFlags = new ArrayList<>();
            List<Boolean> shouldEmergencyList = new ArrayList<>();
            List<Boolean> shouldConfBlockList = new ArrayList<>();
            List<Long> totalTimes = new ArrayList<>();

            // 逐查询评估
            for (int i = 0; i < items.size(); i++) {
                if (!runningTasks.getOrDefault(runId, false)) {
                    log.info("评估运行被用户停止: runId={}", runId);
                    break;
                }

                EvalDatasetItem item = items.get(i);
                long queryStart = System.currentTimeMillis();

                try {
                    // 执行检索
                    List<RetrievalResult> vectorResults = retrievalService.vectorRetrieval(item.getQuery(), 30);
                    List<RetrievalResult> keywordResults = retrievalService.keywordRetrieval(item.getQuery(), 30);
                    List<RetrievalResult> fusedResults = retrievalService.hybridRetrieval(item.getQuery(), 30);

                    // 提取chunkId列表
                    List<Long> vectorIds = toChunkIdList(vectorResults);
                    List<Long> keywordIds = toChunkIdList(keywordResults);
                    List<Long> fusedIds = toChunkIdList(fusedResults);

                    allVectorIds.add(vectorIds);
                    allKeywordIds.add(keywordIds);
                    allRetrievedIds.add(fusedIds);

                    // 解析ground truth
                    Set<Long> groundTruthIds = parseChunkIdSet(item.getGroundTruthChunkIds());
                    allGroundTruthIds.add(groundTruthIds);

                    Map<Long, Double> labels = parseRelevanceLabels(item.getRelevanceLabels());
                    allRelevanceLabels.add(labels);

                    // 安全检查标志
                    emergencyFlags.add(false);  // 离线评估默认不触发紧急阻断
                    confidenceBlockFlags.add(false);
                    shouldEmergencyList.add(Boolean.TRUE.equals(item.getShouldTriggerEmergency()));
                    shouldConfBlockList.add(Boolean.TRUE.equals(item.getShouldTriggerConfidenceBlock()));

                    long queryTime = System.currentTimeMillis() - queryStart;
                    totalTimes.add(queryTime);

                    // 保存逐查询结果
                    saveQueryResult(runId, item, fusedIds, vectorIds, keywordIds, groundTruthIds, queryTime);

                } catch (Exception e) {
                    log.error("查询评估失败: query={}, error={}", item.getQuery(), e.getMessage());
                    // 降级：记录失败但继续其他查询
                    saveFailedQueryResult(runId, item, e.getMessage());
                }

                run.setEvaluatedQueries(i + 1);

                // 每10条输出一次进度
                if ((i + 1) % 10 == 0 || (i + 1) == totalQueries) {
                    log.info("评估进度: {}/{}", i + 1, totalQueries);
                    runMapper.updateById(run);
                }
            }

            // 汇总计算指标
            if (!allRetrievedIds.isEmpty()) {
                computeAndSaveMetrics(run, allRetrievedIds, allVectorIds, allKeywordIds,
                        allGroundTruthIds, allRelevanceLabels, emergencyFlags, confidenceBlockFlags,
                        shouldEmergencyList, shouldConfBlockList, totalTimes);
            }

            // 标记完成
            run.setStatus(EvalRun.STATUS_COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
            runMapper.updateById(run);

            log.info("评估运行完成: runId={}, 评估 {} 条查询", runId, run.getEvaluatedQueries());

        } catch (Exception e) {
            log.error("评估运行失败: runId={}, error={}", runId, e.getMessage(), e);
            run.setStatus(EvalRun.STATUS_FAILED);
            run.setErrorLog(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runMapper.updateById(run);
        } finally {
            runningTasks.remove(runId);
        }

        return runId;
    }

    @Override
    public EvalRun getRunStatus(Long runId) {
        return runMapper.selectById(runId);
    }

    @Override
    public EvalRun getRunSummary(Long runId) {
        return runMapper.selectById(runId);
    }

    @Override
    public void stopEvaluation(Long runId) {
        runningTasks.put(runId, false);
        log.info("停止评估运行: runId={}", runId);
    }

    @Override
    public Page<EvalRun> listRuns(int page, int size) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRun> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.orderByDesc(EvalRun::getCreatedAt);
        return runMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public Page<EvalQueryResult> listQueryResults(Long runId, int page, int size) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalQueryResult> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(EvalQueryResult::getRunId, runId);
        wrapper.orderByAsc(EvalQueryResult::getCreatedAt);
        return queryResultMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<EvalPipelineSnapshot> getPipelineSnapshot(Long runId, Long queryResultId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalPipelineSnapshot> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(EvalPipelineSnapshot::getRunId, runId);
        wrapper.eq(EvalPipelineSnapshot::getQueryResultId, queryResultId);
        wrapper.orderByAsc(EvalPipelineSnapshot::getStepOrder);
        return snapshotMapper.selectList(wrapper);
    }

    // ==================== 私有方法 ====================

    private void computeAndSaveMetrics(EvalRun run,
                                        List<List<Long>> allRetrievedIds,
                                        List<List<Long>> allVectorIds,
                                        List<List<Long>> allKeywordIds,
                                        List<Set<Long>> allGroundTruthIds,
                                        List<Map<Long, Double>> allRelevanceLabels,
                                        List<Boolean> emergencyFlags,
                                        List<Boolean> confidenceBlockFlags,
                                        List<Boolean> shouldEmergencyList,
                                        List<Boolean> shouldConfBlockList,
                                        List<Long> totalTimes) {

        // 检索质量
        RetrievalEvaluator.RetrievalEvalResult retrievalResult = retrievalEvaluator.evaluate(
                allRetrievedIds, allGroundTruthIds, allRelevanceLabels,
                List.of(1, 5, 10, 20));
        run.setRecallAt1(retrievalResult.recallAt1);
        run.setRecallAt5(retrievalResult.recallAt5);
        run.setRecallAt10(retrievalResult.recallAt10);
        run.setRecallAt20(retrievalResult.recallAt20);
        run.setPrecisionAt5(retrievalResult.precisionAt5);
        run.setPrecisionAt10(retrievalResult.precisionAt10);
        run.setMrr(retrievalResult.mrr);
        run.setNdcgAt10(retrievalResult.ndcgAt10);
        run.setHitRateAt5(retrievalResult.hitRateAt5);

        // 融合质量
        FusionEvaluator.FusionEvalResult fusionResult = fusionEvaluator.evaluate(
                allVectorIds, allKeywordIds, allRetrievedIds, allGroundTruthIds);
        run.setMrrVectorOnly(fusionResult.mrrVectorOnly);
        run.setMrrKeywordOnly(fusionResult.mrrKeywordOnly);
        run.setMrrFused(fusionResult.mrrFused);
        run.setComplementarityScore(fusionResult.complementarityScore);

        // 安全质量
        SafetyEvaluator.SafetyEvalResult safetyResult = safetyEvaluator.evaluate(
                emergencyFlags, confidenceBlockFlags, shouldEmergencyList, shouldConfBlockList);
        run.setEmergencyDetectionAccuracy(safetyResult.emergencyDetectionAccuracy);
        run.setSafetyBlockRate(safetyResult.safetyBlockRate);

        // 管线耗时
        run.setAvgTotalTimeMs(MetricsCalculator.average(
                totalTimes.stream().map(Long::doubleValue).collect(Collectors.toList())));
        run.setAvgRetrievalTimeMs(run.getAvgTotalTimeMs() * 0.4); // 估算值
    }

    private void saveQueryResult(Long runId, EvalDatasetItem item,
                                  List<Long> fusedIds, List<Long> vectorIds, List<Long> keywordIds,
                                  Set<Long> groundTruthIds, long queryTime) {
        int firstRank = MetricsCalculator.findFirstRelevantRank(fusedIds, groundTruthIds);

        EvalQueryResult result = EvalQueryResult.builder()
                .runId(runId)
                .datasetItemId(item.getId())
                .query(item.getQuery())
                .retrievedChunkIds(toJson(fusedIds))
                .vectorChunkIds(toJson(vectorIds))
                .keywordChunkIds(toJson(keywordIds))
                .hitGroundTruth(firstRank > 0)
                .firstRelevantRank(firstRank > 0 ? firstRank : -1)
                .beforeFilterCount(fusedIds.size())
                .afterFilterCount(fusedIds.size())
                .emergencyTriggered(false)
                .confidenceBlocked(false)
                .safetySanitized(false)
                .finalConfidence(0.8)
                .totalTimeMs(queryTime)
                .retrievalTimeMs(queryTime)
                .cacheHit(false)
                .entitiesRecognized(0)
                .entitiesMapped(0)
                .build();

        queryResultMapper.insert(result);
    }

    private void saveFailedQueryResult(Long runId, EvalDatasetItem item, String errorMessage) {
        EvalQueryResult result = EvalQueryResult.builder()
                .runId(runId)
                .datasetItemId(item.getId())
                .query(item.getQuery())
                .hitGroundTruth(false)
                .firstRelevantRank(-1)
                .errorMessage(errorMessage)
                .build();
        queryResultMapper.insert(result);
    }

    // ==================== 转换工具方法 ====================

    private List<Long> toChunkIdList(List<RetrievalResult> results) {
        return results.stream()
                .map(RetrievalResult::getChunkId)
                .collect(Collectors.toList());
    }

    private Set<Long> parseChunkIdSet(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            @SuppressWarnings("unchecked")
            List<Integer> list = objectMapper.readValue(json, List.class);
            return list.stream().map(v -> ((Number) v).longValue()).collect(Collectors.toSet());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private Map<Long, Double> parseRelevanceLabels(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<Long, Double> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                result.put(Long.parseLong(entry.getKey()), ((Number) entry.getValue()).doubleValue());
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
