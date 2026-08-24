package com.imkqas.service.his;

import java.util.List;
import java.util.Optional;

/**
 * 问卷缓存协调服务
 * 协调三级缓存（内存 → Redis → FHIR）加载问卷模板，并维护问卷注册表关键词索引。
 *
 * @author 系统
 * @version 1.0
 */
public interface QuestionnaireCacheService {

    /**
     * 按标识加载问卷模板（内存 → Redis → FHIR 三级降级）
     */
    Optional<QuestionnaireTemplate> findById(String id);

    /**
     * 获取所有可用问卷模板（按注册表优先级排序）
     */
    List<QuestionnaireTemplate> findAll();

    /**
     * 按分类获取问卷模板
     */
    List<QuestionnaireTemplate> findByCategory(String category);

    /**
     * 获取内存中的问卷注册表（用于关键词匹配）
     */
    List<QuestionnaireRegistry> getRegistries();

    /**
     * 全量刷新：重载注册表 + 重新预热模板缓存
     */
    void refresh();
}
