package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分析层输入 —— 只读事实包，不包含可供重新计算的数据
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisInput {

    /** 元信息 */
    private AnalysisMeta meta;

    /** 规则计算结果（已完成计算，只读引用） */
    private RuleResultBlock ruleResults;

    /** 患者上下文（已脱敏） */
    private PatientContextBlock patientContext;

    /** 各题用户原始表述 */
    private List<RawInputItem> rawInputs;

    /** 安全状态 */
    private SafetyStatusBlock safetyStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisMeta {
        private String questionnaireTitle;
        private String collectionDate;
        private String analysisId;
        private String collectionMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleResultBlock {
        private int totalScore;
        private int maxScore;
        private String riskLevel;
        private String riskLabel;
        private List<FlagItem> flags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlagItem {
        private String type;
        private String label;
        private String condition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientContextBlock {
        private String ageRange;
        private String gender;
        private int assessmentCount;
        private TrendBlock trend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendBlock {
        private String direction;
        private int delta;
        private int previousScore;
        private String previousDate;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawInputItem {
        private String questionText;
        private String userSaid;
        /** 答案来源：llm / rule_parser / direct_input / fallback */
        private String source;
        /** 置信度 0.0-1.0，可空兼容旧数据 */
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SafetyStatusBlock {
        private boolean hasEmergencyFlag;
        private List<String> triggeredKeywords;
        private boolean wasInterrupted;
    }
}
