package com.student.utils;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * PDF 预检结果，包含分类结论和诊断元数据
 */
@Data
@Builder
@AllArgsConstructor
public class PdfPreCheckResult {

    /** 分类结果 */
    private PdfClassification classification;

    /** 判定理由（用于日志诊断） */
    private String reason;

    /** 采样页面拼接文本 */
    private String sampledText;

    /** 实际采样页数 */
    private int sampledPageCount;

    /** PDF 总页数 */
    private int totalPages;

    /** 是否检测到图片对象 */
    private boolean hasImages;

    /** 是否检测到目录结构 */
    private boolean hasToc;

    /** 采样的页码（0-based） */
    private List<Integer> sampledPageIndices;
}
