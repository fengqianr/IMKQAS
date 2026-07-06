package com.student.dto.rag;

/**
 * 审核请求DTO
 *
 * @author 系统
 * @version 1.0
 */
public class ApproveRequest {

    /** 未映射词条ID */
    private Long id;

    /** 标准术语 */
    private String standardTerm;

    /** 实体类型（可选，覆盖LLM猜测） */
    private String entityType;

    /** SNOMED CT概念ID（可选） */
    private String snomedConceptId;

    public ApproveRequest() {}

    public ApproveRequest(Long id, String standardTerm) {
        this.id = id;
        this.standardTerm = standardTerm;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStandardTerm() { return standardTerm; }
    public void setStandardTerm(String standardTerm) { this.standardTerm = standardTerm; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getSnomedConceptId() { return snomedConceptId; }
    public void setSnomedConceptId(String snomedConceptId) { this.snomedConceptId = snomedConceptId; }
}
