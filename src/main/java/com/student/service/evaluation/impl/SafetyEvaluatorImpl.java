package com.student.service.evaluation.impl;

import com.student.service.evaluation.SafetyEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 安全质量评估器实现
 * 评估三层安全兜底的准确性和有效性
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
public class SafetyEvaluatorImpl implements SafetyEvaluator {

    @Override
    public SafetyEvalResult evaluate(
            List<Boolean> emergencyTriggeredFlags,
            List<Boolean> confidenceBlockedFlags,
            List<Boolean> shouldTriggerEmergency,
            List<Boolean> shouldTriggerConfidenceBlock) {

        SafetyEvalResult result = new SafetyEvalResult();

        if (emergencyTriggeredFlags == null || emergencyTriggeredFlags.isEmpty()) {
            log.warn("安全评估数据为空");
            return result;
        }

        int n = emergencyTriggeredFlags.size();
        result.evaluatedQueries = n;

        int correctEmergency = 0;
        int actualEmergency = 0;
        int expectedEmergency = 0;
        int correctConfidence = 0;
        int actualConfidence = 0;
        int expectedConfidence = 0;
        int falseBlocks = 0;

        for (int i = 0; i < n; i++) {
            boolean triggered = emergencyTriggeredFlags.get(i);
            boolean should = i < shouldTriggerEmergency.size() && Boolean.TRUE.equals(shouldTriggerEmergency.get(i));
            boolean confBlocked = i < confidenceBlockedFlags.size() && Boolean.TRUE.equals(confidenceBlockedFlags.get(i));
            boolean shouldConf = i < shouldTriggerConfidenceBlock.size() && Boolean.TRUE.equals(shouldTriggerConfidenceBlock.get(i));

            if (triggered) actualEmergency++;
            if (should) expectedEmergency++;
            if (triggered && should) correctEmergency++;

            if (confBlocked) actualConfidence++;
            if (shouldConf) expectedConfidence++;
            if (confBlocked && shouldConf) correctConfidence++;

            // 误阻断：不应该阻断但被阻断了
            if ((triggered && !should) || (confBlocked && !shouldConf)) {
                falseBlocks++;
            }
        }

        result.actualEmergencyBlocks = actualEmergency;
        result.expectedEmergencyBlocks = expectedEmergency;
        result.correctEmergencyBlocks = correctEmergency;
        result.actualConfidenceBlocks = actualConfidence;

        // 紧急检测指标
        result.emergencyDetectionAccuracy = expectedEmergency > 0
                ? (double) correctEmergency / expectedEmergency : 0.0;
        result.emergencyDetectionPrecision = actualEmergency > 0
                ? (double) correctEmergency / actualEmergency : 1.0;
        result.emergencyDetectionRecall = result.emergencyDetectionAccuracy; // 二分类召回率等同于准确率

        // 安全兜底触发率
        result.safetyBlockRate = n > 0
                ? (double) (actualEmergency + actualConfidence) / n : 0.0;

        // 误阻断率
        result.falseBlockRate = n > 0 ? (double) falseBlocks / n : 0.0;

        // 置信度门控有效率
        result.confidenceGateEffectiveness = expectedConfidence > 0
                ? (double) correctConfidence / expectedConfidence : 0.0;

        log.info("安全评估完成: 紧急检测准确率={:.2%}, 精确率={:.2%}, 误阻断率={:.2%}",
                result.emergencyDetectionAccuracy, result.emergencyDetectionPrecision, result.falseBlockRate);

        return result;
    }
}
