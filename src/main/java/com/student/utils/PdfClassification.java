package com.student.utils;

/**
 * PDF 预检分类结果，决定提取管道路由
 */
public enum PdfClassification {

    /** 数字原生 PDF：文本层清晰可提取，跳过 MinerU 直接用 PDFBox */
    DIGITAL_NATIVE,

    /** 扫描件 PDF：无可用文本层，需走 MinerU 强制 OCR */
    SCANNED,

    /** 无法确定：走原三级降级管道（MinerU → PDFBox → Tesseract） */
    UNCERTAIN
}
