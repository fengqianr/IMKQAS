package com.student.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MedicalDocumentSplitter 单元测试
 * 覆盖章节检测、层级构建、分片质量、元数据正确性等场景
 */
@DisplayName("医学文档结构感知分片器测试")
class MedicalDocumentSplitterTest {

    private MedicalDocumentSplitter splitter;
    private MedicalDocumentSplitter smallSplitter;

    /** 默认配置的分片器 */
    private MedicalDocumentSplitter defaultSplitter() {
        return new MedicalDocumentSplitter(500, 50, 20,
                List.of("\n\n", "\n", "。", "；", "，", " ", ""),
                true, true, true, true, 2, 2, 150, 5);
    }

    /** 小 chunk 分片器（用于测试超长内容降级） */
    private MedicalDocumentSplitter smallChunkSplitter() {
        return new MedicalDocumentSplitter(120, 20, 10,
                List.of("\n\n", "\n", "。", "；", "，", " ", ""),
                true, true, true, true, 2, 2, 0, 5);
    }

    @BeforeEach
    void setUp() {
        splitter = defaultSplitter();
        smallSplitter = smallChunkSplitter();
    }

    // ======================== 边界情况 ========================

    @Test
    @DisplayName("null 输入抛异常")
    void testNullInput_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> splitter.split(null));
        assertThrows(IllegalArgumentException.class, () -> splitter.splitWithMetadata(null));
    }

    @Test
    @DisplayName("空字符串返回空列表")
    void testEmptyInput_returnsEmptyList() {
        assertTrue(splitter.split("").isEmpty());
        assertTrue(splitter.splitWithMetadata("").isEmpty());
    }

    @Test
    @DisplayName("纯文本无标题 — 平坦分片")
    void testPlainText_noHeaders() {
        String text = "这是一段没有任何标题的普通医学文本。" +
                "它包含多句话但是没有章节标记。" +
                "这种情况下应该回退到标准的递归分片。".repeat(8);

        List<MedicalDocumentSplitter.SegmentInfo> result = splitter.splitWithMetadata(text);
        assertFalse(result.isEmpty());
        // 无标题时 sectionPath 应为空数组
        for (MedicalDocumentSplitter.SegmentInfo info : result) {
            assertEquals("[]", info.getSectionPath(),
                    "无标题文档的 sectionPath 应为空数组");
        }
    }

    // ======================== Phase 2: 结构解析 ========================

    @Nested
    @DisplayName("章节标题检测")
    class SectionDetection {

        @Test
        @DisplayName("【】括号标题检测")
        void testDetection_BracketHeaders() {
            String text = "导言文本\n【病因与发病机制】\n内容A\n【临床表现】\n内容B\n【治疗方案】\n内容C";

            List<MedicalDocumentSplitter.HeaderMatch> headers =
                    splitter.detectHeaders(text);

            assertEquals(3, headers.size());
            assertEquals("病因与发病机制", headers.get(0).title);
            assertEquals("BRACKET", headers.get(0).patternType);
            assertEquals("临床表现", headers.get(1).title);
            assertEquals("治疗方案", headers.get(2).title);
        }

        @Test
        @DisplayName("Markdown 标题检测")
        void testDetection_MarkdownHeaders() {
            String text = "## 临床表现\n内容A\n### 症状\n内容B\n### 体征\n内容C";

            List<MedicalDocumentSplitter.HeaderMatch> headers =
                    splitter.detectHeaders(text);

            assertTrue(headers.size() >= 1);
            assertEquals("临床表现", headers.get(0).title);
        }

        @Test
        @DisplayName("中文编号标题检测")
        void testDetection_ChineseNumbered() {
            String text = "一、概述\n内容A\n二、流行病学\n内容B";

            List<MedicalDocumentSplitter.HeaderMatch> headers =
                    splitter.detectHeaders(text);

            assertTrue(headers.size() >= 2);
        }

        @Test
        @DisplayName("冒号副标题检测")
        void testDetection_ColonSubtitle() {
            String text = "急性发作期治疗（SABA为核心）：\n• 轻中度：沙丁胺醇雾化\n• 重度：异丙托溴铵";

            List<MedicalDocumentSplitter.HeaderMatch> headers =
                    splitter.detectHeaders(text);

            assertTrue(headers.size() >= 1);
            assertEquals("急性发作期治疗（SABA为核心）", headers.get(0).title);
            assertEquals("COLON_SUBTITLE", headers.get(0).patternType);
        }

        @Test
        @DisplayName("短独立行标题检测（兜底）")
        void testDetection_ShortStandalone() {
            String text = "预防措施\n• 尘螨控制：防螨床垫\n• 禁止室内吸烟\n\n紧急处理要点\n• SpO₂立即氧疗";

            List<MedicalDocumentSplitter.HeaderMatch> headers =
                    splitter.detectHeaders(text);

            assertTrue(headers.size() >= 2,
                    "预防措施和紧急处理要点应被检测为独立标题");
        }

        @Test
        @DisplayName("标题优先级：【】 > Markdown > 编号")
        void testDetection_PriorityOrder() {
            String text = "【病因】\n## 具体病因\n这是【】优先的内容";

            List<MedicalDocumentSplitter.HeaderMatch> headers =
                    splitter.detectHeaders(text);

            // 【病因】应该被检测到，且不应和 ## 冲突
            boolean hasBracket = headers.stream()
                    .anyMatch(h -> "BRACKET".equals(h.patternType));
            assertTrue(hasBracket);
        }
    }

    @Nested
    @DisplayName("层级分配")
    class LevelAssignment {

        @Test
        @DisplayName("【】标题分配 level 1")
        void testLevel_BracketHeader() {
            MedicalDocumentSplitter.HeaderMatch match =
                    new MedicalDocumentSplitter.HeaderMatch(0, 10, "病因", "BRACKET");
            assertEquals(1, splitter.assignLevel(match));
        }

        @Test
        @DisplayName("中文编号分配 level 2")
        void testLevel_ChineseNumbered() {
            MedicalDocumentSplitter.HeaderMatch match =
                    new MedicalDocumentSplitter.HeaderMatch(0, 5, "一、概述", "CHINESE_NUMBERED");
            assertEquals(2, splitter.assignLevel(match));
        }

        @Test
        @DisplayName("冒号副标题分配 level 2")
        void testLevel_ColonSubtitle() {
            MedicalDocumentSplitter.HeaderMatch match =
                    new MedicalDocumentSplitter.HeaderMatch(0, 15, "急性发作期治疗", "COLON_SUBTITLE");
            assertEquals(2, splitter.assignLevel(match));
        }
    }

    @Nested
    @DisplayName("结构树构建")
    class TreeBuilding {

        @Test
        @DisplayName("父子关系正确构建")
        void testTree_ParentChild() {
            // 治疗方案(level1) → 急性发作期(level2), 长期控制(level2)
            String text = "主体文本\n" +
                    "【治疗方案】\n方案总述\n" +
                    "急性发作期治疗（SABA为核心）：\n• 轻中度细节\n" +
                    "长期控制治疗（阶梯方案）：\n• 第一阶细节";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            // 检查是否有 level=2 的 chunk，且其 parent 是 "治疗方案"
            boolean foundParentChild = false;
            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                if (info.getSectionLevel() == 2
                        && "治疗方案".equals(info.getParentSection())) {
                    foundParentChild = true;
                    break;
                }
            }
            assertTrue(foundParentChild,
                    "level 2 的 chunk 应有 parent_section='治疗方案'");
        }

        @Test
        @DisplayName("兄弟关系正确注入")
        void testTree_Siblings() {
            String text = "【病因与发病机制】\n内容A\n【临床表现】\n内容B\n【诊断标准】\n内容C";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            boolean foundSibling = false;
            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                if ("临床表现".equals(info.getSectionTitle())
                        || "临床表现与诊断要点".equals(info.getSectionTitle())) {
                    // 临床表现的 prev 应是 "病因与发病机制" 或类似，next 应是 "诊断标准"
                    if (info.getPrevSibling() != null || info.getNextSibling() != null) {
                        foundSibling = true;
                    }
                }
            }
            assertTrue(foundSibling, "应存在兄弟章节信息");
        }
    }

    // ======================== Phase 3: 分片质量 ========================

    @Nested
    @DisplayName("章节级分片")
    class SectionChunking {

        @Test
        @DisplayName("短章节独立成块")
        void testChunk_ShortSections_individualChunks() {
            String text = "【病因】\n过敏体质、尘螨暴露。\n" +
                    "【表现】\n反复发作性喘息、慢性咳嗽。\n" +
                    "【标准】\n肺功能FEV₁/FVC<80%。";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            // 三个短章节应该各自成块（可能合并到前导文本等）
            assertTrue(result.size() >= 1,
                    "至少应有 1 个 chunk");
            // 验证每个 chunk 的长度不超过 chunkSize
            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                assertTrue(info.getText().length() <= 500,
                        "每个 chunk 不应超过 500 字符");
            }
        }

        @Test
        @DisplayName("超长章节回退递归分片")
        void testChunk_OversizedSection_fallsBack() {
            String longContent = "这是一个超长的医学章节内容。" +
                    "它包含非常多的详细描述。".repeat(30);

            List<String> chunks = smallSplitter.split(longContent);
            assertTrue(chunks.size() > 1,
                    "超长内容应被分为多个 chunk");
            for (String chunk : chunks) {
                assertTrue(chunk.length() <= 160,
                        "每个 chunk 长度不应明显超过 chunkSize(120)+overlap(20)");
            }
        }

        @Test
        @DisplayName("分片携带正确的面包屑路径")
        void testChunk_Breadcrumb() {
            String text = "9. 儿童支气管哮喘\n\n" +
                    "哮喘是儿童最常见的慢性呼吸道疾病...\n\n" +
                    "【病因与发病机制】\n• 遗传因素：过敏体质家族史\n• 过敏原暴露：尘螨\n\n" +
                    "【治疗方案】\n急性发作期治疗（SABA为核心）：\n• 轻中度：沙丁胺醇雾化";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            boolean hasBreadcrumb = false;
            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                if (info.getBreadcrumb() != null && info.getBreadcrumb().contains(">")) {
                    hasBreadcrumb = true;
                }
            }
            assertTrue(hasBreadcrumb, "至少有一个 chunk 包含层级面包屑");

            // 验证文档主题被提取
            boolean hasTopic = result.stream()
                    .anyMatch(i -> i.getDocumentTopic() != null);
            assertTrue(hasTopic, "应有文档主题");
        }
    }

    // ======================== 表格保护 ========================

    @Nested
    @DisplayName("表格保护")
    class TableProtection {

        @Test
        @DisplayName("管道表格完整保留")
        void testTable_PipeTable_intact() {
            String text = "临床表现\n" +
                    "| 症状/体征 | 诊断要点 |\n" +
                    "|------|------|\n" +
                    "| 反复发作性喘息 | 双肺哮鸣音 |\n" +
                    "| 慢性咳嗽 | 夜间/晨起加重 |\n\n" +
                    "后续说明文本。";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            // 表格应出现在某个 chunk 中
            boolean tableFound = false;
            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                if (info.getText().contains("| 症状/体征 | 诊断要点 |")) {
                    tableFound = true;
                    // 验证表格行都在同一个 chunk 中
                    assertTrue(info.getText().contains("反复发作性喘息"),
                            "表格行应在同一个 chunk 中");
                    assertTrue(info.getText().contains("慢性咳嗽"),
                            "表格行应在同一个 chunk 中");
                    assertTrue(info.isTable(), "isTable 标记应为 true");
                    break;
                }
            }
            assertTrue(tableFound, "表格应完整保留在某个 chunk 中");
        }

        @Test
        @DisplayName("不足最小行数的表格不保护")
        void testTable_TooFewRows_notProtected() {
            String text = "单行伪表格\n| 字段 | 值 |\n正文继续";

            // 使用 minTableRows=2 的默认配置
            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            // 2行（含分隔行只有1行数据），不应触发表格保护
            boolean tableFlagged = result.stream().anyMatch(
                    MedicalDocumentSplitter.SegmentInfo::isTable);
            assertFalse(tableFlagged,
                    "不足最小行数的伪表格不应标记 isTable");
        }
    }

    // ======================== ICD 编码提取 ========================

    @Nested
    @DisplayName("ICD 编码提取")
    class IcdExtraction {

        @Test
        @DisplayName("提取标准 ICD-10 编码")
        void testIcd_StandardCodes_extracted() {
            String text = "儿童支气管哮喘 ICD 编码：J45\n" +
                    "2型糖尿病编码为 E11，而 E11.9 为未特指的2型糖尿病。";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            boolean j45Found = false;
            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                if (info.getIcdCodes().contains("J45")) {
                    j45Found = true;
                }
            }
            assertTrue(j45Found, "J45 应被提取");

            // 检查带小数点的 ICD 编码
            boolean e11_9Found = result.stream()
                    .flatMap(i -> i.getIcdCodes().stream())
                    .anyMatch("E11.9"::equals);
            assertTrue(e11_9Found, "E11.9 应被提取");
        }

        @Test
        @DisplayName("无 ICD 编码时列表为空")
        void testIcd_NoCodes_emptyList() {
            String text = "这是一段不包含任何 ICD 编码的普通医学文本。";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                assertTrue(info.getIcdCodes().isEmpty(),
                        "无 ICD 编码时应返回空列表");
            }
        }
    }

    // ======================== 元数据完整性 ========================

    @Nested
    @DisplayName("元数据完整性")
    class MetadataCompleteness {

        @Test
        @DisplayName("metadata JSON 包含所有必需字段")
        void testMetadata_AllFieldsPresent() {
            String text = "9. 儿童支气管哮喘\n\n" +
                    "ICD 编码：J45\n" +
                    "哮喘是儿童最常见的慢性呼吸道疾病...\n\n" +
                    "【病因与发病机制】\n• 遗传因素：过敏体质家族史\n• 过敏原暴露：尘螨\n\n" +
                    "【诊断标准】\n• 肺功能FEV₁/FVC<80%\n\n" +
                    "【治疗方案】\n急性发作期治疗（SABA为核心）：\n" +
                    "• 轻中度：沙丁胺醇雾化0.15mg/kg\n" +
                    "• 重度：异丙托溴铵联合SABA\n" +
                    "长期控制治疗（阶梯方案）：\n• 第一阶：按需SABA\n\n" +
                    "【预防措施】\n• 尘螨控制：防螨床垫\n\n" +
                    "【紧急处理要点】\n• SpO₂<92%立即氧疗";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            assertFalse(result.isEmpty(), "应有分片结果");

            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                // 每个 SegmentInfo 应有非空 text
                assertNotNull(info.getText());
                assertFalse(info.getText().isBlank());

                // startChar / endChar 应合理
                assertTrue(info.getStartChar() >= 0);
                assertTrue(info.getEndChar() > info.getStartChar());

                // sectionPath / breadcrumb 不应为 null
                assertNotNull(info.getSectionPath());
                assertNotNull(info.getBreadcrumb());

                // sectionLevel 应在合理范围
                assertTrue(info.getSectionLevel() >= 0);
                assertTrue(info.getSectionLevel() <= 5);
            }
        }

        @Test
        @DisplayName("文档级别元数据一致")
        void testMetadata_DocumentLevelConsistent() {
            String text = "9. 儿童支气管哮喘\n\n" +
                    "【病因】\n内容A\n【表现】\n内容B\n【诊断】\n内容C";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            // 所有 chunk 应有相同的 documentTopic
            String firstTopic = null;
            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                if (firstTopic == null && info.getDocumentTopic() != null) {
                    firstTopic = info.getDocumentTopic();
                }
                if (firstTopic != null && info.getDocumentTopic() != null) {
                    assertEquals(firstTopic, info.getDocumentTopic(),
                            "同一文档的所有 chunk 应有相同的 documentTopic");
                }
            }
        }

        @Test
        @DisplayName("层级嵌套正确：治疗方案子章节路径")
        void testMetadata_NestedPath() {
            String text = "【治疗方案】\n" +
                    "急性发作期治疗（SABA为核心）：\n• 轻中度：沙丁胺醇雾化0.15mg/kg\n" +
                    "长期控制治疗（阶梯方案）：\n• 第一阶：按需SABA";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    splitter.splitWithMetadata(text);

            boolean level2Found = false;
            for (MedicalDocumentSplitter.SegmentInfo info : result) {
                if (info.getSectionLevel() == 2) {
                    level2Found = true;
                    // 子章节的 parent 应为 "治疗方案"
                    assertEquals("治疗方案", info.getParentSection(),
                            "level 2 子章节的 parent_section 应为'治疗方案'");
                    // 子章节的 root 也应为 "治疗方案"
                    assertEquals("治疗方案", info.getRootSection(),
                            "level 2 子章节的 root_section 应为'治疗方案'");
                }
            }
            assertTrue(level2Found, "应有 level 2 的子章节 chunk");
        }
    }

    // ======================== 同级合并 ========================

    @Nested
    @DisplayName("同级合并")
    class SiblingMerging {

        @Test
        @DisplayName("同层级小 chunk 合并")
        void testMerge_SmallSiblings_merged() {
            MedicalDocumentSplitter mergeSplitter = new MedicalDocumentSplitter(
                    500, 50, 10,
                    List.of("\n\n", "\n", "。", "；", "，", " ", ""),
                    true, true, true, true, 2, 2, 200, 5);

            // 三个极短的 bullet points，在同一章节下，应合并
            String text = "【诊断标准】\n• 标准一\n• 标准二\n• 标准三";

            List<MedicalDocumentSplitter.SegmentInfo> result =
                    mergeSplitter.splitWithMetadata(text);

            // 三个 bullet 可能被合并成一个 chunk
            long smallCount = result.stream()
                    .filter(i -> i.getText().length() < 100)
                    .count();
            assertTrue(smallCount <= 1 || result.size() <= 2,
                    "过小的同层级 chunk 应被合并，不应有太多小碎片");
        }
    }

    // ======================== 综合测试 ========================

    @Test
    @DisplayName("完整医学文档综合测试")
    void testIntegration_CompleteMedicalDocument() {
        String text = "9. 儿童支气管哮喘\n\n" +
                "别名：哮喘、喘息性支气管炎（婴幼儿）    ICD 编码：J45\n" +
                "哮喘是儿童最常见的慢性呼吸道疾病，以反复发作性喘息、咳嗽为特征...\n\n" +
                "【病因与发病机制】\n" +
                "• 遗传因素：过敏体质（特应性）家族史；多基因遗传\n" +
                "• 过敏原暴露：尘螨（最主要）、动物皮屑、蟑螂、花粉、真菌孢子\n" +
                "• 促发因素：病毒感染（占儿童哮喘发作80%）、运动、冷空气\n" +
                "• 发病机制：Th2介导的慢性气道炎症 → 气道高反应性 → 可逆性气道阻塞\n\n" +
                "【临床表现与诊断要点】\n" +
                "| 症状/体征 | 诊断要点 |\n" +
                "|------|------|\n" +
                "| 反复发作性喘息 | 听诊可闻及双肺广泛哮鸣音 |\n" +
                "| 慢性咳嗽（夜间/晨起加重） | 咳嗽变异型哮喘 |\n\n" +
                "【诊断标准】\n" +
                "• ≥6岁：肺功能FEV₁/FVC<80%，支气管舒张试验阳性（FEV₁增加≥12%）\n" +
                "• <6岁：依赖临床诊断（API指数阳性）+ 治疗性诊断\n" +
                "• 呼出气一氧化氮（FeNO）升高（>25ppb）提示嗜酸性气道炎症\n\n" +
                "【治疗方案】\n" +
                "急性发作期治疗（SABA为核心）：\n" +
                "• 轻中度：沙丁胺醇雾化0.15mg/kg（最小2.5mg），每20分钟一次 × 3次\n" +
                "• 重度：异丙托溴铵联合SABA雾化；全身激素（甲泼尼龙1～2mg/kg静注）\n" +
                "• 危重：氦氧混合气、MgSO₄静脉（25～75mg/kg），必要时机械通气\n" +
                "长期控制治疗（阶梯方案）：\n" +
                "• 第一阶：按需SABA（轻度间歇）\n" +
                "• 第二阶：低剂量ICS（如布地奈德100μg/次，bid）——控制治疗基石\n" +
                "• 第三阶：ICS+LABA（≥5岁）；或中剂量ICS（<5岁）\n" +
                "• 第四阶：中高剂量ICS+LABA+孟鲁司特；重症考虑奥马珠单抗（抗IgE）\n\n" +
                "预防措施\n" +
                "• 尘螨控制：防螨床垫、热水洗涤（>60°C）、减少毛绒玩具\n" +
                "• 禁止室内吸烟，减少二手烟/三手烟暴露\n\n" +
                "紧急处理要点\n" +
                "• SpO₂<92%立即氧疗+SABA雾化\n" +
                "• 重度发作使用SABA 3次无效需立即就医\n" +
                "• 沉默肺（哮鸣音消失+呼吸困难加重）是极危重信号\n" +
                "• 建立哮喘行动计划，家长须掌握家庭急救步骤";

        List<MedicalDocumentSplitter.SegmentInfo> result =
                splitter.splitWithMetadata(text);

        assertFalse(result.isEmpty());

        // 验证 ICD 编码 J45 被提取
        boolean j45Extracted = result.stream()
                .flatMap(i -> i.getIcdCodes().stream())
                .anyMatch("J45"::equals);
        assertTrue(j45Extracted, "J45 应被提取，但实际 ICD 列表: " +
                result.stream().flatMap(i -> i.getIcdCodes().stream()).toList());

        // 验证有章节层级信息
        boolean hasLeveledSections = result.stream()
                .anyMatch(i -> i.getSectionLevel() >= 1);
        assertTrue(hasLeveledSections, "应有 level >= 1 的章节 chunk");

        // 验证表格被保护
        boolean tableIntact = result.stream()
                .anyMatch(i -> i.getText().contains("症状/体征")
                        && i.getText().contains("反复发作性喘息")
                        && i.getText().contains("慢性咳嗽"));
        assertTrue(tableIntact, "表格应完整保留");

        // 每个 chunk 应有内容
        for (MedicalDocumentSplitter.SegmentInfo info : result) {
            assertTrue(info.getText().length() >= 10,
                    "chunk 内容不应太短: " + info.getText());
            assertTrue(info.getText().length() <= 550,
                    "chunk 长度不应明显超过 chunkSize(500)");
        }

        // 打印分片概览，便于人工检查
        System.out.println("=== 儿童支气管哮喘文档分片结果 ===");
        System.out.println("总 chunk 数: " + result.size());
        for (int i = 0; i < result.size(); i++) {
            MedicalDocumentSplitter.SegmentInfo info = result.get(i);
            System.out.printf("Chunk #%d: level=%d, breadcrumb=%s, icd=%s, table=%s, len=%d%n",
                    i, info.getSectionLevel(), info.getBreadcrumb(),
                    info.getIcdCodes(), info.isTable(), info.getText().length());
            System.out.printf("  section_path: %s%n", info.getSectionPath());
            System.out.printf("  parent: %s, root: %s, prev: %s, next: %s%n",
                    info.getParentSection(), info.getRootSection(),
                    info.getPrevSibling(), info.getNextSibling());
            System.out.printf("  内容预览: %s...%n",
                    info.getText().substring(0, Math.min(60, info.getText().length()))
                            .replace("\n", "\\n"));
        }
    }
}
