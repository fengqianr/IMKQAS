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
 * PageLevelClassifier 单元测试
 */
class PageLevelClassifierTest {

    /** 创建含文本的 PDF，每页文本由 pageTexts 指定 */
    private File createTextPdf(int pages, String[] pageTexts) throws IOException {
        Path tmp = Files.createTempFile("test_plc_", ".pdf");
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

    /** 创建含多行文本的 PDF（每页文本可含换行符） */
    private File createMultiLineTextPdf(int pages, String[] pageTexts) throws IOException {
        Path tmp = Files.createTempFile("test_plc_ml_", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                if (i < pageTexts.length && pageTexts[i] != null) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        cs.newLineAtOffset(50, 700);
                        String[] lines = pageTexts[i].split("\n", -1);
                        for (int j = 0; j < lines.length; j++) {
                            cs.showText(lines[j]);
                            if (j < lines.length - 1) {
                                cs.newLine();
                            }
                        }
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
        Path tmp = Files.createTempFile("test_plc_img_", ".pdf");
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

    // ===================== isKeyPage =====================

    @Test
    void testIsKeyPage_FirstPage_ShouldReturnTrue() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertTrue(classifier.isKeyPage("Some content", 0, 10));
    }

    @Test
    void testIsKeyPage_LastPage_ShouldReturnTrue() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertTrue(classifier.isKeyPage("Some content", 9, 10));
    }

    @Test
    void testIsKeyPage_MiddlePageNoKeyword_ShouldReturnFalse() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertFalse(classifier.isKeyPage("普通文本内容", 3, 10));
    }

    @Test
    void testIsKeyPage_MedicalKeywordDiagnosis_ShouldReturnTrue() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertTrue(classifier.isKeyPage("根据临床诊断结果显示", 3, 10));
    }

    @Test
    void testIsKeyPage_MedicalKeywordTreatment_ShouldReturnTrue() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertTrue(classifier.isKeyPage("治疗方案包括药物和手术", 3, 10));
    }

    @Test
    void testIsKeyPage_MedicalKeywordDosage_ShouldReturnTrue() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertTrue(classifier.isKeyPage("推荐剂量为每次10mg", 5, 20));
    }

    @Test
    void testIsKeyPage_NullText_ShouldReturnFalse() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertFalse(classifier.isKeyPage(null, 3, 10));
    }

    // ===================== hasComplexTable =====================

    @Test
    void testHasComplexTable_WithMarkdownTable_ShouldReturnTrue() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        String tableText = "表1 患者基本信息\n| 姓名 | 年龄 | 诊断 |\n| 张三 | 45 | 高血压 |\n| 李四 | 52 | 糖尿病 |";
        assertTrue(classifier.hasComplexTable(tableText));
    }

    @Test
    void testHasComplexTable_PlainText_ShouldReturnFalse() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertFalse(classifier.hasComplexTable("这是一段普通文本，包含一些医学信息。"));
    }

    @Test
    void testHasComplexTable_TabSeparated_ShouldReturnTrue() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        String tableText = "Table 实验室检查结果\nWBC\tRBC\tHGB\tPLT\n5.2\t4.8\t140\t250";
        assertTrue(classifier.hasComplexTable(tableText));
    }

    @Test
    void testHasComplexTable_KeywordOnlyNoSeparator_ShouldReturnFalse() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertFalse(classifier.hasComplexTable("合计金额为100元"));
    }

    @Test
    void testHasComplexTable_NullText_ShouldReturnFalse() {
        PageLevelClassifier classifier = new PageLevelClassifier();
        assertFalse(classifier.hasComplexTable(null));
    }

    // ===================== classify 集成测试 =====================

    @Test
    void testClassify_TextRichFirstPage_ShouldBeHasTextLayer() throws IOException {
        PageLevelClassifier classifier = new PageLevelClassifier();
        String longText = "Medical imaging diagnosis is an indispensable and critically important "
                + "tool in clinical practice. It plays a vital role in disease detection, "
                + "treatment planning, and therapeutic monitoring. Various modalities such as "
                + "CT, MRI, and ultrasound provide complementary diagnostic information. "
                + "The continuous advancement in imaging technology has revolutionized "
                + "modern medicine and improved patient outcomes significantly.";
        String[] texts = {longText};
        File pdf = createTextPdf(1, texts);
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            PageLevelResult result = classifier.classify(doc, 0, 1);
            assertEquals(PageType.HAS_TEXT_LAYER, result.getPageType(),
                    "首页文本充足且非表格，应为 HAS_TEXT_LAYER: " + result.getReason());
            assertTrue(result.isKeyPage());
            assertTrue(result.isHasTextLayer());
        }
    }

    @Test
    void testClassify_ImageOnlyKeyPage_ShouldBeNoTextKeyPage() throws IOException {
        PageLevelClassifier classifier = new PageLevelClassifier();
        File pdf = createImageOnlyPdf(3);
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            // 第 0 页（首页）: 纯图片 + 关键页
            PageLevelResult result = classifier.classify(doc, 0, 3);
            assertEquals(PageType.NO_TEXT_KEY_PAGE, result.getPageType(),
                    "首页纯图片应判定为 NO_TEXT_KEY_PAGE: " + result.getReason());
            assertTrue(result.isKeyPage());
            assertFalse(result.isHasTextLayer());
        }
    }

    @Test
    void testClassify_ImageOnlyNonKey_ShouldBeNoTextNonKey() throws IOException {
        PageLevelClassifier classifier = new PageLevelClassifier();
        File pdf = createImageOnlyPdf(5);
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            // 第 2 页（中间页）: 纯图片 + 非关键页
            PageLevelResult result = classifier.classify(doc, 2, 5);
            assertEquals(PageType.NO_TEXT_NON_KEY, result.getPageType(),
                    "中间页纯图片应判定为 NO_TEXT_NON_KEY: " + result.getReason());
            assertFalse(result.isKeyPage());
            assertFalse(result.isHasTextLayer());
        }
    }

    @Test
    void testClassify_ComplexTableKeyPage_ShouldBeComplexTableKey() throws IOException {
        PageLevelClassifier classifier = new PageLevelClassifier();
        // 单行文本含"Table"关键词和多个管道符，模拟表格内容
        String tableText = "Table 2 Results |col1|col2|col3|col4|col5|col6|";
        String[] texts = {tableText};
        File pdf = createTextPdf(1, texts);
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            // 第 0 页: 含表格 + 关键页（首页）
            PageLevelResult result = classifier.classify(doc, 0, 1);
            assertEquals(PageType.COMPLEX_TABLE_KEY, result.getPageType(),
                    "首页含复杂表格应判定为 COMPLEX_TABLE_KEY: " + result.getReason());
            assertTrue(result.isKeyPage());
            assertTrue(result.isHasComplexTable());
        }
    }

    @Test
    void testClassify_ComplexTableNonKey_ShouldBeComplexTableNonKey() throws IOException {
        PageLevelClassifier classifier = new PageLevelClassifier();
        // 单行文本含"Table"关键词和多个管道符
        String tableText = "Table Data |col1|col2|col3|col4|col5|col6|col7|";
        String[] texts = {"Page 1 text", "Page 2 text", tableText, "Page 4 text"};
        File pdf = createTextPdf(4, texts);
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            // 第 3 页（索引2）: 含表格 + 非关键页（非首页、非末页、无医学关键词）
            PageLevelResult result = classifier.classify(doc, 2, 4);
            assertEquals(PageType.COMPLEX_TABLE_NON_KEY, result.getPageType(),
                    "中间页含表格无医学关键词应判定为 COMPLEX_TABLE_NON_KEY: " + result.getReason());
            assertFalse(result.isKeyPage());
            assertTrue(result.isHasComplexTable());
        }
    }

    // ===================== 自定义关键词 =====================

    @Test
    void testClassify_CustomKeyword_ShouldMatch() throws IOException {
        java.util.Set<String> customKeywords = java.util.Set.of("ECG", "ultrasound");
        PageLevelClassifier classifier = new PageLevelClassifier(10, customKeywords);
        String longText = "Medical imaging is an important diagnostic tool in clinical practice.";
        String[] texts = {longText, longText,
                "Patient received ECG examination results showing normal findings.",
                longText, longText};
        File pdf = createTextPdf(5, texts);
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            PageLevelResult result = classifier.classify(doc, 2, 5);
            assertTrue(result.isKeyPage(), "含自定义关键词'ECG'应判定为关键页: " + result.getReason());
        }
    }
}
