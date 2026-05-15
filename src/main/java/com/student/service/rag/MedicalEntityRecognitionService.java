package com.student.service.rag;

import java.util.List;
import java.util.Map;

/**
 * 医学实体识别服务接口
 * 使用 HanLP + THUOCL 医学词库进行分词和命名实体识别
 * 抽取药品、疾病、症状、人群、身体部位等关键实体
 *
 * @author 系统
 * @version 1.0
 */
public interface MedicalEntityRecognitionService {

    /**
     * 对查询文本进行医学实体识别
     *
     * @param query 用户查询文本
     * @return 识别到的实体列表
     */
    List<MedicalEntity> recognize(String query);

    /**
     * 对查询文本进行分词（保留医学专业分词）
     *
     * @param query 用户查询文本
     * @return 分词结果列表
     */
    List<String> segment(String query);

    /**
     * 检查服务是否可用
     */
    boolean isAvailable();

    /**
     * 获取词库统计信息
     */
    Map<String, Object> getStats();

    // ========== 内部数据类型 ==========

    /** 医学实体类型 */
    enum EntityType {
        DRUG("药品"),
        DISEASE("疾病"),
        SYMPTOM("症状"),
        POPULATION("人群"),
        BODY_PART("身体部位"),
        EXAMINATION("检查"),
        TREATMENT("治疗"),
        OTHER("其他");

        private final String label;

        EntityType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** 医学实体 */
    class MedicalEntity {
        private final String text;
        private final EntityType type;
        private final double confidence;
        private final int startPos;
        private final int endPos;

        public MedicalEntity(String text, EntityType type, double confidence, int startPos, int endPos) {
            this.text = text;
            this.type = type;
            this.confidence = confidence;
            this.startPos = startPos;
            this.endPos = endPos;
        }

        public String getText() { return text; }
        public EntityType getType() { return type; }
        public double getConfidence() { return confidence; }
        public int getStartPos() { return startPos; }
        public int getEndPos() { return endPos; }

        @Override
        public String toString() {
            return String.format("%s[%s](%.2f)", text, type.getLabel(), confidence);
        }
    }
}
