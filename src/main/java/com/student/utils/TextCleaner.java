package com.student.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 文本后处理/清洗工具：对提取后的文本执行去页眉页脚、去引用标记、
 * 空白规范化、断行修复等操作。
 * 非 Spring Bean，使用时直接 new 即可。
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
public class TextCleaner {

    /** 引用标记模式：[1], [2-3], [1,2,5], [1, 2, 5] */
    private static final Pattern CITATION_PATTERN =
            Pattern.compile("\\[[\\d,\\-\\s]+\\]");

    /** 中文单字断行：孤立 CJK 字符 + 可选空格 + 换行 + 可选空格 + CJK 字符 */
    private static final Pattern BROKEN_LINE_PATTERN =
            Pattern.compile("(?<![\\u4e00-\\u9fff\\u3400-\\u4dbf])([\\u4e00-\\u9fff\\u3400-\\u4dbf])\\s*\\n\\s*([\\u4e00-\\u9fff\\u3400-\\u4dbf])");

    /** 多空格/制表符模式（不包含换行） */
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[ \\t]+");

    /** 多余换行模式（3个及以上） */
    private static final Pattern EXCESS_NEWLINE_PATTERN = Pattern.compile("\\n{3,}");

    /** 元数据关键词（Anna's Archive、DuXiu 等馆藏元数据） */
    private static final Pattern METADATA_KEYWORD = Pattern.compile(
            "annas-archive|anna['’]s\\s*archive|duxiu|filename_decoded|pdg_dir_name|document\\s+generated\\s+by",
            Pattern.CASE_INSENSITIVE);

    /** 元数据专属 JSON key（header_md5, sha1, sha256, crc32 等技术元数据） */
    private static final Pattern METADATA_JSON_KEYS = Pattern.compile(
            "header_md5|page_md5|sha1|sha256|crc32|filename_decoded|pdg_dir_name",
            Pattern.CASE_INSENSITIVE);

    /** 页眉页脚最大长度（超过此长度的行不判定为页眉页脚） */
    private static final int HEADER_FOOTER_MAX_LENGTH = 100;

    private final int headerFooterRepeatThreshold;

    public TextCleaner() {
        this(3);
    }

    public TextCleaner(int headerFooterRepeatThreshold) {
        this.headerFooterRepeatThreshold = headerFooterRepeatThreshold;
    }

    /**
     * 执行全部清洗规则（按顺序：元数据 → 页眉页脚 → 引用标记 → 空白规范化 → 断行修复）
     *
     * @param text 原始文本
     * @return 清洗后的文本
     */
    public String clean(String text) {
        if (text == null) return null;
        if (text.isBlank()) return "";

        String result = removeMetadata(text);
        result = removeHeadersFooters(result);
        result = removeCitationMarkers(result);
        result = normalizeWhitespace(result);
        result = fixBrokenLines(result);
        return result;
    }

    /**
     * 去除馆藏元数据：移除 Anna's Archive、DuXiu 等声明段落以及
     * JSON 技术元数据（header_md5、sha1、sha256、crc32 等）。
     * 按段落（双换行）检测，任一命中即移除整个段落。
     */
    public String removeMetadata(String text) {
        if (text == null) return null;
        if (text.isBlank()) return "";

        String[] paragraphs = text.split("\n\n", -1);
        StringBuilder result = new StringBuilder();
        for (String para : paragraphs) {
            if (!isMetadataParagraph(para)) {
                if (!result.isEmpty()) {
                    result.append("\n\n");
                }
                result.append(para);
            }
        }
        return result.toString();
    }

    private boolean isMetadataParagraph(String para) {
        if (para == null || para.isBlank()) return false;

        // 规则 1：命中元数据关键词
        if (METADATA_KEYWORD.matcher(para).find()) {
            return true;
        }

        // 规则 2：JSON 结构 + 元数据专属 key（header_md5, sha1 等）
        String stripped = para.replaceAll("\\s+", "");
        if (stripped.length() == 0) return false;
        if (!stripped.contains("{") && !stripped.contains("[")) return false;

        return METADATA_JSON_KEYS.matcher(stripped).find();
    }

    /**
     * 去除页眉页脚：基于重复行频率分析，移除每页重复出现的短行
     */
    public String removeHeadersFooters(String text) {
        if (text == null) return null;
        if (text.isBlank()) return "";

        String[] lines = text.split("\n", -1);
        Map<String, Integer> lineFrequency = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.length() > HEADER_FOOTER_MAX_LENGTH) {
                continue;
            }
            lineFrequency.put(trimmed, lineFrequency.getOrDefault(trimmed, 0) + 1);
        }

        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            boolean isHeaderFooter = !trimmed.isEmpty()
                    && trimmed.length() <= HEADER_FOOTER_MAX_LENGTH
                    && lineFrequency.getOrDefault(trimmed, 0) >= headerFooterRepeatThreshold;
            if (!isHeaderFooter) {
                if (!result.isEmpty()) {
                    result.append("\n");
                }
                result.append(line);
            }
        }
        return result.toString();
    }

    /**
     * 去除引用标记：匹配并移除 [1], [2-3], [1,2,5] 等纯数字引用
     */
    public String removeCitationMarkers(String text) {
        if (text == null) return null;
        if (text.isBlank()) return "";

        return CITATION_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * 空白规范化：多空格→单空格，≥3 换行→双换行
     */
    public String normalizeWhitespace(String text) {
        if (text == null) return null;
        if (text.isBlank()) return "";

        String result = MULTI_SPACE_PATTERN.matcher(text).replaceAll(" ");
        result = EXCESS_NEWLINE_PATTERN.matcher(result).replaceAll("\n\n");
        return result.trim();
    }

    /**
     * 修复中文断行：检测并修复词内换行（如 "中\n文" → "中文"）
     */
    public String fixBrokenLines(String text) {
        if (text == null) return null;
        if (text.isBlank()) return "";

        return BROKEN_LINE_PATTERN.matcher(text).replaceAll("$1$2");
    }
}
