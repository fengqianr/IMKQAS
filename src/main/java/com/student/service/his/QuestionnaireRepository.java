package com.student.service.his;

import java.util.List;
import java.util.Optional;

/**
 * 问卷模板库接口
 * 内置标准量表（PHQ-9, GAD-7, ISI等）
 *
 * @author 系统
 * @version 1.0
 */
public interface QuestionnaireRepository {

    /**
     * 根据ID获取问卷模板
     */
    Optional<QuestionnaireTemplate> findById(String id);

    /**
     * 获取所有可用问卷
     */
    List<QuestionnaireTemplate> findAll();

    /**
     * 根据关键词匹配问卷
     */
    List<QuestionnaireTemplate> matchByKeywords(String userInput);

    /**
     * 按分类获取问卷
     */
    List<QuestionnaireTemplate> findByCategory(String category);
}
