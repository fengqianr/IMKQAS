package com.imkqas.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 未映射词条审核队列 — 手动ACK + 死信队列 + 指数退避重试
 * 仅在 mq-enabled=true 时激活
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@ConditionalOnProperty(name = "imkqas.query-rewrite.synonym-expansion.mq-enabled", havingValue = "true")
public class RabbitMQConfig {

    // ========== 队列和交换机名称 ==========

    public static final String EXCHANGE_NAME = "unmapped.term.exchange";
    public static final String QUEUE_NAME = "unmapped.term.review.queue";
    public static final String DLQ_NAME = "unmapped.term.review.dlq";
    public static final String DLX_EXCHANGE_NAME = "unmapped.term.dlx.exchange";
    public static final String ROUTING_KEY = "unmapped.term.review";

    // ========== 时间配置（毫秒） ==========

    /** 首次重试延迟 */
    public static final long RETRY_DELAY_1 = 2000L;
    /** 第二次重试延迟 */
    public static final long RETRY_DELAY_2 = 4000L;
    /** 第三次重试延迟 */
    public static final long RETRY_DELAY_3 = 8000L;
    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    // ========== 死信交换机 ==========

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE_NAME);
    }

    // ========== 死信队列 ==========

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(dlxExchange())
                .with(ROUTING_KEY);
    }

    // ========== 业务交换机 ==========

    @Bean
    public DirectExchange unmappedTermExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    // ========== 业务队列（绑定死信交换机） ==========

    @Bean
    public Queue unmappedTermReviewQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding reviewQueueBinding() {
        return BindingBuilder.bind(unmappedTermReviewQueue())
                .to(unmappedTermExchange())
                .with(ROUTING_KEY);
    }

    // ========== 消息转换器 ==========

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
