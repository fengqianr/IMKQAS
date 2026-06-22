package com.student.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 医学文档结构感知分片器
 * 先解析文档结构树（章节/子章节/列表项），沿结构边界切分，
 * 每个 chunk 携带完整的层级元数据（章节路径、面包屑、父子兄弟关系等）。
 *
 * @author 系统
 * @version 1.0
 */
public class MedicalDocumentSplitter {

    // ======================== 标题检测正则（按优先级排序） ========================

    /** L1: 【】括号标题 */
    static final Pattern BRACKET_HEADER = Pattern.compile("^【([^】]+)】\\s*$");

    /** L1: Markdown 一级/二级标题 */
    static final Pattern MARKDOWN_H1_H2 = Pattern.compile("^(#{1,2})\\s+(.+)$");

    /** L2: Markdown 三级标题 */
    static final Pattern MARKDOWN_H3 = Pattern.compile("^#{3}\\s+(.+)$");

    /** L1: 中文"第X章/节" */
    static final Pattern CHINESE_CHAPTER = Pattern.compile("^(第[一二三四五六七八九十\\d]+[章节篇部分])\\s*$");

    /** L2: 中文序号标题 "一、概述"/"二、流行病学" */
    static final Pattern CHINESE_NUMBERED = Pattern.compile("^([一二三四五六七八九十])、\\s*(.+)$");

    /** L2: 数字编号 "1. "/"2.1 "/"9. 标题" */
    static final Pattern DIGIT_HEADER = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.?\\s+(.+)$");

    /** L2: 冒号结尾的短标题行，如 "急性发作期治疗（SABA为核心）：" */
    static final Pattern COLON_SUBTITLE = Pattern.compile("^(.{2,50})[：:]\\s*$");

    // ======================== 其他检测正则 ========================

    /** 表格行（多行模式，用于还原后检测） */
    static final Pattern TABLE_ROW_MULTILINE = Pattern.compile("^\\|.+\\|$", Pattern.MULTILINE);

    /** 表格行（单行匹配，用于逐行检测） */
    static final Pattern TABLE_ROW = Pattern.compile("^\\|.+\\|$");

    /** 表格分隔行 */
    static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|[\\s\\-:]+\\|$");

    /** ICD 编码 */
    static final Pattern ICD_CODE = Pattern.compile("\\b([A-Z]\\d{2}(?:\\.\\d{1,2})?)\\b");

    /** 无序列表项 */
    static final Pattern BULLET_ITEM = Pattern.compile("^[•\\-\\*\\◦\\▪]\\s+");

    /** 短独立行（可能是不带标记的章节标题） */
    static final Pattern SHORT_STANDALONE = Pattern.compile("^(.{2,30})$");

    /** 占位符前缀 */
    static final String PH_PREFIX = "__MEDPROT_";

    // ======================== 配置字段 ========================

    private final int chunkSize;
    private final int overlap;
    private final int minChunkSize;
    private final List<String> separators;
    private final boolean sectionHierarchy;
    private final boolean tableProtection;
    private final boolean icdDetection;
    private final boolean siblingContext;
    private final int minSectionHeaderLen;
    private final int minTableRows;
    private final int siblingMergeThreshold;
    private final int maxDepth;
    private final RecursiveTextSplitter fallbackSplitter;

    // ======================== 构造函数 ========================

    public MedicalDocumentSplitter(int chunkSize, int overlap, int minChunkSize,
                                   List<String> separators,
                                   boolean sectionHierarchy, boolean tableProtection,
                                   boolean icdDetection, boolean siblingContext,
                                   int minSectionHeaderLen, int minTableRows,
                                   int siblingMergeThreshold, int maxDepth) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap(" + overlap
                    + ") 不能大于或等于 chunkSize(" + chunkSize + ")");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        this.minChunkSize = minChunkSize;
        this.separators = Collections.unmodifiableList(new ArrayList<>(separators));
        this.sectionHierarchy = sectionHierarchy;
        this.tableProtection = tableProtection;
        this.icdDetection = icdDetection;
        this.siblingContext = siblingContext;
        this.minSectionHeaderLen = minSectionHeaderLen;
        this.minTableRows = minTableRows;
        this.siblingMergeThreshold = siblingMergeThreshold;
        this.maxDepth = maxDepth;
        this.fallbackSplitter = new RecursiveTextSplitter(chunkSize, overlap, minChunkSize, separators);
    }

    // ======================== 公开 API ========================

    /**
     * 按医学文档结构分片
     * @param text 原始文本
     * @return 分片后的文本列表
     */
    public List<String> split(String text) {
        List<SegmentInfo> infos = splitWithMetadata(text);
        List<String> result = new ArrayList<>();
        for (SegmentInfo info : infos) {
            result.add(info.text);
        }
        return result;
    }

    /**
     * 按医学文档结构分片，返回携带层级元数据的结果
     * @param text 原始文本
     * @return 分片结果列表（含元数据）
     */
    public List<SegmentInfo> splitWithMetadata(String text) {
        if (text == null) {
            throw new IllegalArgumentException("文本不能为 null");
        }
        if (text.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, ProtectedBlock> protectedBlocks = new LinkedHashMap<>();

        // Phase 1: 预处理保护
        String processed = text;
        processed = protectDosage(processed, protectedBlocks);
        processed = protectTables(processed, protectedBlocks);

        // Phase 2: 文档结构解析
        SectionNode root = sectionHierarchy
                ? parseDocumentStructure(processed)
                : createFlatRoot(processed);

        // Phase 3: 沿结构树切分
        List<SegmentInfo> segments = chunkBySections(root, processed);

        // Phase 4: 同级小chunk合并
        segments = mergeSmallSiblings(segments);

        // Phase 5: 滑动窗口重叠
        segments = addOverlap(segments);

        // Phase 6: 还原占位符 + 过滤
        segments = restoreAndFilter(segments, protectedBlocks, text);

        return segments;
    }

    // ======================== Phase 1: 预处理保护 ========================

    /**
     * 保护剂量信息（复用 RecursiveTextSplitter 的剂量模式）
     */
    private String protectDosage(String text, Map<String, ProtectedBlock> blocks) {
        Matcher matcher = RecursiveTextSplitter.DOSAGE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        int counter = blocks.size();
        while (matcher.find()) {
            String matched = matcher.group();
            String placeholder = PH_PREFIX + counter + "__";
            blocks.put(placeholder, new ProtectedBlock(matched, matcher.start()));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            counter++;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 保护表格结构，将连续管道表格行整体替换为占位符
     */
    private String protectTables(String text, Map<String, ProtectedBlock> blocks) {
        if (!tableProtection) {
            return text;
        }

        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        int counter = blocks.size();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];
            if (TABLE_ROW.matcher(line).matches()) {
                // 检查是否为表格块起始
                int tableStart = i;
                int pipeCount = countPipeChars(line);
                int dataRowCount = 1;
                i++;

                while (i < lines.length) {
                    String nextLine = lines[i];
                    if (TABLE_ROW.matcher(nextLine).matches()) {
                        if (countPipeChars(nextLine) == pipeCount) {
                            dataRowCount++;
                            i++;
                        } else {
                            break;
                        }
                    } else if (TABLE_SEPARATOR.matcher(nextLine).matches()) {
                        i++;
                    } else {
                        break;
                    }
                }

                if (dataRowCount >= minTableRows) {
                    // 收集整个表格块
                    StringBuilder tableBlock = new StringBuilder();
                    for (int j = tableStart; j < i; j++) {
                        if (!tableBlock.isEmpty()) {
                            tableBlock.append("\n");
                        }
                        tableBlock.append(lines[j]);
                    }
                    String tableText = tableBlock.toString();
                    String placeholder = PH_PREFIX + counter + "__";
                    blocks.put(placeholder, new ProtectedBlock(tableText, -1));
                    result.append(placeholder);
                    counter++;
                } else {
                    // 不足最小行数，不保护，原样输出
                    for (int j = tableStart; j < i; j++) {
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

    private int countPipeChars(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == '|') count++;
        }
        return count;
    }

    // ======================== Phase 2: 文档结构解析 ========================

    /**
     * 解析文档结构，构建 SectionNode 树
     */
    SectionNode parseDocumentStructure(String text) {
        List<HeaderMatch> headers = detectHeaders(text);

        if (headers.isEmpty()) {
            return createFlatRoot(text);
        }

        SectionNode root = buildTree(headers, text.length());

        // 提取文档主题（第一个标题作为文档主题）
        String docTopic = extractDocumentTopic(headers, text);

        // 计算每个节点的结束位置
        computeEndPositions(root, text.length());

        // 注入兄弟关系和文档元数据
        injectSiblingInfo(root);
        injectDocumentMeta(root, docTopic);

        return root;
    }

    /**
     * 扫描全文，检测所有标题
     */
    List<HeaderMatch> detectHeaders(String text) {
        List<HeaderMatch> matches = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int pos = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.length() < minSectionHeaderLen) {
                pos += line.length() + 1;
                continue;
            }

            HeaderMatch match = null;

            // 按优先级尝试匹配（最高优先级的匹配生效）
            Matcher bracketMatcher = BRACKET_HEADER.matcher(trimmed);
            if (bracketMatcher.matches()) {
                match = new HeaderMatch(pos, pos + line.length(),
                        bracketMatcher.group(1), "BRACKET");
            }

            if (match == null) {
                Matcher mdMatcher = MARKDOWN_H1_H2.matcher(trimmed);
                if (mdMatcher.matches() && mdMatcher.group(2).length() >= minSectionHeaderLen) {
                    match = new HeaderMatch(pos, pos + line.length(),
                            mdMatcher.group(2), "MARKDOWN_H1H2");
                }
            }

            if (match == null) {
                Matcher chMatcher = CHINESE_CHAPTER.matcher(trimmed);
                if (chMatcher.matches()) {
                    match = new HeaderMatch(pos, pos + line.length(),
                            chMatcher.group(1), "CHINESE_CHAPTER");
                }
            }

            if (match == null) {
                Matcher cnMatcher = CHINESE_NUMBERED.matcher(trimmed);
                if (cnMatcher.matches()) {
                    match = new HeaderMatch(pos, pos + line.length(),
                            trimmed, "CHINESE_NUMBERED");
                }
            }

            if (match == null) {
                Matcher digMatcher = DIGIT_HEADER.matcher(trimmed);
                if (digMatcher.matches() && digMatcher.group(2).length() >= minSectionHeaderLen) {
                    match = new HeaderMatch(pos, pos + line.length(),
                            digMatcher.group(2), "DIGIT_HEADER");
                }
            }

            if (match == null) {
                Matcher colMatcher = COLON_SUBTITLE.matcher(trimmed);
                if (colMatcher.matches() && !isBulletItem(trimmed)) {
                    match = new HeaderMatch(pos, pos + line.length(),
                            colMatcher.group(1), "COLON_SUBTITLE");
                }
            }

            // 短独立行（兜底检测：短行 + 下一个非空行是缩进或列表项）
            if (match == null && trimmed.length() >= 2 && trimmed.length() <= 30
                    && !isBulletItem(trimmed)
                    && !trimmed.contains("：") && !trimmed.contains(":")) {
                boolean followedByContent = false;
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.isEmpty()) continue;
                    followedByContent = isBulletItem(next)
                            || next.startsWith("  ") || next.startsWith("\t");
                    break;
                }
                if (followedByContent) {
                    match = new HeaderMatch(pos, pos + line.length(),
                            trimmed, "SHORT_STANDALONE");
                }
            }

            if (match != null) {
                match.level = assignLevel(match);
                matches.add(match);
            }

            pos += line.length() + 1;
        }

        return matches;
    }

    private boolean isBulletItem(String line) {
        return BULLET_ITEM.matcher(line).find();
    }

    /**
     * 根据标题模式分配层级深度
     */
    int assignLevel(HeaderMatch match) {
        return switch (match.patternType) {
            case "BRACKET", "MARKDOWN_H1H2", "CHINESE_CHAPTER" -> 1;
            case "CHINESE_NUMBERED", "DIGIT_HEADER" -> 2;
            case "COLON_SUBTITLE", "MARKDOWN_H3" -> 2;
            case "SHORT_STANDALONE" -> 1;
            default -> 2;
        };
    }

    /**
     * 从标题列表构建父子结构树
     */
    SectionNode buildTree(List<HeaderMatch> headers, int textLength) {
        SectionNode root = new SectionNode(-1, 0, textLength, "__ROOT__", null, null);
        Deque<SectionNode> stack = new ArrayDeque<>();
        stack.push(root);

        for (int i = 0; i < headers.size(); i++) {
            HeaderMatch h = headers.get(i);
            int nextStart = (i + 1 < headers.size()) ? headers.get(i + 1).startPos : textLength;

            SectionNode node = new SectionNode(h.level, h.startPos, nextStart,
                    h.title, h.patternType, null);

            // 找到父节点：栈顶 level < 当前 level
            while (stack.size() > 1 && stack.peek().level >= h.level) {
                stack.pop();
            }
            SectionNode parent = stack.peek();
            node.parent = parent;
            parent.children.add(node);
            stack.push(node);
        }

        return root;
    }

    /**
     * 递归计算各节点的结束位置
     * 自底向上：父节点的 end 覆盖到最后一个子节点，子节点之间不重叠
     */
    void computeEndPositions(SectionNode node, int textLength) {
        // 递归处理子节点
        for (SectionNode child : node.children) {
            computeEndPositions(child, textLength);
        }

        // 有子节点时，父节点的 end 扩展到最后一个子节点的 end
        if (!node.children.isEmpty()) {
            node.endChar = node.children.get(node.children.size() - 1).endChar;
        }

        // 调整子节点的 end：到下一个兄弟的 start，或父节点的 end
        for (int i = 0; i < node.children.size(); i++) {
            SectionNode child = node.children.get(i);
            if (i + 1 < node.children.size()) {
                child.endChar = node.children.get(i + 1).startChar;
            } else {
                child.endChar = node.endChar;
            }
        }
    }

    /**
     * 注入兄弟关系
     */
    void injectSiblingInfo(SectionNode node) {
        List<SectionNode> children = node.children;
        for (int i = 0; i < children.size(); i++) {
            SectionNode child = children.get(i);
            if (i > 0 && siblingContext) {
                child.prevSiblingTitle = children.get(i - 1).title;
            }
            if (i + 1 < children.size() && siblingContext) {
                child.nextSiblingTitle = children.get(i + 1).title;
            }
            injectSiblingInfo(child);
        }
    }

    /**
     * 向所有节点注入文档级元数据
     */
    void injectDocumentMeta(SectionNode node, String docTopic) {
        node.documentTopic = docTopic;
        for (SectionNode child : node.children) {
            injectDocumentMeta(child, docTopic);
        }
    }

    /**
     * 从标题列表中提取文档主题
     */
    String extractDocumentTopic(List<HeaderMatch> headers, String text) {
        if (headers.isEmpty()) {
            return null;
        }
        // 优先从第一个标题之前的前导文本中提取
        int firstHeaderPos = headers.get(0).startPos;
        if (firstHeaderPos > 0) {
            String preamble = text.substring(0, Math.min(firstHeaderPos, text.length())).trim();
            String[] lines = preamble.split("\n");
            for (String line : lines) {
                String t = line.trim();
                if (!t.isEmpty() && t.length() >= 2 && t.length() <= 80) {
                    return t.replaceFirst("^\\d+[\\.、\\s]+", "").trim();
                }
            }
        }
        // 前导文本为空时，使用第一个标题作为文档主题
        return headers.get(0).title;
    }

    /**
     * 当没有检测到任何标题时，创建平坦根节点
     */
    SectionNode createFlatRoot(String text) {
        SectionNode root = new SectionNode(-1, 0, text.length(), "__ROOT__", null, null);
        SectionNode body = new SectionNode(0, 0, text.length(), null, null, root);
        root.children.add(body);
        return root;
    }

    // ======================== Phase 3: 沿结构树切分 ========================

    /**
     * 沿 SectionNode 树深度优先切分
     */
    List<SegmentInfo> chunkBySections(SectionNode root, String text) {
        List<SegmentInfo> result = new ArrayList<>();
        chunkNodeRecursive(root, text, result);
        return result;
    }

    private void chunkNodeRecursive(SectionNode node, String text, List<SegmentInfo> result) {
        if (node.children.isEmpty()) {
            // 叶节点
            result.addAll(chunkLeafContent(node, text));
            return;
        }

        // 处理 node 自身在第一个子节点之前的内容（前导文本）
        // root 节点（level<0）的前导文本也要处理，用无标题的 SegmentInfo
        if (!node.children.isEmpty()) {
            SectionNode firstChild = node.children.get(0);
            if (firstChild.startChar > node.startChar) {
                String preamble = text.substring(node.startChar, firstChild.startChar).trim();
                if (!preamble.isEmpty()) {
                    SegmentInfo info = new SegmentInfo(preamble, node.startChar, firstChild.startChar);
                    if (node.level >= 0) {
                        attachMetadata(info, node);
                    }
                    // 注入文档级元数据
                    info.documentTopic = node.documentTopic;
                    if (preamble.length() > chunkSize) {
                        result.addAll(splitOversized(preamble, node, node.startChar));
                    } else {
                        result.add(info);
                    }
                }
            }
        }

        // 递归处理子节点
        for (int i = 0; i < node.children.size(); i++) {
            SectionNode child = node.children.get(i);

            // 处理子节点之间的间隙文本
            if (i > 0) {
                SectionNode prev = node.children.get(i - 1);
                if (child.startChar > prev.endChar) {
                    String gap = text.substring(prev.endChar, child.startChar).trim();
                    if (!gap.isEmpty()) {
                        SegmentInfo info = new SegmentInfo(gap, prev.endChar, child.startChar);
                        attachMetadata(info, node);
                        result.add(info);
                    }
                }
            }

            chunkNodeRecursive(child, text, result);
        }
    }

    /**
     * 切分叶节点内容
     */
    List<SegmentInfo> chunkLeafContent(SectionNode node, String text) {
        String content;
        int actualStart = node.startChar;
        int actualEnd;

        if (node.level > 0 && node.title != null) {
            // 叶节点：从标题行开始到 endChar
            content = text.substring(node.startChar, Math.min(node.endChar, text.length()));
            actualEnd = Math.min(node.endChar, text.length());
        } else {
            content = text.substring(node.startChar, Math.min(node.endChar, text.length()));
            actualEnd = Math.min(node.endChar, text.length());
        }

        content = content.trim();
        if (content.isEmpty()) {
            return Collections.emptyList();
        }

        List<SegmentInfo> result = new ArrayList<>();

        if (content.length() <= chunkSize) {
            SegmentInfo info = new SegmentInfo(content, actualStart, actualEnd);
            attachMetadata(info, node);
            result.add(info);
        } else {
            result.addAll(splitOversized(content, node, actualStart));
        }

        return result;
    }

    /**
     * 超长内容回退到 RecursiveTextSplitter
     */
    private List<SegmentInfo> splitOversized(String content, SectionNode node, int offset) {
        List<String> subChunks = fallbackSplitter.split(content);
        List<SegmentInfo> result = new ArrayList<>();
        int pos = offset;
        for (String subChunk : subChunks) {
            String trimmed = subChunk.trim();
            if (trimmed.length() < minChunkSize) {
                continue;
            }
            SegmentInfo info = new SegmentInfo(trimmed, pos, pos + subChunk.length());
            attachMetadata(info, node);
            result.add(info);
            pos += subChunk.length();
        }
        return result;
    }

    // ======================== Phase 4: 同级合并 ========================

    /**
     * 合并相邻的过小 chunk（同一章节下的小碎片合并，保持语义组完整）
     */
    List<SegmentInfo> mergeSmallSiblings(List<SegmentInfo> segments) {
        if (segments.size() <= 1 || siblingMergeThreshold <= 0) {
            return segments;
        }

        List<SegmentInfo> result = new ArrayList<>();
        SegmentInfo buffer = null;

        for (SegmentInfo seg : segments) {
            if (seg.text.length() >= siblingMergeThreshold) {
                if (buffer != null) {
                    result.add(buffer);
                    buffer = null;
                }
                result.add(seg);
            } else {
                if (buffer != null
                        && Objects.equals(buffer.sectionPath, seg.sectionPath)
                        && buffer.text.length() + seg.text.length() <= chunkSize) {
                    // 合并到 buffer
                    buffer.text = buffer.text + "\n" + seg.text;
                    buffer.endChar = seg.endChar;
                } else {
                    if (buffer != null) {
                        result.add(buffer);
                    }
                    buffer = seg;
                }
            }
        }
        if (buffer != null) {
            result.add(buffer);
        }

        return result;
    }

    // ======================== Phase 5 + 6: 重叠 + 还原/过滤 ========================

    /**
     * 滑动窗口重叠（与 RecursiveTextSplitter 一致）
     */
    List<SegmentInfo> addOverlap(List<SegmentInfo> segments) {
        if (overlap <= 0 || segments.size() <= 1) {
            return new ArrayList<>(segments);
        }

        List<SegmentInfo> result = new ArrayList<>();
        result.add(segments.get(0));

        for (int i = 1; i < segments.size(); i++) {
            SegmentInfo prev = result.get(i - 1);
            SegmentInfo current = segments.get(i);

            int overlapLen = Math.min(overlap, prev.text.length());
            String overlapText = prev.text.substring(prev.text.length() - overlapLen);

            SegmentInfo overlapped = new SegmentInfo(
                    overlapText + current.text,
                    current.startChar, current.endChar);
            copyMetadata(current, overlapped);
            result.add(overlapped);
        }

        return result;
    }

    /**
     * 还原占位符 + 过滤过短 chunk + trim
     */
    List<SegmentInfo> restoreAndFilter(List<SegmentInfo> segments,
                                       Map<String, ProtectedBlock> blocks, String originalText) {
        List<SegmentInfo> result = new ArrayList<>();
        int searchOffset = 0;

        for (SegmentInfo seg : segments) {
            // 还原占位符
            String restored = seg.text;
            for (Map.Entry<String, ProtectedBlock> entry : blocks.entrySet()) {
                restored = restored.replace(entry.getKey(), entry.getValue().originalText);
            }
            restored = restored.trim();

            if (restored.length() < minChunkSize) {
                continue;
            }

            SegmentInfo restoredSeg = new SegmentInfo(restored, seg.startChar, seg.endChar);
            copyMetadata(seg, restoredSeg);

            // 在原始文本中重新定位
            int found = originalText.indexOf(restored, searchOffset);
            if (found >= 0) {
                restoredSeg.startChar = found;
                restoredSeg.endChar = found + restored.length();
                searchOffset = found + restored.length();
            }

            // 检测表格标记（至少 minTableRows 行管道行才标记为表格）
            if (tableProtection) {
                long pipeRowCount = restored.lines()
                        .filter(line -> TABLE_ROW.matcher(line).matches())
                        .count();
                restoredSeg.isTable = pipeRowCount >= minTableRows;
            }

            // 提取 ICD 编码
            if (icdDetection) {
                restoredSeg.icdCodes = extractIcdCodes(restored);
            }

            result.add(restoredSeg);
        }

        return result;
    }

    // ======================== 元数据构建 ========================

    /**
     * 将 SectionNode 的层级信息注入 SegmentInfo
     */
    void attachMetadata(SegmentInfo info, SectionNode node) {
        info.sectionLevel = node.level;
        info.sectionTitle = node.title;
        info.paragraphNumber = node.paragraphNumber;
        info.sectionPath = buildSectionPath(node);
        info.breadcrumb = buildBreadcrumb(node);
        info.parentSection = (node.parent != null && node.parent.level >= 0) ? node.parent.title : null;
        info.rootSection = findRootSection(node);
        info.prevSibling = node.prevSiblingTitle;
        info.nextSibling = node.nextSiblingTitle;
        info.documentTopic = node.documentTopic;
        info.documentSectionCount = countSections(node);
    }

    void copyMetadata(SegmentInfo from, SegmentInfo to) {
        to.sectionLevel = from.sectionLevel;
        to.sectionTitle = from.sectionTitle;
        to.paragraphNumber = from.paragraphNumber;
        to.sectionPath = from.sectionPath;
        to.breadcrumb = from.breadcrumb;
        to.parentSection = from.parentSection;
        to.rootSection = from.rootSection;
        to.prevSibling = from.prevSibling;
        to.nextSibling = from.nextSibling;
        to.documentTopic = from.documentTopic;
        to.documentSectionCount = from.documentSectionCount;
        to.isTable = from.isTable;
        to.icdCodes = from.icdCodes;
    }

    /**
     * 构建 JSON 数组形式的章节路径
     */
    String buildSectionPath(SectionNode node) {
        List<String> path = new ArrayList<>();
        SectionNode current = node;
        while (current != null && current.level >= 0 && current.title != null) {
            path.add(0, current.title);
            current = current.parent;
        }
        if (path.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escapeJson(path.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 构建面包屑路径（文档主题在最前，然后从根章节到当前章节）
     */
    String buildBreadcrumb(SectionNode node) {
        List<String> parts = new ArrayList<>();
        // 收集从根到当前节点的路径
        SectionNode current = node;
        while (current != null && current.level >= 0 && current.title != null) {
            parts.add(0, current.title);
            current = current.parent;
        }
        // 文档主题放在最前面
        if (node.documentTopic != null) {
            parts.add(0, node.documentTopic);
        }
        return String.join(" > ", parts);
    }

    String findRootSection(SectionNode node) {
        SectionNode current = node;
        while (current.parent != null && current.parent.level >= 0) {
            current = current.parent;
        }
        return (current != null && current.title != null && current.level >= 0)
                ? current.title : null;
    }

    int countSections(SectionNode node) {
        // 找到根节点
        SectionNode root = node;
        while (root.parent != null) {
            root = root.parent;
        }
        return countLevel1Sections(root);
    }

    private int countLevel1Sections(SectionNode node) {
        int count = 0;
        for (SectionNode child : node.children) {
            if (child.level == 1) count++;
        }
        return count;
    }

    List<String> extractIcdCodes(String text) {
        List<String> codes = new ArrayList<>();
        Matcher matcher = ICD_CODE.matcher(text);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ======================== 内部类 ========================

    /** 标题匹配结果 */
    static class HeaderMatch {
        final int startPos;
        final int endPos;
        final String title;
        final String patternType;
        int level;

        HeaderMatch(int startPos, int endPos, String title, String patternType) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.title = title;
            this.patternType = patternType;
        }
    }

    /** 文档结构树节点 */
    static class SectionNode {
        int level;
        int startChar;
        int endChar;
        String title;
        String subtitle;
        String paragraphNumber;
        String titlePattern;
        SectionNode parent;
        List<SectionNode> children = new ArrayList<>();
        String prevSiblingTitle;
        String nextSiblingTitle;
        String documentTopic;

        SectionNode(int level, int startChar, int endChar,
                    String title, String titlePattern, SectionNode parent) {
            this.level = level;
            this.startChar = startChar;
            this.endChar = endChar;
            this.title = title;
            this.titlePattern = titlePattern;
            this.parent = parent;
        }
    }

    /** 分片结果（含层级元数据） */
    public static class SegmentInfo {
        String text;
        int startChar;
        int endChar;
        String sectionPath;
        int sectionLevel;
        String sectionTitle;
        String parentSection;
        String rootSection;
        String paragraphNumber;
        String prevSibling;
        String nextSibling;
        String breadcrumb;
        List<String> icdCodes = Collections.emptyList();
        boolean isTable;
        String documentTopic;
        int documentSectionCount;

        SegmentInfo(String text, int startChar, int endChar) {
            this.text = text;
            this.startChar = startChar;
            this.endChar = endChar;
        }

        public String getText() { return text; }
        public int getStartChar() { return startChar; }
        public int getEndChar() { return endChar; }
        public String getSectionPath() { return sectionPath; }
        public int getSectionLevel() { return sectionLevel; }
        public String getSectionTitle() { return sectionTitle; }
        public String getParentSection() { return parentSection; }
        public String getRootSection() { return rootSection; }
        public String getParagraphNumber() { return paragraphNumber; }
        public String getPrevSibling() { return prevSibling; }
        public String getNextSibling() { return nextSibling; }
        public String getBreadcrumb() { return breadcrumb; }
        public List<String> getIcdCodes() { return icdCodes; }
        public boolean isTable() { return isTable; }
        public String getDocumentTopic() { return documentTopic; }
        public int getDocumentSectionCount() { return documentSectionCount; }
    }

    /** 受保护的文本块 */
    static class ProtectedBlock {
        final String originalText;
        final int originalPosition;

        ProtectedBlock(String originalText, int originalPosition) {
            this.originalText = originalText;
            this.originalPosition = originalPosition;
        }
    }
}
