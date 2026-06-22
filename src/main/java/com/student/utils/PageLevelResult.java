package com.student.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 单页级别分类结果，包含页面元数据和分类信息
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@AllArgsConstructor
public class PageLevelResult {

    /** 页码（0-based） */
    private int pageIndex;

    /** 页面分类 */
    private PageType pageType;

    /** 是否有文本层 */
    private boolean hasTextLayer;

    /** 是否包含图片对象 */
    private boolean hasImages;

    /** 页面文本长度 */
    private int textLength;

    /** 是否为关键页（首页/末页/含医学关键词） */
    private boolean isKeyPage;

    /** 是否包含复杂表格 */
    private boolean hasComplexTable;

    /** 预提取的页面文本（含文本层时有效） */
    private String pageText;

    /** 判定理由 */
    private String reason;
}
