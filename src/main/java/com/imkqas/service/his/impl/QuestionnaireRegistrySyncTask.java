package com.imkqas.service.his.impl;

import com.imkqas.service.his.QuestionnaireCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 问卷注册表定时同步任务
 * 每日凌晨全量同步：重载 MySQL 注册表 + 重新预热问卷模板缓存。
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionnaireRegistrySyncTask {

    private final QuestionnaireCacheService cacheService;

    /**
     * 每日凌晨全量同步（cron 可通过 imkqas.his.sync-cron 覆盖）
     */
    @Scheduled(cron = "${imkqas.his.sync-cron:0 0 2 * * ?}")
    public void syncDaily() {
        log.info("开始每日问卷注册表/模板同步...");
        try {
            cacheService.refresh();
            log.info("每日问卷同步完成");
        } catch (Exception e) {
            log.error("每日问卷同步失败", e);
        }
    }
}
