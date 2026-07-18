package com.imkqas.service.his.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.imkqas.entity.his.InterviewSessionEntity;
import com.imkqas.mapper.his.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 访谈会话清理定时任务
 * 扫描 PAUSED 超时会话 → 标记为 EXPIRED
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewSessionCleanupTask {

    private final InterviewSessionMapper sessionMapper;

    /**
     * 每5分钟扫描一次：PAUSED 超过30分钟 → EXPIRED
     */
    @Scheduled(fixedDelay = 300_000)
    public void expirePausedSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        var update = new LambdaUpdateWrapper<InterviewSessionEntity>()
                .eq(InterviewSessionEntity::getStatus, "PAUSED")
                .le(InterviewSessionEntity::getLastActiveAt, threshold)
                .set(InterviewSessionEntity::getStatus, "EXPIRED");
        try {
            int count = sessionMapper.update(null, update);
            if (count > 0) {
                log.info("过期PAUSED会话: {} 条", count);
            }
        } catch (Exception e) {
            log.warn("清理过期会话失败", e);
        }
    }
}
