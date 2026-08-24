package com.imkqas.service.his;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 问卷匹配结果
 * 承载匹配到的问卷模板及其关键词命中数（用于动态计算置信度）。
 *
 * @author 系统
 * @version 1.0
 */
@Data
@AllArgsConstructor
public class QuestionnaireMatchResult {

    /** 匹配到的问卷模板 */
    private QuestionnaireTemplate template;

    /** 关键词命中数 */
    private int hitCount;
}
