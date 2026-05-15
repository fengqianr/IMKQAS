package com.student.service.snomed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SNOMED CT 术语查询响应DTO
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnomedTermResponse {

    /**
     * SNOMED CT 概念ID
     */
    private String conceptId;

    /**
     * 术语名称（首选）
     */
    private String term;

    /**
     * 术语类型（FSN/SYNONYM/ABBREVIATION）
     */
    private String termType;

    /**
     * 语义标签
     */
    private String semanticTag;

    /**
     * 同义词列表
     */
    private List<String> synonyms;

    /**
     * 概念状态（是否活跃）
     */
    private boolean active;

    /**
     * 定义状态
     */
    private String definitionStatus;

    /**
     * 原始FHIR资源类型
     */
    private String resourceType;

    /**
     * 是否为降级结果（本地映射）
     */
    private boolean fallback;
}