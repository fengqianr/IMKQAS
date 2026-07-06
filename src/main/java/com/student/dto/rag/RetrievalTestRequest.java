package com.student.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 检索K值调优测试请求DTO
 *
 * @author 系统
 * @version 1.0
 */
@Data
public class RetrievalTestRequest {
    /** 检索通路: VECTOR / KEYWORD / HYBRID */
    @NotBlank(message = "通路类型不能为空")
    private String pathway;

    /** 查询文本 */
    @NotBlank(message = "查询文本不能为空")
    private String query;

    /** 返回结果数量 (2-1000) */
    @Min(1)
    @Max(1000)
    private int topK = 10;

    /** 混合检索每路召回数量 (仅HYBRID通路有效，覆盖配置中的initialTopK) */
    @Min(1)
    @Max(1000)
    private Integer hybridPerSideK;

    /** 向量检索权重 (仅HYBRID通路有效) */
    private Double vectorWeight;

    /** 关键词检索权重 (仅HYBRID通路有效) */
    private Double keywordWeight;
}
