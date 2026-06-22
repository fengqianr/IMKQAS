package com.student.utils;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

import lombok.extern.slf4j.Slf4j;

/**
 * 页面级分类器：逐页判定 PDF 页面的类型，决定每页采用何种提取策略。
 * 非 Spring Bean，使用时直接 new 即可。
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
public class PageLevelClassifier {

    /** 默认医学关键词集合（用于关键页判定） */
    private static final Set<String> DEFAULT_MEDICAL_KEYWORDS = Set.of(
            "诊断", "结论", "用法用量", "禁忌", "检查结果",
            "临床表现", "治疗", "手术", "药物", "剂量"
    );

    /** 表格关键词 */
    private static final Set<String> TABLE_KEYWORDS = Set.of(
            "表", "Table", "合计", "总计", "小计"
    );

    /** 表格分隔符模式：tab、多个空格、管道符 */
    private static final Pattern TABLE_SEPARATOR_PATTERN =
            Pattern.compile("(\\t|\\|)");

    /** 多空格列分隔（3个及以上连续空格） */
    private static final Pattern MULTI_SPACE_COLUMN = Pattern.compile(" {3,}");

    /** 每页文本层最小长度（低于此值视为无文本层） */
    private static final int DEFAULT_MIN_TEXT_LAYER_LENGTH = 10;

    private final int minTextLayerLength;
    private final Set<String> medicalKeywords;

    public PageLevelClassifier() {
        this(DEFAULT_MIN_TEXT_LAYER_LENGTH, DEFAULT_MEDICAL_KEYWORDS);
    }

    public PageLevelClassifier(int minTextLayerLength, Set<String> medicalKeywords) {
        this.minTextLayerLength = minTextLayerLength;
        this.medicalKeywords = medicalKeywords;
    }

    /**
     * 对单页进行分类
     *
     * @param doc        PDFBox 文档对象
     * @param pageIndex  页码（0-based）
     * @param totalPages PDF 总页数
     * @return 页面分类结果
     */
    public PageLevelResult classify(PDDocument doc, int pageIndex, int totalPages) {
        boolean hasText;
        String pageText;
        try {
            pageText = extractPageText(doc, pageIndex);
            hasText = pageText != null && pageText.trim().length() >= minTextLayerLength;
        } catch (IOException e) {
            log.warn("第 {} 页文本提取异常: {}", pageIndex + 1, e.getMessage());
            return buildUncertain(pageIndex, "文本提取异常: " + e.getMessage());
        }

        boolean hasImages;
        try {
            hasImages = hasImageObjects(doc, pageIndex);
        } catch (IOException e) {
            hasImages = false;
        }

        int textLength = pageText != null ? pageText.trim().length() : 0;
        boolean keyPage = isKeyPage(pageText, pageIndex, totalPages);
        boolean complexTable = hasComplexTable(pageText);

        PageType pageType;
        String reason;

        if (hasText && !complexTable) {
            pageType = PageType.HAS_TEXT_LAYER;
            reason = String.format("有文本层(%d字符)，非复杂表格", textLength);
        } else if (hasText && complexTable && keyPage) {
            pageType = PageType.COMPLEX_TABLE_KEY;
            reason = "复杂表格 + 关键页";
        } else if (hasText && complexTable) {
            pageType = PageType.COMPLEX_TABLE_NON_KEY;
            reason = "复杂表格 + 非关键页";
        } else if (!hasText && keyPage) {
            pageType = PageType.NO_TEXT_KEY_PAGE;
            reason = "无文本层 + 关键页";
        } else if (!hasText) {
            pageType = PageType.NO_TEXT_NON_KEY;
            reason = "无文本层 + 非关键页";
        } else {
            pageType = PageType.UNCERTAIN;
            reason = "无法确定页面类型";
        }

        return PageLevelResult.builder()
                .pageIndex(pageIndex)
                .pageType(pageType)
                .hasTextLayer(hasText)
                .hasImages(hasImages)
                .textLength(textLength)
                .isKeyPage(keyPage)
                .hasComplexTable(complexTable)
                .pageText(pageText)
                .reason(reason)
                .build();
    }

    /**
     * 判断是否为关键页（首页、末页、含医学关键词）
     */
    boolean isKeyPage(String pageText, int pageIndex, int totalPages) {
        if (pageIndex == 0 || pageIndex == totalPages - 1) {
            return true;
        }
        if (pageText == null || pageText.isBlank()) {
            return false;
        }
        for (String keyword : medicalKeywords) {
            if (pageText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测页面是否包含复杂表格（启发式：表格关键词 + 分隔符模式）
     */
    boolean hasComplexTable(String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return false;
        }
        // 检测表格关键词
        boolean hasTableKeyword = false;
        for (String keyword : TABLE_KEYWORDS) {
            if (pageText.contains(keyword)) {
                hasTableKeyword = true;
                break;
            }
        }
        if (!hasTableKeyword) {
            return false;
        }
        // 检测表格分隔符：tab、管道符、多空格列对齐
        long tabPipeCount = TABLE_SEPARATOR_PATTERN.matcher(pageText).results().count();
        long multiSpaceColCount = MULTI_SPACE_COLUMN.matcher(pageText).results().count();
        return (tabPipeCount + multiSpaceColCount) >= 3;
    }

    /**
     * 检查页面是否有有效文本层
     */
    boolean hasTextLayer(PDDocument doc, int pageIndex) {
        try {
            String text = extractPageText(doc, pageIndex);
            return text != null && text.trim().length() >= minTextLayerLength;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 检查页面是否包含图片对象
     */
    boolean hasImageObjects(PDDocument doc, int pageIndex) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        if (page.getResources() == null) {
            return false;
        }
        Iterable<org.apache.pdfbox.cos.COSName> xobjNames = page.getResources().getXObjectNames();
        for (org.apache.pdfbox.cos.COSName name : xobjNames) {
            if (page.getResources().isImageXObject(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取单页文本
     */
    private String extractPageText(PDDocument doc, int pageIndex) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.setSortByPosition(true);
        return stripper.getText(doc);
    }

    private PageLevelResult buildUncertain(int pageIndex, String reason) {
        return PageLevelResult.builder()
                .pageIndex(pageIndex)
                .pageType(PageType.UNCERTAIN)
                .hasTextLayer(false)
                .hasImages(false)
                .textLength(0)
                .isKeyPage(false)
                .hasComplexTable(false)
                .pageText("")
                .reason(reason)
                .build();
    }
}
