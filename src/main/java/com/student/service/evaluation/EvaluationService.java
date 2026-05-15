package com.student.service.evaluation;

import com.student.entity.evaluation.EvalRun;

import java.util.List;

/**
 * 离线评估服务接口
 * 编排完整的离线评估流程：数据集加载→管线执行→指标计算→结果存储
 *
 * @author 系统
 * @version 1.0
 */
public interface EvaluationService {

    /**
     * 启动评估运行
     *
     * @param datasetId      数据集ID
     * @param runName        运行名称
     * @param evalDimensions 评估维度列表（RETRIEVAL/FUSION/FILTERING/RERANKING/GENERATION/SAFETY/PIPELINE）
     * @param skipLlmEval    是否跳过LLM评判（仅评估检索，不调用LLM生成）
     * @param sampleSize     采样大小（null表示全量）
     * @return 运行记录ID
     */
    Long startEvaluation(Long datasetId, String runName, List<String> evalDimensions,
                         boolean skipLlmEval, Integer sampleSize);

    /**
     * 查询评估运行状态
     */
    EvalRun getRunStatus(Long runId);

    /**
     * 查询评估运行汇总指标
     *
     * @return EvalRun（包含所有汇总指标字段）
     */
    EvalRun getRunSummary(Long runId);

    /**
     * 停止正在进行的评估
     */
    void stopEvaluation(Long runId);

    /**
     * 分页查询评估运行历史
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<EvalRun> listRuns(int page, int size);

    /**
     * 分页查询某次运行的逐查询结果
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.student.entity.evaluation.EvalQueryResult>
    listQueryResults(Long runId, int page, int size);

    /**
     * 查询某次运行某查询的管线快照
     */
    List<com.student.entity.evaluation.EvalPipelineSnapshot> getPipelineSnapshot(Long runId, Long queryResultId);
}
