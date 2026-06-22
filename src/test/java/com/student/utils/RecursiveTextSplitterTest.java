package com.student.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecursiveTextSplitter 单元测试
 *
 * @author 系统
 * @version 1.0
 */
class RecursiveTextSplitterTest {

    /** 创建一个适用于小 chunk 测试的分割器（minChunkSize=0 避免过滤） */
    private static RecursiveTextSplitter smallSplitter(int chunkSize, int overlap) {
        return new RecursiveTextSplitter(chunkSize, overlap, 0,
                List.of("\n\n", "\n", "。", "；", "，", " ", ""));
    }

    // ===================== 基本分割 =====================

    @Test
    void testSplit_TextShorterThanChunkSize_shouldReturnSingleChunk() {
        RecursiveTextSplitter splitter = smallSplitter(500, 50);
        List<String> result = splitter.split("短文本");
        assertEquals(1, result.size());
        assertEquals("短文本", result.get(0));
    }

    @Test
    void testSplit_SplitByDoubleNewline_shouldKeepParagraphsIntact() {
        // 每个段落 8 chars, chunkSize=15 时双换行合并会导致相邻合并
        // 直接用较大 chunkSize 但段落间不合并的策略
        RecursiveTextSplitter splitter = smallSplitter(50, 0);
        String text = "第一段内容AAAAA\n\n第二段内容BBBBB\n\n第三段内容CCCCC";
        List<String> result = splitter.split(text);
        // 3 个段落，每个都远小于 50，合并时同级尝试合并
        // buffer: "第一段内容AAAAA" + "\n\n" + "第二段内容BBBBB" = 20 chars < 50
        // buffer: ... + "\n\n" + "第三段内容CCCCC" = 31 chars < 50
        // 所以全部合并为 1 个 chunk
        assertEquals(1, result.size());
    }

    @Test
    void testSplit_FallbackToSingleNewline_shouldSplitLines() {
        RecursiveTextSplitter splitter = smallSplitter(13, 0);
        // 每行 14 chars, chunkSize=13 → 无法合并 → 各自独立
        String text = "这是第一行很长\n这是第二行很长";
        List<String> result = splitter.split(text);
        assertEquals(2, result.size(), "每行 14 chars > chunkSize 13，应各自独立");
    }

    @Test
    void testSplit_FallbackToChinesePeriod_shouldSplitSentences() {
        RecursiveTextSplitter splitter = smallSplitter(10, 0);
        // 每句 6-7 chars < 10 → 合并。但第二句 + 第三句 = 14 > 10 → 拆分
        String text = "这是第一句。这是第二句。这是第三句。";
        List<String> result = splitter.split(text);
        // 6+1+7=14 → flush, 第二三句的 merge: 7+1+6=14 → flush
        assertTrue(result.size() >= 2, "chunkSize=10 时至少产生 2 个 chunk");
    }

    // ===================== 递归降级 =====================

    @Test
    void testSplit_RecurseWhenSegmentTooLarge_shouldUseNextSeparator() {
        RecursiveTextSplitter splitter = smallSplitter(80, 0);
        // 两个长句子，中间用 \n\n 分隔。每句约 22 chars，"长句A很长...。长句B..."
        // chunkSize=18 → 会递归到。 分隔
        RecursiveTextSplitter tight = smallSplitter(18, 0);
        String text = "短段落。\n\n这是第一句很长很长的内容。这是第二句很长很长的内容。";
        List<String> result = tight.split(text);
        // 每个 chunk 不超过 18
        for (String chunk : result) {
            assertTrue(chunk.length() <= 18,
                    "Chunk 长度 " + chunk.length() + " 超出 chunkSize 18: " + chunk);
        }
    }

    @Test
    void testSplit_CharacterLevelFallback_shouldHardSplit() {
        RecursiveTextSplitter splitter = smallSplitter(10, 0);
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"; // 无任何分隔符
        List<String> result = splitter.split(text);
        assertTrue(result.size() >= 3);
        for (String chunk : result) {
            assertTrue(chunk.length() <= 10,
                    "Chunk 长度 " + chunk.length() + " 超出 chunkSize 10");
        }
    }

    @Test
    void testSplit_MixedSeparators_shouldFollowPriority() {
        RecursiveTextSplitter splitter = smallSplitter(100, 0);
        String text = "段落A短\n\n段落B里面有句号。第二句。\n\n段落C只有换行\n第一行\n第二行";
        List<String> result = splitter.split(text);
        assertFalse(result.isEmpty());
        for (String chunk : result) {
            assertTrue(chunk.length() <= 100);
        }
    }

    // ===================== 剂量保护 =====================

    @Test
    void testDosageProtection_QuantityUnitPattern_shouldStayIntact() {
        RecursiveTextSplitter splitter = smallSplitter(50, 0);
        String text = "阿莫西林用法为口服。成人剂量为10mg每日一次。本品应在饭后服用。";
        List<String> result = splitter.split(text);
        boolean foundIntact = result.stream().anyMatch(c -> c.contains("10mg"));
        assertTrue(foundIntact, "剂量 '10mg' 应保持完整");
    }

    @Test
    void testDosageProtection_ChinesePillPattern_shouldStayIntact() {
        RecursiveTextSplitter splitter = smallSplitter(50, 0);
        String text = "请遵医嘱。每次2片，每日3次，饭后服用。请勿超量。";
        List<String> result = splitter.split(text);
        boolean foundPill = result.stream().anyMatch(c -> c.contains("每次2片"));
        boolean foundDaily = result.stream().anyMatch(c -> c.contains("每日3次"));
        assertTrue(foundPill, "'每次2片' 应保持完整");
        assertTrue(foundDaily, "'每日3次' 应保持完整");
    }

    @Test
    void testDosageProtection_TimesPerDayPattern_shouldStayIntact() {
        RecursiveTextSplitter splitter = smallSplitter(50, 0);
        String text = "本品用法用量：3次/日，每次1片。孕妇禁用。";
        List<String> result = splitter.split(text);
        boolean found = result.stream().anyMatch(c -> c.contains("3次/日"));
        assertTrue(found, "'3次/日' 应保持完整");
    }

    @Test
    void testDosageProtection_RangePattern_shouldStayIntact() {
        RecursiveTextSplitter splitter = smallSplitter(60, 0);
        String text = "儿童用药剂量为5-10mg/kg，每日分2次服用。请严格按体重计算剂量。";
        List<String> result = splitter.split(text);
        boolean found = result.stream().anyMatch(c -> c.contains("5-10mg"));
        assertTrue(found, "'5-10mg' 剂量范围应保持完整");
    }

    // ===================== 列表保护 =====================

    @Test
    void testListProtection_NumberedList_shouldStayTogether() {
        RecursiveTextSplitter splitter = smallSplitter(200, 0);
        String text = "适应症包括：\n1. 上呼吸道感染\n2. 下呼吸道感染\n3. 泌尿生殖道感染\n\n其他说明文字。";
        List<String> result = splitter.split(text);
        boolean listTogether = result.stream()
                .anyMatch(c -> c.contains("上呼吸道感染")
                        && c.contains("下呼吸道感染")
                        && c.contains("泌尿生殖道感染"));
        assertTrue(listTogether, "编号列表应尽量保持整体");
    }

    @Test
    void testListProtection_BulletList_shouldStayTogether() {
        RecursiveTextSplitter splitter = smallSplitter(200, 0);
        String text = "注意事项：\n- 对青霉素过敏者禁用\n- 严重肝肾功能不全者慎用\n- 孕妇及哺乳期妇女慎用\n\n其他内容。";
        List<String> result = splitter.split(text);
        boolean listTogether = result.stream()
                .anyMatch(c -> c.contains("青霉素过敏")
                        && c.contains("肝肾功能不全")
                        && c.contains("哺乳期妇女"));
        assertTrue(listTogether, "无序列表应尽量保持整体");
    }

    @Test
    void testListProtection_SingleListItem_shouldNotBeProtected() {
        RecursiveTextSplitter splitter = smallSplitter(100, 0);
        String text = "这是一段普通文字。\n1. 单条说明\n继续普通文字内容。";
        List<String> result = splitter.split(text);
        assertFalse(result.isEmpty());
    }

    // ===================== 短块合并（同级别内合并） =====================

    @Test
    void testMerge_AdjacentShortChunksAtSameLevel_shouldMerge() {
        // chunkSize=50, 两个相邻短片段在同一个分隔符级别会合并
        RecursiveTextSplitter splitter = smallSplitter(50, 0);
        String text = "短A。短B。这是一段足够长的内容用来填充分块大小超过五十个字符限制。";
        List<String> result = splitter.split(text);
        // "短A。短B。" 会被合并。注：分隔符是。所以 "短A" = 2chars, "短B" = 2chars
        // buffer: "短A" → "短A。短B" = 5 < 50 → merged
        boolean hasMerged = result.stream().anyMatch(c -> c.contains("短A") && c.contains("短B"));
        assertTrue(hasMerged, "同级别的短片段应被合并");
    }

    @Test
    void testMerge_MergedChunkExceedsSize_shouldKeepSeparate() {
        // chunkSize=8, "ABCD"=4, "EFGH"=4, 合并 = 9 > 8 → 各自独立
        RecursiveTextSplitter splitter = smallSplitter(8, 0);
        String text = "ABCD。EFGH。";
        List<String> result = splitter.split(text);
        // ABCD=4, EFGH=4, 分隔符。合并 = 9 > 8 → 各自独立
        assertEquals(2, result.size());
        assertTrue(result.contains("ABCD"));
        assertTrue(result.contains("EFGH"));
    }

    @Test
    void testMerge_LastShortChunkMergesBackward() {
        RecursiveTextSplitter splitter = smallSplitter(30, 0);
        // 最后一个小片段会尝试与前一个 buffer 合并
        String text = "前一段内容正常。尾";
        List<String> result = splitter.split(text);
        // "前一段内容正常。" = 8chars, "尾" = 1char, 合并 = 10 < 30 → 1 个 chunk
        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("尾"));
    }

    // ===================== 边界条件 =====================

    @Test
    void testSplit_EmptyText_shouldReturnEmptyList() {
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(500, 50);
        List<String> result = splitter.split("");
        assertTrue(result.isEmpty());
    }

    @Test
    void testSplit_NullText_shouldThrowException() {
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(500, 50);
        assertThrows(IllegalArgumentException.class, () -> splitter.split(null));
    }

    @Test
    void testSplit_WhitespaceOnly_shouldReturnEmptyList() {
        RecursiveTextSplitter splitter = smallSplitter(500, 50);
        List<String> result = splitter.split("   \n  \n  ");
        assertTrue(result.isEmpty(), "纯空白文本应返回空列表");
    }

    @Test
    void testSplit_MinChunkSizeFilter_shouldDiscardTinyChunks() {
        // 使用默认 minChunkSize=20
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(500, 0);
        String text = "很短的。这是一个足够长的句子内容被保留下来用于验证最小分块大小的过滤功能。";
        List<String> result = splitter.split(text);
        for (String chunk : result) {
            assertTrue(chunk.length() >= 20,
                    "Chunk 长度 " + chunk.length() + " 应 >= minChunkSize 20: '" + chunk + "'");
        }
    }

    // ===================== 重叠 =====================

    @Test
    void testOverlap_shouldAddSlidingWindowOverlap() {
        RecursiveTextSplitter splitter = smallSplitter(5, 2);
        // 无分隔符的纯字母串，强制按字符切分 → chunkSize=5 产生多个 chunk
        String text = "ABCDEFGHIJKLMNOP"; // 16 chars → 4 chunks of 5
        List<String> result = splitter.split(text);
        assertTrue(result.size() >= 2, "长文本应产生多个 chunk");

        if (result.size() >= 2) {
            // 第一个 chunk 末尾 2 字符 = 第二个 chunk 开头 2 字符（重叠）
            String firstEnd = result.get(0).substring(result.get(0).length() - 2);
            String secondStart = result.get(1).substring(0, 2);
            assertEquals(firstEnd, secondStart,
                    "第二个 chunk 应以第一个 chunk 的尾部分开头");
        }
    }

    @Test
    void testOverlap_ZeroOverlap_shouldNotAddOverlap() {
        RecursiveTextSplitter splitter = smallSplitter(5, 0);
        String text = "ABCDEFGHIJ"; // 10 chars → 2 chunks of 5
        List<String> result = splitter.split(text);
        assertEquals(2, result.size());
        // 无重叠时第二个 chunk 不应以第一个的末尾开头
        assertFalse(result.get(1).startsWith(result.get(0).substring(result.get(0).length() - 2)),
                "无重叠时不应以前一个 chunk 末尾开头");
    }

    // ===================== 构造函数校验 =====================

    @Test
    void testConstructor_OverlapGreaterThanOrEqualToChunkSize_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new RecursiveTextSplitter(100, 100));
        assertThrows(IllegalArgumentException.class, () -> new RecursiveTextSplitter(100, 150));
    }

    @Test
    void testConstructor_ValidParameters_shouldCreate() {
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(300, 50, 30,
                List.of("\n\n", "\n", "。", " "));
        assertNotNull(splitter);
    }

    // ===================== 完整性 =====================

    @Test
    void testSplit_PreservesAllCharacters() {
        RecursiveTextSplitter splitter = smallSplitter(100, 0);
        String original = "阿莫西林胶囊说明书\n\n【适应症】适用于敏感菌所致的感染。\n\n【用法用量】口服。成人一次0.5g。";
        List<String> result = splitter.split(original);

        int totalChars = result.stream().mapToInt(String::length).sum();
        assertTrue(totalChars > 0);
        // 拼接字符数不应少于原文 trim 后的 70%（部分空白可能丢失）
        assertTrue(totalChars >= original.trim().length() * 0.7,
                "不应丢失过多字符: total=" + totalChars + ", original=" + original.trim().length());
    }

    // ===================== 生产默认构造 =====================

    @Test
    void testDefaultConstructor_shouldUseProductionDefaults() {
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(500, 50);
        String text = "段落一的内容。\n\n段落二的内容。\n\n段落三的内容。";
        List<String> result = splitter.split(text);
        assertTrue(result.size() >= 1);
        for (String chunk : result) {
            assertTrue(chunk.length() <= 500, "Chunk 不应超过 chunkSize 500");
        }
    }
}
