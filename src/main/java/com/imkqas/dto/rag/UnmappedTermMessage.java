package com.imkqas.dto.rag;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 未映射词条RabbitMQ消息体
 * 通过异步队列发送到审核系统
 *
 * @author 系统
 * @version 1.0
 */
public class UnmappedTermMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一ID（幂等性保证） */
    private String messageId;

    /** 未映射的口语表达 */
    private String term;

    /** 上下文（原始查询） */
    private String contextQuery;

    /** 推测的实体类型 */
    private String entityType;

    /** LLM临时推断结果 */
    private String llmGuess;

    /** LLM推断置信度 */
    private Double llmConfidence;

    /** 消息创建时间 */
    private LocalDateTime timestamp;

    /** 重试次数（消费端使用） */
    private int retryCount;

    public UnmappedTermMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.retryCount = 0;
    }

    public UnmappedTermMessage(String term, String contextQuery, String entityType,
                               String llmGuess, Double llmConfidence) {
        this();
        this.term = term;
        this.contextQuery = contextQuery;
        this.entityType = entityType;
        this.llmGuess = llmGuess;
        this.llmConfidence = llmConfidence;
    }

    // ========== Getters & Setters ==========

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getContextQuery() { return contextQuery; }
    public void setContextQuery(String contextQuery) { this.contextQuery = contextQuery; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getLlmGuess() { return llmGuess; }
    public void setLlmGuess(String llmGuess) { this.llmGuess = llmGuess; }

    public Double getLlmConfidence() { return llmConfidence; }
    public void setLlmConfidence(Double llmConfidence) { this.llmConfidence = llmConfidence; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    @Override
    public String toString() {
        return "UnmappedTermMessage{messageId='" + messageId + "', term='" + term + "'}";
    }
}
