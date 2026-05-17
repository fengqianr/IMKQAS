package com.student.service.rag;

import java.util.List;
import java.util.Map;

/**
 * 多因子重排序服务接口
 * 综合权威性、时效性、语义相似度、意图匹配度四个维度进行精确重排序
 * <p>
 * 公式：final = wa * authority + wt * timeliness + ws * semantic + wi * intent
 *
 * @author 系统
 * @version 1.0
 */
public interface MultiFactorRerankService {

    /**
     * 多因子重排序
     *
     * @param query   查询文本
     * @param results RRF融合后的候选结果
     * @param topK    返回数量
     * @return 精排后的结果
     */
    List<MultiRetrievalService.RetrievalResult> rerank(
            String query,
            List<MultiRetrievalService.RetrievalResult> results,
            int topK);

    /**
     * 使用默认topK重排序
     */
    List<MultiRetrievalService.RetrievalResult> rerank(
            String query,
            List<MultiRetrievalService.RetrievalResult> results);

    /**
     * 计算单个结果的综合分数
     *
     * @param query        查询文本
     * @param result       检索结果
     * @param semanticScore 语义相似度分数
     * @return 综合分数 (0~1)
     */
    double calculateFinalScore(String query, MultiRetrievalService.RetrievalResult result, double semanticScore);

    /** 知识类型枚举（用于时效性半衰期映射） */
    enum KnowledgeType {
        TREATMENT("治疗方案/药物指南"),
        DIAGNOSIS("诊断标准/疾病分类"),
        DEVICE("医疗器械/技术参数"),
        BASIC_SCIENCE("基础医学知识"),
        PUBLIC_HEALTH("公共卫生政策"),
        CLASSIC_RESEARCH("历史病例/经典研究"),
        UNKNOWN("未知类型");

        private final String label;

        KnowledgeType(String label) { this.label = label; }

        public String getLabel() { return label; }

        /** 根据字符串推断知识类型（启发式匹配） */
        public static KnowledgeType infer(String text) {
            if (text == null) return UNKNOWN;
            String lower = text.toLowerCase();
            // 治疗/药物相关
            if (containsAny(lower, "治疗", "用药", "药物", "剂量", "处方", "手术", "治疗方案",
                    "指南", "共识", "treatment", "drug", "therapy", "medication")) {
                return TREATMENT;
            }
            // 诊断/分类相关
            if (containsAny(lower, "诊断", "分类", "标准", "dsm", "icd", "diagnosis",
                    "criteria", "classification")) {
                return DIAGNOSIS;
            }
            // 器械/技术相关
            if (containsAny(lower, "器械", "设备", "技术参数", "ct", "mri", "手术机器人",
                    "device", "equipment", "technology")) {
                return DEVICE;
            }
            // 基础医学
            if (containsAny(lower, "解剖", "生理", "生化", "通路", "机制", "anatomy",
                    "physiology", "biochemistry", "pathway", "mechanism")) {
                return BASIC_SCIENCE;
            }
            // 公共卫生
            if (containsAny(lower, "疫苗", "筛查", "公共卫生", "流行病", "vaccine",
                    "screening", "public health", "epidemiology")) {
                return PUBLIC_HEALTH;
            }
            // 经典研究
            if (containsAny(lower, "里程碑", "经典", "196", "197", "198", "landmark",
                    "classic", "pioneering")) {
                return CLASSIC_RESEARCH;
            }
            return UNKNOWN;
        }

        private static boolean containsAny(String text, String... keywords) {
            for (String kw : keywords) {
                if (text.contains(kw)) return true;
            }
            return false;
        }
    }

    /** 来源类型 → 权威系数的标准映射 */
    enum AuthorityLevel {
        SYSTEMATIC_REVIEW("系统评价/Meta分析/临床指南", 1.0),
        DRUG_LABEL("药品说明书(FDA/NMPA)", 0.95),
        RCT("随机对照试验(RCT)", 0.9),
        COHORT_STUDY("队列研究/病例对照研究", 0.7),
        HOSPITAL_SCIENCE("医院官方科普/医学百科", 0.6),
        EXPERT_OPINION("专家意见/病例报告", 0.5),
        GENERAL_WEBSITE("普通网站/自媒体", 0.3),
        FORUM_QA("论坛/问答", 0.1);

        private final String description;
        private final double authorityScore;

        AuthorityLevel(String description, double authorityScore) {
            this.description = description;
            this.authorityScore = authorityScore;
        }

        public String getDescription() { return description; }

        public double getAuthorityScore() { return authorityScore; }

        /** 从 metadata 中的 source_type 映射到权威分数 */
        public static double fromSourceType(String sourceType) {
            if (sourceType == null) return 0.5;
            return switch (sourceType.toLowerCase()) {
                case "systematic_review", "meta_analysis", "clinical_guideline" -> SYSTEMATIC_REVIEW.authorityScore;
                case "drug_label" -> DRUG_LABEL.authorityScore;
                case "rct" -> RCT.authorityScore;
                case "cohort_study", "case_control" -> COHORT_STUDY.authorityScore;
                case "hospital_science", "medical_encyclopedia" -> HOSPITAL_SCIENCE.authorityScore;
                case "expert_opinion", "case_report" -> EXPERT_OPINION.authorityScore;
                case "general_website", "self_media" -> GENERAL_WEBSITE.authorityScore;
                case "forum", "qa" -> FORUM_QA.authorityScore;
                default -> 0.5;
            };
        }
    }
}
