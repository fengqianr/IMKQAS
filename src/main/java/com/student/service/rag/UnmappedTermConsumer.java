package com.student.service.rag;

import com.rabbitmq.client.Channel;
import com.student.config.RabbitMQConfig;
import com.student.dto.rag.UnmappedTermMessage;
import com.student.entity.synonym.UnmappedTermRecord;
import com.student.mapper.UnmappedTermRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 未映射词条消息消费者
 * 手动ACK + 3次指数退避重试 + 死信队列兜底
 * 消费成功后将词条写入数据库审核队列
 * 仅在 mq-enabled=true 时激活
 *
 * @author 系统
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "imkqas.query-rewrite.synonym-expansion.mq-enabled", havingValue = "true")
public class UnmappedTermConsumer {

    private final UnmappedTermRecordMapper unmappedTermMapper;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 监听未映射词条审核队列
     * 手动ACK模式 — 只有成功写入数据库后才确认
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME, ackMode = "MANUAL")
    public void consume(UnmappedTermMessage message, Channel channel, Message amqpMessage) {
        if (message == null || message.getTerm() == null) {
            log.warn("收到空消息，直接ACK");
            safeAck(channel, amqpMessage.getMessageProperties().getDeliveryTag());
            return;
        }

        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        int retryCount = message.getRetryCount();

        log.info("收到未映射词条审核消息: term={}, messageId={}, retry={}/{}",
                message.getTerm(), message.getMessageId(), retryCount, RabbitMQConfig.MAX_RETRY_COUNT);

        try {
            // 写入数据库审核队列
            persistUnmappedTerm(message);

            // 成功 → 手动ACK
            safeAck(channel, deliveryTag);
            log.info("未映射词条审核消息处理成功: term={}, messageId={}",
                    message.getTerm(), message.getMessageId());

        } catch (Exception e) {
            log.warn("处理未映射词条消息失败: term={}, retry={}/{}, error={}",
                    message.getTerm(), retryCount, RabbitMQConfig.MAX_RETRY_COUNT, e.getMessage());

            // 判断是否需要重试
            if (retryCount < RabbitMQConfig.MAX_RETRY_COUNT) {
                retryWithBackoff(message, retryCount);
                // 当前消息ACK（不重新入队，我们用重发方式）
                safeAck(channel, deliveryTag);
            } else {
                // 重试耗尽 → NACK不重新入队 → 进入死信队列
                log.error("未映射词条重试耗尽，进入死信队列: term={}, messageId={}",
                        message.getTerm(), message.getMessageId());
                safeNack(channel, deliveryTag, false);
            }
        }
    }

    /**
     * 将消息持久化到数据库审核队列
     */
    private void persistUnmappedTerm(UnmappedTermMessage message) {
        // 检查是否已存在（幂等性）
        UnmappedTermRecord existing = unmappedTermMapper.findPendingByTerm(message.getTerm());
        if (existing != null) {
            unmappedTermMapper.incrementOccurrence(existing.getId());
            log.debug("词条已存在，增加出现次数: term={}, count={}",
                    message.getTerm(), (existing.getOccurrenceCount() != null ? existing.getOccurrenceCount() + 1 : 1));
            return;
        }

        UnmappedTermRecord record = new UnmappedTermRecord();
        record.setTerm(message.getTerm());
        record.setContextQuery(message.getContextQuery());
        record.setGuessedEntityType(message.getEntityType());
        record.setLlmGuess(message.getLlmGuess());
        record.setLlmConfidence(message.getLlmConfidence());
        record.setOccurrenceCount(1);
        record.setStatus("PENDING");
        record.setFirstSeenAt(LocalDateTime.now());
        record.setLastSeenAt(LocalDateTime.now());
        unmappedTermMapper.insert(record);

        log.info("未映射词条已写入数据库审核队列: term={}", message.getTerm());
    }

    /**
     * 指数退避重试
     * 延迟时间：2s → 4s → 8s
     */
    private void retryWithBackoff(UnmappedTermMessage message, int currentRetry) {
        long delay;
        switch (currentRetry) {
            case 0: delay = RabbitMQConfig.RETRY_DELAY_1; break;
            case 1: delay = RabbitMQConfig.RETRY_DELAY_2; break;
            default: delay = RabbitMQConfig.RETRY_DELAY_3; break;
        }

        message.setRetryCount(currentRetry + 1);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        // 重新发送消息（携带重试次数）
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        log.debug("未映射词条消息已重新入队: term={}, retry={}, delay={}ms",
                message.getTerm(), message.getRetryCount(), delay);
    }

    /**
     * 安全ACK（不抛异常）
     */
    private void safeAck(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("ACK失败: deliveryTag={}", deliveryTag, e);
        }
    }

    /**
     * 安全NACK（不抛异常）
     */
    private void safeNack(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (IOException e) {
            log.error("NACK失败: deliveryTag={}", deliveryTag, e);
        }
    }
}
