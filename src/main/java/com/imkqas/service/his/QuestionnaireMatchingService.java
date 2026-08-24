package com.imkqas.service.his;

import java.util.List;

/**
 * 问卷匹配引擎
 * 基于注册表关键词对用户输入做命中计数，并按（命中数降序、优先级升序）排序。
 *
 * @author 系统
 * @version 1.0
 */
public interface QuestionnaireMatchingService {

    /**
     * 匹配问卷
     *
     * @param userInput 用户输入文本
     * @return 按（命中数 desc, 优先级 asc）排序的匹配结果，仅包含至少命中一个关键词的问卷
     */
    List<QuestionnaireMatchResult> match(String userInput);
}
