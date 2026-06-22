package com.student.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 递归文本分割器
 * 按分隔符优先级递归分割文本，确保每个 chunk 不超过指定长度，
 * 并尽可能在语义边界上切分（段落 > 句子 > 子句 > 词 > 字符）。
 *
 * @author 系统
 * @version 1.0
 */
public class RecursiveTextSplitter {

    private final int chunkSize;
    private final int overlap;
    private final int minChunkSize;
    private final List<String> separators;

    /** 占位符前缀，用于保护剂量和列表等敏感文本 */
    private static final String PLACEHOLDER_PREFIX = "__PROTECTED_";

    /** 剂量模式正则（包内可见，供 MedicalDocumentSplitter 复用） */
    static final Pattern DOSAGE_PATTERN = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:mg|g|ml|μg|mcg|IU|单位|微克|毫克|克|毫升|纳克|ng)"
                    + "|每次\\s*\\d+(?:\\.\\d+)?\\s*[片粒袋支瓶包颗丸]"
                    + "|(?:每日|每天|一日)\\s*\\d+(?:\\.\\d+)?\\s*次"
                    + "|\\d+(?:\\.\\d+)?\\s*次\\s*/\\s*[日天周]"
                    + "|\\d+\\s*[-~至]\\s*\\d+\\s*(?:mg|g|ml|μg|片|粒|次)"
    );

    /** 编号列表项模式 */
    private static final Pattern NUMBERED_LIST_ITEM = Pattern.compile(
            "^\\d+[\\.、\\)]\\s+"
    );

    /** 无序列表项模式 */
    private static final Pattern BULLET_LIST_ITEM = Pattern.compile(
            "^[\\-\\*\\•\\◦\\▪]\\s+"
    );

    /**
     * 使用默认参数构造分割器
     * @param chunkSize 分块大小（字符数）
     * @param overlap   重叠字符数
     */
    public RecursiveTextSplitter(int chunkSize, int overlap) {
        this(chunkSize, overlap, 20,
                List.of("\n\n", "\n", "。", "；", "，", " ", ""));
    }

    /**
     * 使用完整参数构造分割器
     * @param chunkSize    分块大小（字符数）
     * @param overlap      重叠字符数
     * @param minChunkSize 最小 chunk 长度（< 此值丢弃）
     * @param separators   分隔符优先级列表
     */
    public RecursiveTextSplitter(int chunkSize, int overlap, int minChunkSize,
                                  List<String> separators) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap(" + overlap
                    + ") 不能大于或等于 chunkSize(" + chunkSize + ")");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        this.minChunkSize = minChunkSize;
        this.separators = Collections.unmodifiableList(new ArrayList<>(separators));
    }

    /**
     * 分割文本
     * @param text 原始文本
     * @return 分割后的 chunk 列表
     */
    public List<String> split(String text) {
        if (text == null) {
            throw new IllegalArgumentException("文本不能为 null");
        }
        if (text.isEmpty()) {
            return Collections.emptyList();
        }

        // Phase 1: 预处理 —— 保护剂量和列表
        Map<String, String> protectedBlocks = new LinkedHashMap<>();
        String processed = protectDosage(text, protectedBlocks);
        processed = protectList(processed, protectedBlocks);

        // Phase 2: 递归分割（内部包含短块合并，按同级别分隔符合并）
        List<String> chunks = recursiveSplit(processed, 0);

        // Phase 3: 滑动窗口重叠（在还原占位符之前，避免 overlap 截断保护文本）
        chunks = addOverlap(chunks);

        // Phase 4: 后处理 —— 还原占位符 + 过滤过短 chunk + trim
        chunks = restoreProtections(chunks, protectedBlocks);
        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            String trimmed = chunk.trim();
            if (trimmed.length() >= minChunkSize) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 递归分割核心逻辑（内含同级别短块合并）
     * @param text           待分割文本
     * @param separatorIndex 当前使用的分隔符索引
     * @return 分割后的 chunk 列表
     */
    private List<String> recursiveSplit(String text, int separatorIndex) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        if (text.length() <= chunkSize) {
            return new ArrayList<>(List.of(text));
        }

        // 兜底：无分隔符可用时按字符硬切
        if (separatorIndex >= separators.size()) {
            return forceSplitByChar(text);
        }

        String separator = separators.get(separatorIndex);
        if (separator.isEmpty()) {
            return forceSplitByChar(text);
        }

        // 按当前分隔符切分
        String[] parts = text.split(Pattern.quote(separator), -1);
        List<String> result = new ArrayList<>();
        String buffer = null;

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            // 超长片段：先 flush buffer，再递归降级处理
            if (part.length() > chunkSize) {
                if (buffer != null) {
                    result.add(buffer);
                    buffer = null;
                }
                result.addAll(recursiveSplit(part, separatorIndex + 1));
                continue;
            }

            // 尝试与 buffer 合并（保持同级别的语义边界）
            if (buffer != null) {
                String combined = buffer + separator + part;
                if (combined.length() <= chunkSize) {
                    buffer = combined;
                } else {
                    result.add(buffer);
                    buffer = part;
                }
            } else {
                buffer = part;
            }
        }

        if (buffer != null) {
            result.add(buffer);
        }

        return result;
    }

    /**
     * 兜底：按固定长度硬切分
     */
    private List<String> forceSplitByChar(String text) {
        List<String> result = new ArrayList<>();
        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            result.add(text.substring(start, end));
            start = end;
        }
        return result;
    }

    /**
     * 保护剂量信息，用占位符替换避免被分隔符切断
     */
    private String protectDosage(String text, Map<String, String> protectedBlocks) {
        Matcher matcher = DOSAGE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        int counter = protectedBlocks.size();
        while (matcher.find()) {
            String matched = matcher.group();
            if (protectedBlocks.containsValue(matched)) {
                continue;
            }
            String placeholder = PLACEHOLDER_PREFIX + counter + "__";
            protectedBlocks.put(placeholder, matched);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            counter++;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 保护列表结构，将连续 2 行及以上的列表整体包裹为占位符
     */
    private String protectList(String text, Map<String, String> protectedBlocks) {
        String[] lines = text.split("\n", -1);
        if (lines.length < 2) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int counter = protectedBlocks.size();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            boolean isListItem = NUMBERED_LIST_ITEM.matcher(line).find()
                    || BULLET_LIST_ITEM.matcher(line).find();

            if (isListItem && i + 1 < lines.length) {
                int listStart = i;
                i++;
                while (i < lines.length) {
                    String nextLine = lines[i];
                    boolean nextIsItem = NUMBERED_LIST_ITEM.matcher(nextLine).find()
                            || BULLET_LIST_ITEM.matcher(nextLine).find();
                    boolean nextIsBlank = nextLine.trim().isEmpty();
                    if (nextIsItem) {
                        i++;
                    } else if (nextIsBlank && i + 1 < lines.length
                            && (NUMBERED_LIST_ITEM.matcher(lines[i + 1]).find()
                                || BULLET_LIST_ITEM.matcher(lines[i + 1]).find())) {
                        i++;
                    } else {
                        break;
                    }
                }

                int listEnd = i;
                int consecutiveItems = 0;
                for (int j = listStart; j < listEnd; j++) {
                    if (NUMBERED_LIST_ITEM.matcher(lines[j]).find()
                            || BULLET_LIST_ITEM.matcher(lines[j]).find()) {
                        consecutiveItems++;
                    }
                }

                if (consecutiveItems >= 2) {
                    StringBuilder listBlock = new StringBuilder();
                    for (int j = listStart; j < listEnd; j++) {
                        if (!listBlock.isEmpty()) {
                            listBlock.append("\n");
                        }
                        listBlock.append(lines[j]);
                    }
                    String listText = listBlock.toString();
                    String placeholder = PLACEHOLDER_PREFIX + counter + "__";
                    protectedBlocks.put(placeholder, listText);
                    result.append(placeholder);
                    counter++;
                } else {
                    for (int j = listStart; j < listEnd; j++) {
                        if (!result.isEmpty()) {
                            result.append("\n");
                        }
                        result.append(lines[j]);
                    }
                }
            } else {
                if (!result.isEmpty()) {
                    result.append("\n");
                }
                result.append(line);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 添加滑动窗口重叠：每个 chunk（除首块外）前缀追加前一块尾部的 overlap 字符
     */
    private List<String> addOverlap(List<String> chunks) {
        if (overlap <= 0 || chunks.size() <= 1) {
            return new ArrayList<>(chunks);
        }

        List<String> result = new ArrayList<>();
        result.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String prevChunk = result.get(i - 1);
            String current = chunks.get(i);

            int overlapLen = Math.min(overlap, prevChunk.length());
            String overlapText = prevChunk.substring(prevChunk.length() - overlapLen);
            result.add(overlapText + current);
        }

        return result;
    }

    /**
     * 还原占位符为原始文本
     */
    private List<String> restoreProtections(List<String> chunks, Map<String, String> protectedBlocks) {
        if (protectedBlocks.isEmpty()) {
            return chunks;
        }

        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            String restored = chunk;
            for (Map.Entry<String, String> entry : protectedBlocks.entrySet()) {
                restored = restored.replace(entry.getKey(), entry.getValue());
            }
            result.add(restored);
        }
        return result;
    }
}
