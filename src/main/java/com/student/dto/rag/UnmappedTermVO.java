package com.student.dto.rag;

import java.time.LocalDateTime;

/**
 * 未映射词条前端展示VO
 *
 * @author 系统
 * @version 1.0
 */
public class UnmappedTermVO {

    private Long id;
    private String term;
    private String contextQuery;
    private String guessedEntityType;
    private String llmGuess;
    private Double llmConfidence;
    private Integer occurrenceCount;
    private String status;
    private String reviewer;
    private String reviewNote;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;

    // 审核人友好显示
    private String reviewerName;
    // 实体类型中文显示
    private String entityTypeLabel;

    public UnmappedTermVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getContextQuery() { return contextQuery; }
    public void setContextQuery(String contextQuery) { this.contextQuery = contextQuery; }

    public String getGuessedEntityType() { return guessedEntityType; }
    public void setGuessedEntityType(String guessedEntityType) { this.guessedEntityType = guessedEntityType; }

    public String getLlmGuess() { return llmGuess; }
    public void setLlmGuess(String llmGuess) { this.llmGuess = llmGuess; }

    public Double getLlmConfidence() { return llmConfidence; }
    public void setLlmConfidence(Double llmConfidence) { this.llmConfidence = llmConfidence; }

    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public String getEntityTypeLabel() { return entityTypeLabel; }
    public void setEntityTypeLabel(String entityTypeLabel) { this.entityTypeLabel = entityTypeLabel; }
}
