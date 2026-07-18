package com.imkqas.service.rag;

import com.imkqas.config.RabbitMQConfig;
import com.imkqas.dto.rag.UnmappedTermMessage;
import com.imkqas.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 未映射词条消息生产者
 * 将未映射词条异步发送到RabbitMQ审核队列
 * 通过Redis SETNX实现同日同词条去重，保证幂等性
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnmappedTermProducer {

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    private final RedisService redisService;

    /** Redis key前缀 */
    private static final String IDEMPOTENT_KEY_PREFIX = "term:queue:";

    /** 去重有效期（24小时，同日同词条只入队一次） */
    private static final long IDEMPOTENT_TTL_SECONDS = 86400L;

    /** 是否启用MQ发送（可通过配置关闭） */
    @Value("${imkqas.query-rewrite.synonym-expansion.mq-enabled:false}")
    private boolean mqEnabled;

    /**
     * 发送未映射词条到审核队列
     * 先通过Redis去重，再发送消息
     *
     * @param message 消息体
     * @return 是否发送成功（含去重跳过）
     */
    public boolean sendToReviewQueue(UnmappedTermMessage message) {
        if (!mqEnabled) {
            log.debug("MQ发送已禁用，跳过: term={}", message.getTerm());
            return false;
        }

        if (message == null || message.getTerm() == null) {
            return false;
        }

        try {
            // 幂等性保证：同日同词条只入队一次
            String idempotentKey = buildIdempotentKey(message.getTerm());
            boolean isNew = redisService.set(idempotentKey, message.getMessageId(), IDEMPOTENT_TTL_SECONDS);
            if (!isNew) {
                log.debug("词条24h内已入队，跳过: term={}", message.getTerm());
                return false;
            }

            // 发送到RabbitMQ（RabbitTemplate可能为null，当MQ未启用时）
            if (rabbitTemplate == null) {
                log.debug("RabbitTemplate不可用，跳过发送: term={}", message.getTerm());
                return false;
            }
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ROUTING_KEY,
                    message
            );

            log.info("未映射词条已发送到审核队列: term={}, messageId={}",
                    message.getTerm(), message.getMessageId());
            return true;

        } catch (Exception e) {
            log.warn("发送未映射词条到MQ失败: term={}, error={}",
                    message.getTerm(), e.getMessage());
            return false;
        }
    }

    /**
     * 构建幂等键：前缀 + term的MD5
     * 同一天相同term只发送一次
     */
    private String buildIdempotentKey(String term) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(term.getBytes(StandardCharsets.UTF_8));
            return IDEMPOTENT_KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return IDEMPOTENT_KEY_PREFIX + term;
        }
    }
}
