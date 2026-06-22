package com.student.utils;

/**
 * 页面类型分类结果，决定单页采用何种提取策略
 *
 * @author 系统
 * @version 1.0
 */
public enum PageType {

    /** 有文本层：PDFBox 直接提取 */
    HAS_TEXT_LAYER,

    /** 无文本层 + 关键页：OCR 优先保障质量 */
    NO_TEXT_KEY_PAGE,

    /** 无文本层 + 非关键页：跳过以节省成本 */
    NO_TEXT_NON_KEY,

    /** 复杂表格 + 关键页：专用提取器或 OCR */
    COMPLEX_TABLE_KEY,

    /** 复杂表格 + 非关键页：PDFBox 带标注 */
    COMPLEX_TABLE_NON_KEY,

    /** 不确定：保守处理，走 OCR */
    UNCERTAIN
}
