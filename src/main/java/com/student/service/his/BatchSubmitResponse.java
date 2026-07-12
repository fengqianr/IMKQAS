package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量提交响应 —— 返回完成摘要供前端渲染完成卡片
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSubmitResponse {
    private boolean completed;
    private String message;
    private int totalScore;
    private int maxScore;
    private String severity;
    private String interpretation;
    private String analysisSummary;
    private String analysisId;
}
