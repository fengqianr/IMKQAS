package com.student.service.triage.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.student.dto.triage.DepartmentKnowledgeBase;
import com.student.model.triage.DepartmentKnowledge;
import com.student.model.triage.EmergencyCheckResult;
import com.student.model.triage.EmergencyLevel;
import com.student.service.triage.config.TriageConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 急诊症状检测器
 * 负责检测症状中的急诊症状并进行分级
 *
 * @author 系统生成
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class EmergencySymptomDetector {

    private final DepartmentKnowledgeBase knowledgeBase;
    private final TriageConfig config;
    private final SymptomNormalizer symptomNormalizer = new SymptomNormalizer(null, false, 0.0);

    /**
     * 检测急诊症状
     *
     * @param symptoms 症状描述
     * @return 急诊检测结果
     */
    public EmergencyCheckResult detect(String symptoms) {
        return detect(symptoms, null);
    }

    /**
     * 检测急诊症状（支持异步执行）
     *
     * @param symptoms 症状描述
     * @param executorService 线程池（可选）
     * @return 急诊检测结果
     */
    public EmergencyCheckResult detect(String symptoms, ExecutorService executorService) {
        if (!config.isEnableEmergencyDetection()) {
            log.debug("急诊检测已禁用");
            return createNoEmergencyResult();
        }

        long startTime = System.currentTimeMillis();

        try {
            // 异步执行检测（如果提供了线程池）
            if (executorService != null) {
                CompletableFuture<EmergencyCheckResult> future = CompletableFuture.supplyAsync(
                    () -> performDetection(symptoms),
                    executorService
                );

                try {
                    return future.get(config.getEmergencyDetectionTimeout(), TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    log.warn("急诊检测超时或异常，返回无急诊结果", e);
                    return createNoEmergencyResult();
                }
            } else {
                // 同步执行
                return performDetection(symptoms);
            }

        } catch (Exception e) {
            log.error("急诊检测异常: symptoms={}", symptoms, e);
            return createNoEmergencyResult();
        } finally {
            long processingTime = System.currentTimeMillis() - startTime;
            log.debug("急诊检测完成: 处理时间={}ms", processingTime);
        }
    }

    /**
     * 执行实际的急诊检测
     */
    private EmergencyCheckResult performDetection(String symptoms) {
        if (symptoms == null || symptoms.trim().isEmpty()) {
            return createNoEmergencyResult();
        }

        // 1. 标准化症状
        String normalizedSymptoms = symptomNormalizer.normalize(symptoms);
        List<String> symptomList = symptomNormalizer.splitSymptoms(normalizedSymptoms);

        if (symptomList.isEmpty()) {
            return createNoEmergencyResult();
        }

        // 2. 检测急诊症状
        List<String> detectedEmergencySymptoms = new ArrayList<>();
        EmergencyLevel emergencyLevel = EmergencyLevel.LOW;

        // 获取所有急诊科室
        List<DepartmentKnowledge> emergencyDepts = knowledgeBase.getEmergencyDepartments();
        if (emergencyDepts.isEmpty()) {
            log.warn("知识库中没有急诊科室配置");
            return createNoEmergencyResult();
        }

        // 3. 检查每个急诊科室的症状
        for (DepartmentKnowledge dept : emergencyDepts) {
            if (!dept.isEmergency() || dept.getSymptoms() == null) {
                continue;
            }

            // 检查症状匹配
            List<String> matchedSymptoms = findMatchedSymptoms(symptomList, dept.getSymptoms());
            if (!matchedSymptoms.isEmpty()) {
                detectedEmergencySymptoms.addAll(matchedSymptoms);

                // 根据匹配症状数量确定紧急级别
                EmergencyLevel deptLevel = determineEmergencyLevel(matchedSymptoms.size(), dept);
                if (deptLevel.getPriority() < emergencyLevel.getPriority()) {
                    emergencyLevel = deptLevel;
                }

                log.debug("发现急诊症状匹配: 科室={}, 匹配症状={}, 紧急级别={}",
                    dept.getName(), matchedSymptoms, deptLevel);
            }
        }

        // 4. 构建结果
        if (detectedEmergencySymptoms.isEmpty()) {
            return createNoEmergencyResult();
        }

        return createEmergencyResult(detectedEmergencySymptoms, emergencyLevel);
    }

    /**
     * 查找匹配的症状
     */
    private List<String> findMatchedSymptoms(List<String> patientSymptoms, List<String> emergencySymptoms) {
        List<String> matched = new ArrayList<>();

        for (String patientSymptom : patientSymptoms) {
            for (String emergencySymptom : emergencySymptoms) {
                if (isSymptomMatch(patientSymptom, emergencySymptom)) {
                    log.info("症状匹配: patientSymptom={}, emergencySymptom={}", patientSymptom, emergencySymptom);
                    matched.add(patientSymptom);
                    break; // 每个患者症状只匹配一次
                }
            }
        }

        log.info("找到匹配症状: patientSymptoms={}, emergencySymptoms={}, matched={}", patientSymptoms, emergencySymptoms, matched);
        return matched;
    }

    /**
     * 判断症状是否匹配
     */
    private boolean isSymptomMatch(String patientSymptom, String emergencySymptom) {
        // 完全匹配
        if (patientSymptom.equals(emergencySymptom)) {
            return true;
        }

        // 包含匹配（如果急诊症状是患者症状的子串）
        if (patientSymptom.contains(emergencySymptom)) {
            return true;
        }

        // 使用症状标准化器进行模糊匹配
        if (config.isEnableFuzzyMatch()) {
            SymptomNormalizer fuzzyNormalizer = new SymptomNormalizer(null, true, config.getFuzzyMatchThreshold());
            String normalizedPatient = fuzzyNormalizer.normalize(patientSymptom);
            String normalizedEmergency = fuzzyNormalizer.normalize(emergencySymptom);

            // 检查标准化后的症状是否匹配
            if (normalizedPatient.contains(normalizedEmergency) || normalizedEmergency.contains(normalizedPatient)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 确定紧急级别
     */
    private EmergencyLevel determineEmergencyLevel(int matchedSymptomsCount, DepartmentKnowledge dept) {
        // 首先检查科室是否有预设的紧急级别
        if (dept.getEmergencyLevel() != null) {
            log.info("使用科室预设紧急级别: dept={}, emergencyLevel={}", dept.getName(), dept.getEmergencyLevel());
            return EmergencyLevel.fromString(dept.getEmergencyLevel());
        }

        log.info("根据匹配症状数量确定紧急级别: matchedSymptomsCount={}, thresholds[CRITICAL={}, HIGH={}, MEDIUM={}]",
            matchedSymptomsCount,
            config.getEmergencyThreshold(EmergencyLevel.CRITICAL),
            config.getEmergencyThreshold(EmergencyLevel.HIGH),
            config.getEmergencyThreshold(EmergencyLevel.MEDIUM));

        // 根据匹配症状数量确定级别
        if (matchedSymptomsCount >= config.getEmergencyThreshold(EmergencyLevel.CRITICAL)) {
            return EmergencyLevel.CRITICAL;
        } else if (matchedSymptomsCount >= config.getEmergencyThreshold(EmergencyLevel.HIGH)) {
            return EmergencyLevel.HIGH;
        } else if (matchedSymptomsCount >= config.getEmergencyThreshold(EmergencyLevel.MEDIUM)) {
            return EmergencyLevel.MEDIUM;
        } else {
            return EmergencyLevel.LOW;
        }
    }

    /**
     * 创建无急诊结果
     */
    private EmergencyCheckResult createNoEmergencyResult() {
        EmergencyCheckResult result = new EmergencyCheckResult();
        result.setEmergency(false);
        result.setEmergencyLevel(EmergencyLevel.LOW);
        result.setEmergencySymptoms(new ArrayList<>());
        result.setAdvice("未检测到需要紧急处理的症状，可按常规流程就诊");
        return result;
    }

    /**
     * 创建有急诊结果
     */
    private EmergencyCheckResult createEmergencyResult(List<String> emergencySymptoms, EmergencyLevel level) {
        EmergencyCheckResult result = new EmergencyCheckResult();
        result.setEmergency(true);
        result.setEmergencyLevel(level);
        result.setEmergencySymptoms(emergencySymptoms);
        result.setAdvice(generateEmergencyAdvice(level, emergencySymptoms));
        return result;
    }

    /**
     * 生成急诊建议
     */
    private String generateEmergencyAdvice(EmergencyLevel level, List<String> symptoms) {
        switch (level) {
            case CRITICAL:
                return String.format("检测到危重症状: %s。请立即前往急诊科或拨打120急救电话！",
                    String.join("、", symptoms));
            case HIGH:
                return String.format("检测到严重症状: %s。建议立即就医，优先考虑急诊科。",
                    String.join("、", symptoms));
            case MEDIUM:
                return String.format("检测到需要关注的症状: %s。建议尽快就诊，可选择急诊科或相关专科。",
                    String.join("、", symptoms));
            case LOW:
                return String.format("检测到轻微急诊症状: %s。建议及时就医，可按常规流程就诊。",
                    String.join("、", symptoms));
            default:
                return "检测到急诊症状，建议及时就医。";
        }
    }

    /**
     * 批量检测急诊症状
     *
     * @param symptomsList 症状列表
     * @return 急诊检测结果列表
     */
    public List<EmergencyCheckResult> batchDetect(List<String> symptomsList) {
        List<EmergencyCheckResult> results = new ArrayList<>();

        for (String symptoms : symptomsList) {
            results.add(detect(symptoms));
        }

        return results;
    }

    /**
     * 获取急诊科室列表（用于调试）
     */
    public List<DepartmentKnowledge> getEmergencyDepartments() {
        return knowledgeBase.getEmergencyDepartments();
    }

    /**
     * 检查是否启用急诊检测
     */
    public boolean isEmergencyDetectionEnabled() {
        return config.isEnableEmergencyDetection();
    }

    /**
     * 获取配置的急诊阈值
     */
    public int getEmergencyThreshold(EmergencyLevel level) {
        return config.getEmergencyThreshold(level);
    }
}