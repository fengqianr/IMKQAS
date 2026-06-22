package com.student.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PdfPreChecker 单元测试
 */
class PdfPreCheckerTest {

    // ===================== 辅助方法 =====================

    /** 创建含英文文本的 PDF，每页文本由 pageTexts 指定 */
    private File createTextPdf(int pages, String[] pageTexts) throws IOException {
        Path tmp = Files.createTempFile("test_", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                if (i < pageTexts.length && pageTexts[i] != null) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        cs.newLineAtOffset(50, 700);
                        cs.showText(pageTexts[i]);
                        cs.endText();
                    }
                }
            }
            doc.save(tmp.toFile());
        }
        File file = tmp.toFile();
        file.deleteOnExit();
        return file;
    }

    /** 创建含图片无文本的 PDF */
    private File createImageOnlyPdf(int pages) throws IOException {
        Path tmp = Files.createTempFile("test_img_", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDImageXObject ximg = LosslessFactory.createFromImage(doc, image);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(ximg, 50, 500, 400, 300);
                }
            }
            doc.save(tmp.toFile());
        }
        File file = tmp.toFile();
        file.deleteOnExit();
        return file;
    }

    // ===================== selectSamplePages =====================

    @Test
    void testSelectSamplePages_SinglePage_shouldReturnPage0() {
        PdfPreChecker checker = new PdfPreChecker();
        assertArrayEquals(new int[]{0}, checker.selectSamplePages(1, false));
    }

    @Test
    void testSelectSamplePages_ShortPdf_shouldReturnFirstThree() {
        PdfPreChecker checker = new PdfPreChecker();
        assertArrayEquals(new int[]{0, 1, 2}, checker.selectSamplePages(8, false));
    }

    @Test
    void testSelectSamplePages_ShortPdf_3Pages_shouldReturnAll() {
        PdfPreChecker checker = new PdfPreChecker();
        assertArrayEquals(new int[]{0, 1, 2}, checker.selectSamplePages(3, false));
    }

    @Test
    void testSelectSamplePages_MediumPdf_shouldReturnCorrectIndices() {
        PdfPreChecker checker = new PdfPreChecker();
        assertArrayEquals(new int[]{0, 2, 4, 9}, checker.selectSamplePages(30, false));
    }

    @Test
    void testSelectSamplePages_LongPdf_shouldReturnCorrectIndices() {
        PdfPreChecker checker = new PdfPreChecker();
        assertArrayEquals(new int[]{0, 4, 9, 19, 69}, checker.selectSamplePages(80, false));
    }

    @Test
    void testSelectSamplePages_LongPdf_100Pages_shouldReturnCorrectIndices() {
        PdfPreChecker checker = new PdfPreChecker();
        assertArrayEquals(new int[]{0, 4, 9, 19, 89}, checker.selectSamplePages(100, false));
    }

    @Test
    void testSelectSamplePages_TocExpansion_shouldReturnExtraPages() {
        PdfPreChecker checker = new PdfPreChecker();
        assertArrayEquals(new int[]{1, 5, 10}, checker.selectSamplePages(50, true));
    }

    // ===================== hasNaturalLanguage =====================

    @Test
    void testHasNaturalLanguage_Chinese_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.hasNaturalLanguage("这是一段中文文本，用于测试自然语言检测。"));
    }

    @Test
    void testHasNaturalLanguage_English_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.hasNaturalLanguage("This is English text for testing natural language detection."));
    }

    @Test
    void testHasNaturalLanguage_MixedChineseEnglish_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.hasNaturalLanguage("Chapter 1 引言 Introduction to Medical AI"));
    }

    @Test
    void testHasNaturalLanguage_PureNumbers_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        assertFalse(checker.hasNaturalLanguage("123 456 789 012 345 678"));
    }

    @Test
    void testHasNaturalLanguage_PureSymbols_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        assertFalse(checker.hasNaturalLanguage("--- === *** /// |||"));
    }

    @Test
    void testHasNaturalLanguage_OnlyTwoEnglishLetters_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        // 2 个英文字母 / 14 个字符 = 14.3% < 30%
        assertFalse(checker.hasNaturalLanguage("ab 123 456 789"));
    }

    @Test
    void testHasNaturalLanguage_EnglishRatioAbove30Percent_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        // 31 个英文字母 / 100 个字符 = 31% > 30%
        String text = "abcde".repeat(6) + "!";  // 30 letters in ~31 chars
        // 构造: 31个字母 + 69个标点/数字 , 总共100字符
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 31; i++) sb.append('a');
        for (int i = 0; i < 69; i++) sb.append('1');
        assertTrue(checker.hasNaturalLanguage(sb.toString()));
    }

    @Test
    void testHasNaturalLanguage_EnglishRatioBelow30Percent_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        // 29 个英文字母 / 100 个字符 = 29% < 30%
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 29; i++) sb.append('a');
        for (int i = 0; i < 71; i++) sb.append('1');
        assertFalse(checker.hasNaturalLanguage(sb.toString()));
    }

    @Test
    void testHasNaturalLanguage_Null_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        assertFalse(checker.hasNaturalLanguage(null));
    }

    @Test
    void testHasNaturalLanguage_Blank_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        assertFalse(checker.hasNaturalLanguage("   \n  \t  "));
    }

    // ===================== isMetadata =====================

    @Test
    void testIsMetadata_FilenameDecoded_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.isMetadata("filename_decoded: some_file_name_here"));
    }

    @Test
    void testIsMetadata_PdgDirName_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.isMetadata("pdg_dir_name: /path/to/directory"));
    }

    @Test
    void testIsMetadata_AnnasArchive_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.isMetadata("Downloaded from annas-archive.org"));
    }

    @Test
    void testIsMetadata_JsonStructure_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        String jsonMeta = "{\"title\": \"test\", \"author\": \"someone\", \"year\": 2020, \"pages\": 100}";
        assertTrue(checker.isMetadata(jsonMeta));
    }

    @Test
    void testIsMetadata_JsonArray_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        String jsonArray = "[{\"id\": 1, \"name\": \"item1\"}, {\"id\": 2, \"name\": \"item2\"}]";
        assertTrue(checker.isMetadata(jsonArray));
    }

    @Test
    void testIsMetadata_NormalProse_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        assertFalse(checker.isMetadata("Medical imaging diagnosis is an indispensable tool in clinical practice."));
    }

    @Test
    void testIsMetadata_Null_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        assertFalse(checker.isMetadata(null));
    }

    // ===================== detectToc =====================

    @Test
    void testDetectToc_ChineseKeyword_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.detectToc("目录\n\n第一章 引言"));
    }

    @Test
    void testDetectToc_EnglishKeywordContents_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.detectToc("Contents\n\n1. Introduction ...... 1"));
    }

    @Test
    void testDetectToc_EnglishKeywordTableOfContents_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker();
        assertTrue(checker.detectToc("Table of Contents\n\nChapter 1 Overview"));
    }

    @Test
    void testDetectToc_NumberedPattern5Lines_shouldReturnTrue() {
        PdfPreChecker checker = new PdfPreChecker(500, 200, 2, 5);
        String toc = """
                1. Introduction    1
                2. Methods    15
                3. Results    30
                4. Discussion    45
                5. Conclusion    60""";
        assertTrue(checker.detectToc(toc));
    }

    @Test
    void testDetectToc_NumberedPatternInsufficientLines_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker(500, 200, 2, 5);
        String toc = """
                1. Introduction    1
                2. Methods    15
                3. Results    30""";
        assertFalse(checker.detectToc(toc));
    }

    @Test
    void testDetectToc_NormalText_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        assertFalse(checker.detectToc("Medical imaging is an indispensable tool in clinical practice."));
    }

    @Test
    void testDetectToc_Null_shouldReturnFalse() {
        PdfPreChecker checker = new PdfPreChecker();
        assertFalse(checker.detectToc(null));
    }

    // ===================== 集成测试：preCheck 分类 =====================

    @Test
    void testPreCheck_DigitalNative_TextRichPdf() throws IOException {
        PdfPreChecker checker = new PdfPreChecker();
        // 5 页英文文本 PDF，第 1、3、5 页有充足内容
        String longText = "Medical imaging diagnosis is an indispensable and critically important "
                + "tool in clinical practice. It plays a vital role in disease detection, "
                + "treatment planning, and therapeutic monitoring. Various modalities such as "
                + "CT, MRI, and ultrasound provide complementary diagnostic information. "
                + "The continuous advancement in imaging technology has revolutionized "
                + "modern medicine and improved patient outcomes significantly.";
        String[] texts = new String[5];
        texts[0] = longText;
        texts[2] = longText;
        texts[4] = longText;

        File pdf = createTextPdf(5, texts);
        PdfPreCheckResult result = checker.preCheck(pdf);
        assertEquals(PdfClassification.DIGITAL_NATIVE, result.getClassification(),
                "文本充足的 PDF 应判定为 DIGITAL_NATIVE: " + result.getReason());
        assertTrue(result.getTotalPages() >= 3);
    }

    @Test
    void testPreCheck_Scanned_ImageOnlyPdf() throws IOException {
        PdfPreChecker checker = new PdfPreChecker();
        File pdf = createImageOnlyPdf(5);
        PdfPreCheckResult result = checker.preCheck(pdf);
        assertEquals(PdfClassification.SCANNED, result.getClassification(),
                "纯图片 PDF 应判定为 SCANNED: " + result.getReason());
        assertTrue(result.isHasImages());
    }

    @Test
    void testPreCheck_Scanned_MetadataJson() throws IOException {
        PdfPreChecker checker = new PdfPreChecker();
        String jsonMeta = "filename_decoded: book_001.pdf pdg_dir_name: /archives/2023";
        String[] texts = {jsonMeta, jsonMeta, jsonMeta};
        File pdf = createTextPdf(5, texts);
        PdfPreCheckResult result = checker.preCheck(pdf);
        assertEquals(PdfClassification.SCANNED, result.getClassification(),
                "元数据 PDF 应判定为 SCANNED: " + result.getReason());
    }

    @Test
    void testPreCheck_Uncertain_ShortText() throws IOException {
        PdfPreChecker checker = new PdfPreChecker();
        // 仅第 1 页有短文本，另外 2 页空白
        String[] texts = new String[3];
        texts[0] = "Short title page only";
        File pdf = createTextPdf(3, texts);
        PdfPreCheckResult result = checker.preCheck(pdf);
        assertEquals(PdfClassification.UNCERTAIN, result.getClassification(),
                "短文本 PDF 应判定为 UNCERTAIN: " + result.getReason());
    }

    @Test
    void testPreCheck_SinglePage() throws IOException {
        PdfPreChecker checker = new PdfPreChecker();
        String[] texts = {"This is a single page PDF with some content for testing purposes."};
        File pdf = createTextPdf(1, texts);
        PdfPreCheckResult result = checker.preCheck(pdf);
        // 单页短文本 → UNCERTAIN
        assertEquals(PdfClassification.UNCERTAIN, result.getClassification(),
                "单页短文本 PDF 应判定为 UNCERTAIN: " + result.getReason());
    }

    @Test
    void testPreCheck_EmptyPdf_shouldReturnUncertain() throws IOException {
        PdfPreChecker checker = new PdfPreChecker();
        Path tmp = Files.createTempFile("test_empty_", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.save(tmp.toFile());
        }
        File pdf = tmp.toFile();
        pdf.deleteOnExit();
        PdfPreCheckResult result = checker.preCheck(pdf);
        assertEquals(PdfClassification.UNCERTAIN, result.getClassification());
        assertEquals("PDF 页数为 0", result.getReason());
    }

    // ===================== 自定义阈值 =====================

    @Test
    void testPreCheck_WithCustomLowThresholds_shouldClassifyDigitalNative() throws IOException {
        PdfPreChecker checker = new PdfPreChecker(100, 50, 1, 5);
        // 单页文本 > 100 字符且有自然语言 + minPagesWithText=1
        String longText = "Medical imaging diagnosis is an indispensable and critically important "
                + "tool in clinical practice. It plays a vital role in disease detection.";
        String[] texts = {longText};
        File pdf = createTextPdf(1, texts);
        PdfPreCheckResult result = checker.preCheck(pdf);
        assertEquals(PdfClassification.DIGITAL_NATIVE, result.getClassification(),
                "自定义低阈值下单页充足文本应判定为 DIGITAL_NATIVE: " + result.getReason());
    }

    @Test
    void testPreCheck_WithCustomHighThresholds_shouldBeUncertain() throws IOException {
        // digitalNativeMinTextLength=2000 极高，scannedMaxTextLength=100 极低
        // 213字符落在中间灰色区间 → UNCERTAIN
        PdfPreChecker checker = new PdfPreChecker(2000, 100, 3, 5);
        String mediumText = "Medical imaging is an important diagnostic tool in clinical practice.";
        String[] texts = {mediumText, mediumText, mediumText};
        File pdf = createTextPdf(5, texts);
        PdfPreCheckResult result = checker.preCheck(pdf);
        assertEquals(PdfClassification.UNCERTAIN, result.getClassification(),
                "高阈值下中等文本应判定为 UNCERTAIN: " + result.getReason());
    }

    @Test
    void testPreCheck_ImageWithShortText_shouldBeScanned() throws IOException {
        PdfPreChecker checker = new PdfPreChecker();
        File pdf = createImageOnlyPdf(3);
        PdfPreCheckResult result = checker.preCheck(pdf);
        assertEquals(PdfClassification.SCANNED, result.getClassification(),
                "有图片无文本应判定为 SCANNED: " + result.getReason());
    }
}
