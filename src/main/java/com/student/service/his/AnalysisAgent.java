package com.student.service.his;

import java.util.concurrent.CompletableFuture;

/**
 * 分析Agent接口 —— 基于规则结果和原始表述生成共情分析报告
 * 严格"只读"：传入已算好的分数和风险等级，不传单项得分
 *
 * @author 系统
 * @version 1.0
 */
public interface AnalysisAgent {

    /**
     * 同步分析
     *
     * @param input 只读事实包
     * @return 结构化分析结果
     */
    AnalysisResult analyze(AnalysisInput input);

    /**
     * 异步分析
     *
     * @param input 只读事实包
     * @return 异步任务，返回分析结果
     */
    CompletableFuture<AnalysisResult> analyzeAsync(AnalysisInput input);

    /**
     * 判断LLM服务是否可用
     */
    boolean isAvailable();
}
