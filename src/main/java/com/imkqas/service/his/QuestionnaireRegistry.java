package com.imkqas.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 问卷注册表项（业务DTO）
 * 承载问卷匹配关键词与FHIR访问路径
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireRegistry {

    /** 问卷唯一标识（如PHQ-9） */
    private String questionnaireId;

    /** 问卷标题 */
    private String title;

    /** 分类：mental_health/diabetes/sleep/pain/general */
    private String category;

    /** 触发关键词列表（用于匹配用户输入） */
    private List<String> keywords;

    /** FHIR访问路径（如Questionnaire/PHQ-9） */
    private String fhirPath;

    /** 优先级（命中数相同时，值越小越优先） */
    private Integer priority;

    /** 状态：active/retired */
    private String status;

    /** 题目数 */
    private Integer itemCount;
}
