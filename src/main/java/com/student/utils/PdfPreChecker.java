package com.student.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import lombok.extern.slf4j.Slf4j;

/**
 * PDF 预检工具：在调用 MinerU 前用 PDFBox 快速采样少量页面，
 * 根据文本量、内容特征、图片检测等综合判断 PDF 类型，
 * 让数字原生 PDF 跳过 MinerU 直接走 PDFBox 提取。
 *
 * <p>非 Spring Bean，使用时直接 new 即可（与 PdfToMarkdownConverter 一致）。</p>
 */
@Slf4j
public class PdfPreChecker {

    // 中文 CJK 统一表意文字范围
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff\\u3400-\\u4dbf]");
    // 英文字母
    private static final Pattern ENGLISH_LETTER_PATTERN = Pattern.compile("[a-zA-Z]");
    // JSON 元数据特征字符（冒号、引号、括号、逗号）
    private static final Pattern JSON_META_CHARS = Pattern.compile("[{}\\[\\]:\",]");
    // 目录行模式：数字 + 可选点号 + 标题文本 + 页码
    private static final Pattern TOC_LINE_PATTERN =
            Pattern.compile("^\\s*\\d+\\.?\\s+.+\\s+\\d{1,6}\\s*$", Pattern.MULTILINE);
    // 目录页关键词
    private static final Pattern TOC_KEYWORD_PATTERN = Pattern.compile("目录|Contents|Table of Contents",
            Pattern.CASE_INSENSITIVE);
    // 每页最小文本长度（低于此值视为无文本页）
    private static final int MIN_PAGE_TEXT_LENGTH = 5;

    private final int digitalNativeMinTextLength;
    private final int scannedMaxTextLength;
    private final int minPagesWithText;
    private final int tocPatternRepeatThreshold;

    public PdfPreChecker() {
        this(500, 200, 2, 5);
    }

    public PdfPreChecker(int digitalNativeMinTextLength, int scannedMaxTextLength) {
        this(digitalNativeMinTextLength, scannedMaxTextLength, 2, 5);
    }

    public PdfPreChecker(int digitalNativeMinTextLength, int scannedMaxTextLength,
                         int minPagesWithText, int tocPatternRepeatThreshold) {
        this.digitalNativeMinTextLength = digitalNativeMinTextLength;
        this.scannedMaxTextLength = scannedMaxTextLength;
        this.minPagesWithText = minPagesWithText;
        this.tocPatternRepeatThreshold = tocPatternRepeatThreshold;
    }

    /**
     * 执行 PDF 预检，返回分类结果和诊断信息。
     *
     * @param pdfFile PDF 文件
     * @return 预检结果
     * @throws IOException 非致命 IO 异常已在内部降级为 UNCERTAIN，仅致命异常抛出
     */
    public PdfPreCheckResult preCheck(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                return buildResult(PdfClassification.UNCERTAIN, "PDF 页数为 0", "",
                        0, totalPages, false, false, List.of());
            }

            int[] initialPages = selectSamplePages(totalPages, false);
            List<Integer> sampledIndices = new ArrayList<>();
            StringBuilder allText = new StringBuilder();
            int pagesWithText = 0;
            boolean hasImages = false;

            // 第一步：采样初始页面
            for (int pageIdx : initialPages) {
                if (pageIdx >= totalPages) continue;
                sampledIndices.add(pageIdx);
                try {
                    String pageText = extractPageText(document, pageIdx);
                    allText.append(pageText).append('\n');
                    if (pageHasText(pageText)) {
                        pagesWithText++;
                    }
                    if (!hasImages && hasImageObjects(document, pageIdx)) {
                        hasImages = true;
                    }
                } catch (IOException e) {
                    log.warn("PDF 预检: 第 {} 页采样异常: {}", pageIdx + 1, e.getMessage());
                }
            }

            String sampledText = allText.toString();
            boolean hasToc = detectToc(sampledText);

            // 第二步：检测到目录时，扩展采样确认正文
            if (hasToc && totalPages > 10) {
                int[] expansionPages = selectSamplePages(totalPages, true);
                for (int pageIdx : expansionPages) {
                    if (sampledIndices.contains(pageIdx) || pageIdx >= totalPages) continue;
                    sampledIndices.add(pageIdx);
                    try {
                        String pageText = extractPageText(document, pageIdx);
                        sampledText = sampledText + "\n" + pageText;
                        if (pageHasText(pageText)) {
                            pagesWithText++;
                        }
                        if (!hasImages && hasImageObjects(document, pageIdx)) {
                            hasImages = true;
                        }
                    } catch (IOException e) {
                        log.warn("PDF 预检: 第 {} 页(扩展)采样异常: {}", pageIdx + 1, e.getMessage());
                    }
                }
            }

            // 第三步：应用决策规则
            PdfClassification classification = classify(sampledText, pagesWithText, hasImages,
                    hasToc, totalPages);

            return buildResult(classification, buildReason(classification, sampledText,
                            pagesWithText, hasImages, hasToc),
                    sampledText, sampledIndices.size(), totalPages, hasImages, hasToc,
                    sampledIndices);

        } catch (IOException e) {
            // 加密 PDF 等无法加载的情况
            if (e.getMessage() != null && e.getMessage().contains("encrypt")) {
                log.warn("PDF 预检: 文件已加密，回退到 UNCERTAIN");
                return buildResult(PdfClassification.UNCERTAIN, "PDF 已加密",
                        "", 0, 0, false, false, List.of());
            }
            throw e;
        }
    }

    /**
     * 应用决策规则进行分类。
     */
    private PdfClassification classify(String sampledText, int pagesWithText,
                                       boolean hasImages, boolean hasToc, int totalPages) {
        int textLength = sampledText.trim().length();

        // --- 单页/仅一页有文本：保守处理为 UNCERTAIN ---
        // 单页 PDF 有文本：可能是正文也可能是封面，无法交叉验证
        if (totalPages == 1 && pagesWithText == 1) {
            if (textLength > digitalNativeMinTextLength && !isMetadata(sampledText)
                    && hasNaturalLanguage(sampledText)) {
                return PdfClassification.DIGITAL_NATIVE;
            }
            return PdfClassification.UNCERTAIN;
        }
        // 多页文档只有一页有文本：可能是目录页或单页文档，保守处理
        if (totalPages > 1 && pagesWithText == 1) {
            if (isMetadata(sampledText)) {
                return PdfClassification.SCANNED;
            }
            return PdfClassification.UNCERTAIN;
        }

        // 放宽有文本页数要求（总页数少于阈值时）
        int effectiveMinPages = Math.min(minPagesWithText, totalPages);

        // --- SCANNED 判断 ---
        // 所有采样页无文本层
        if (pagesWithText == 0) {
            return PdfClassification.SCANNED;
        }
        // 文本过短且无图片
        if (textLength < scannedMaxTextLength && !hasImages) {
            return PdfClassification.SCANNED;
        }
        // JSON 元数据
        if (isMetadata(sampledText)) {
            return PdfClassification.SCANNED;
        }
        // 有图片但几乎无文本
        if (hasImages && textLength < scannedMaxTextLength && pagesWithText < effectiveMinPages) {
            return PdfClassification.SCANNED;
        }

        // --- DIGITAL_NATIVE 判断（必须全部满足）---
        boolean enoughPagesWithText = pagesWithText >= effectiveMinPages;
        boolean textLengthOk = textLength > digitalNativeMinTextLength;
        boolean notMetadata = !isMetadata(sampledText);
        boolean hasLang = hasNaturalLanguage(sampledText);

        if (enoughPagesWithText && textLengthOk && notMetadata && hasLang) {
            return PdfClassification.DIGITAL_NATIVE;
        }

        // --- 其余为 UNCERTAIN ---
        return PdfClassification.UNCERTAIN;
    }

    /**
     * 自适应采样：根据总页数决定采样哪些页面。
     *
     * @param totalPages   总页数
     * @param expandForToc 是否为目录扩展模式
     * @return 采样页码（0-based）
     */
    int[] selectSamplePages(int totalPages, boolean expandForToc) {
        if (expandForToc) {
            // TOC 扩展：额外采样第 2、6、11 页确认正文
            return new int[]{1, 5, 10};
        }

        if (totalPages <= 1) {
            return new int[]{0};
        }
        if (totalPages <= 10) {
            // 短文档：第 1、2、3 页
            int end = Math.min(3, totalPages);
            int[] pages = new int[end];
            for (int i = 0; i < end; i++) pages[i] = i;
            return pages;
        }
        if (totalPages <= 50) {
            // 中档文档：第 1、3、5、10 页
            return new int[]{0, 2, 4, 9};
        }
        // 长文档：第 1、5、10、20 页 + 倒数第 10 页
        return new int[]{0, 4, 9, 19, Math.max(0, totalPages - 11)};
    }

    /**
     * 提取单页文本。
     */
    private String extractPageText(PDDocument doc, int pageIndex) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        // PDFBox 页码从 1 开始
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.setSortByPosition(true);
        return stripper.getText(doc);
    }

    /**
     * 检查页面是否有有效文本内容。
     */
    private boolean pageHasText(String pageText) {
        return pageText != null && pageText.trim().length() >= MIN_PAGE_TEXT_LENGTH;
    }

    /**
     * 检查文本是否包含自然语言（中文或英文）。
     */
    boolean hasNaturalLanguage(String text) {
        if (text == null || text.isBlank()) return false;
        if (CHINESE_PATTERN.matcher(text).find()) return true;
        long totalChars = text.codePoints().count();
        if (totalChars == 0) return false;
        long englishLetters = ENGLISH_LETTER_PATTERN.matcher(text).results().count();
        return (double) englishLetters / totalChars > 0.3;
    }

    /**
     * 检查文本是否为馆藏元数据（关键词或 JSON 结构）。
     */
    boolean isMetadata(String text) {
        if (text == null || text.isBlank()) return false;

        // 已知元数据关键词
        String lower = text.toLowerCase();
        if (lower.contains("filename_decoded")
                || lower.contains("pdg_dir_name")
                || lower.contains("annas-archive")) {
            return true;
        }

        // JSON 结构检测：{}, [], :, ", 逗号占比超过 15%
        String stripped = text.replaceAll("\\s+", "");
        if (stripped.length() == 0) return false;
        long metaCharCount = JSON_META_CHARS.matcher(stripped).results().count();
        double ratio = (double) metaCharCount / stripped.length();
        return ratio > 0.15 && (stripped.contains("{") || stripped.contains("["));
    }

    /**
     * 检测文本是否包含目录结构特征。
     */
    boolean detectToc(String text) {
        if (text == null || text.isBlank()) return false;

        // 包含目录关键词
        if (TOC_KEYWORD_PATTERN.matcher(text).find()) {
            return true;
        }

        // 目录行模式出现次数达到阈值
        long tocLineCount = TOC_LINE_PATTERN.matcher(text).results().count();
        return tocLineCount >= tocPatternRepeatThreshold;
    }

    /**
     * 检查指定页面是否包含图片对象。
     */
    boolean hasImageObjects(PDDocument doc, int pageIndex) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        if (page.getResources() == null) return false;
        Iterable<org.apache.pdfbox.cos.COSName> xobjNames = page.getResources().getXObjectNames();
        for (org.apache.pdfbox.cos.COSName name : xobjNames) {
            if (page.getResources().isImageXObject(name)) {
                return true;
            }
        }
        return false;
    }

    private String buildReason(PdfClassification c, String text, int pagesWithText,
                               boolean hasImages, boolean hasToc) {
        return switch (c) {
            case DIGITAL_NATIVE -> String.format(
                    "采样文本充足(%d字符/%d页有文本)，自然语言确认，非元数据",
                    text.trim().length(), pagesWithText);
            case SCANNED -> {
                if (pagesWithText == 0) yield "所有采样页均无文本层";
                if (isMetadata(text)) yield "文本为馆藏元数据(JSON结构)";
                if (hasImages && text.trim().length() < scannedMaxTextLength)
                    yield String.format("图片对象+文本不足(%d字符)", text.trim().length());
                yield String.format("文本过短(%d字符)且无图片", text.trim().length());
            }
            case UNCERTAIN -> {
                if (hasToc && text.trim().length() < digitalNativeMinTextLength)
                    yield "检测到目录但正文文本不足";
                if (pagesWithText < Math.min(minPagesWithText, 999))
                    yield String.format("仅%d页有文本", pagesWithText);
                yield String.format("文本长度%d在灰色区间(200-500)", text.trim().length());
            }
        };
    }

    private PdfPreCheckResult buildResult(PdfClassification classification, String reason,
                                          String sampledText, int sampledPageCount,
                                          int totalPages, boolean hasImages,
                                          boolean hasToc, List<Integer> indices) {
        return PdfPreCheckResult.builder()
                .classification(classification)
                .reason(reason)
                .sampledText(sampledText)
                .sampledPageCount(sampledPageCount)
                .totalPages(totalPages)
                .hasImages(hasImages)
                .hasToc(hasToc)
                .sampledPageIndices(indices)
                .build();
    }
}
