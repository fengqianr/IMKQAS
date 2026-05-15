package com.student.service.evaluation;

import java.util.List;

/**
 * 安全质量评估器接口
 * 评估三层安全兜底的有效性
 *
 * @author 系统
 * @version 1.0
 */
public interface SafetyEvaluator {

    /**
     * 安全评估结果
     */
    class SafetyEvalResult {
        /** 紧急检测准确率（正确阻断/应阻断总数） */
        public double emergencyDetectionAccuracy;
        /** 紧急检测精确率（正确阻断/实际阻断总数） */
        public double emergencyDetectionPrecision;
        /** 紧急检测召回率 */
        public double emergencyDetectionRecall;
        /** 安全兜底触发率 */
        public double safetyBlockRate;
        /** 误阻断率（不应阻断但被阻断） */
        public double falseBlockRate;
        /** 置信度门控有效率 */
        public double confidenceGateEffectiveness;
        /** 评估查询数 */
        public int evaluatedQueries;
        /** 实际触发紧急阻断数 */
        public int actualEmergencyBlocks;
        /** 应触发紧急阻断数 */
        public int expectedEmergencyBlocks;
        /** 正确触发紧急阻断数 */
        public int correctEmergencyBlocks;
        /** 实际触发置信度阻断数 */
        public int actualConfidenceBlocks;
    }

    /**
     * 评估安全质量
     *
     * @param emergencyTriggeredFlags      每个查询是否触发了紧急阻断
     * @param confidenceBlockedFlags       每个查询是否触发了置信度阻断
     * @param shouldTriggerEmergency       每个查询是否应该触发紧急阻断
     * @param shouldTriggerConfidenceBlock 每个查询是否应该触发置信度阻断
     * @return 安全评估结果
     */
    SafetyEvalResult evaluate(
            List<Boolean> emergencyTriggeredFlags,
            List<Boolean> confidenceBlockedFlags,
            List<Boolean> shouldTriggerEmergency,
            List<Boolean> shouldTriggerConfidenceBlock);
}
