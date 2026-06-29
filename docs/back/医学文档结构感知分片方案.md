# 医学文档结构感知分片方案（层级元数据增强版）

## Context

当前 `RecursiveTextSplitter` 按通用分隔符递归切分，对医学文档结构无感知。以儿童支气管哮喘文档为例，存在以下问题：

- **章节边界被破坏**：【病因与发病机制】【治疗方案】等标题与正文被切开
- **表格被撕裂**：管道符表格被当作普通文本处理
- **层级信息完全丢失**：检索时无法区分 "这段来自【治疗方案 > 急性发作期】" 还是 "这段来自【预防措施】"
- **生成时无上下文锚点**：LLM 拿到一个 chunk 不知道它在文档中的位置，无法补回章节上下文

## 方案概述

新增 `medical` 分片策略。与现有 `semantic` 策略的核心区别：**不只按边界切分，而是先解析文档结构树，再切分，最终每个 chunk 都携带完整的层级元数据**。

## 层级元数据设计

### 结构树模型

以支气管哮喘文档为例，解析后的结构树：

```
(level 0, paraNum: "9")
├── [导言段] 儿童支气管哮喘 — 别名、ICD编码(J45)、概述
│
├── (level 1, title: "病因与发病机制")
│   ├── • 遗传因素：过敏体质家族史；多基因遗传
│   ├── • 过敏原暴露：尘螨、动物皮屑、蟑螂...
│   ├── • 促发因素：病毒感染(占80%)、运动、冷空气...
│   └── • 发病机制：Th2介导慢性气道炎症 → 高反应性 → 可逆阻塞
│
├── (level 1, title: "临床表现与诊断要点")
│   ├── [表格] 症状/体征 ↔ 诊断要点 (含哮鸣音、三凹征、桶状胸等)
│   └── [说明段]
│
├── (level 1, title: "诊断标准")
│   ├── • ≥6岁：肺功能FEV₁/FVC<80%，支气管舒张试验阳性(≥12%)
│   ├── • <6岁：依赖临床诊断(API指数阳性) + 治疗性诊断
│   ├── • FeNO升高(>25ppb)提示嗜酸性气道炎症
│   └── • 过敏原皮肤点刺或特异性IgE检测
│
├── (level 1, title: "治疗方案")
│   ├── (level 2, title: "急性发作期治疗", subtitle: "SABA为核心")
│   │   ├── • 轻中度：沙丁胺醇雾化0.15mg/kg(最小2.5mg)，每20min×3次
│   │   ├── • 重度：异丙托溴铵联合SABA；甲泼尼龙1~2mg/kg静注
│   │   └── • 危重：氦氧混合气、MgSO₄静脉(25~75mg/kg)，必要时机械通气
│   ├── (level 2, title: "长期控制治疗", subtitle: "阶梯方案")
│   │   ├── • 第一阶：按需SABA（轻度间歇）
│   │   ├── • 第二阶：低剂量ICS（布地奈德100μg bid）—— 基石
│   │   ├── • 第三阶：ICS+LABA(≥5岁)；或中剂量ICS(<5岁)
│   │   └── • 第四阶：中高剂量ICS+LABA+孟鲁司特；重症考虑奥马珠单抗
│   └── (level 2, title: "吸入技术教育")
│       └── 正确使用MDI+储雾罐（婴幼儿必须储雾罐+面罩）
│
├── (level 1, title: "预防措施")
│   ├── • 尘螨控制：防螨床垫、热水洗涤(>60°C)、减少毛绒玩具
│   ├── • 禁止室内吸烟，减少二手烟/三手烟暴露
│   ├── • 提倡自然分娩和母乳喂养（减少特应性风险）
│   └── • 过敏原特异性免疫治疗（适合>5岁尘螨过敏儿童）
│
└── (level 1, title: "紧急处理要点")
    ├── • SpO₂<92%立即氧疗+SABA雾化
    ├── • 重度发作SABA 3次无效需立即就医
    ├── • 沉默肺（哮鸣音消失+呼吸困难加重）是极危重信号
    └── • 建立哮喘行动计划，家长须掌握家庭急救步骤
```

### 每个 Chunk 携带的元数据

```json
{
  "start_char": 0,
  "end_char": 487,
  "section_path": ["治疗方案", "急性发作期治疗（SABA为核心）"],
  "section_level": 2,
  "section_title": "急性发作期治疗（SABA为核心）",
  "parent_section": "治疗方案",
  "root_section": "治疗方案",
  "paragraph_number": "•",
  "prev_sibling": "病因与发病机制",
  "next_sibling": "长期控制治疗（阶梯方案）",
  "depth": 2,
  "breadcrumb": "治疗方案 > 急性发作期治疗（SABA为核心）",
  "icd_codes": ["J45"],
  "is_table": false,
  "document_topic": "儿童支气管哮喘",
  "document_section_count": 7
}
```

### 元数据的使用场景

**场景 1 — 检索时按层级过滤**
```
用户问："哮喘急性发作怎么用药？"
→ 检索时加权 section_title 匹配 "急性发作期治疗" 的 chunk
→ 同时拉取兄弟 chunk（轻中度/重度/危重）拼接完整方案
```

**场景 2 — 生成时补回上下文**
```
检索到 chunk "轻中度：沙丁胺醇雾化0.15mg/kg"
→ 从 breadcrumb 自动生成前缀：
  "[来源：儿童支气管哮喘 > 治疗方案 > 急性发作期治疗（SABA为核心）]"
→ LLM 知道这是阶梯治疗的一部分，不会断章取义
```

**场景 3 — 父子上下文注入**
```
chunk 较小只包含子级内容时
→ 从 parent_section 字段拉取父标题作为上下文头
→ 确保 LLM 看到 "以下内容位于【治疗方案】章节下"
```

## 修改清单

### 1. 新建 `src/main/java/com/student/utils/MedicalDocumentSplitter.java`

**核心数据结构：**

```java
// 文档结构树节点
class SectionNode {
    int level;                    // 层级深度（0=文档标题, 1=一级章节, 2=二级...）
    String title;                 // 章节标题文本
    String subtitle;              // 副标题（如 "SABA为核心"）
    String paragraphNumber;       // 段落编号（如 "9", "一", "2.1", "•"）
    String titlePattern;          // 匹配到的标题模式类型：BRACKET / MARKDOWN / NUMBERED / BOLD_TEXT
    int startChar, endChar;       // 在原文本中的起止位置
    SectionNode parent;           // 父节点
    List<SectionNode> children;   // 子节点
}

// 分片结果（增强版）
class SegmentInfo {
    String text;
    int startChar, endChar;
    String sectionPath;           // JSON数组: ["治疗方案", "急性发作期治疗"]
    int sectionLevel;             // 层级深度
    String sectionTitle;          // 当前所在章节标题
    String parentSection;         // 父章节标题
    String rootSection;           // 根章节标题
    String paragraphNumber;       // 段落编号
    String prevSibling;           // 前一个兄弟章节标题
    String nextSibling;           // 后一个兄弟章节标题
    String breadcrumb;            // 面包屑路径
    List<String> icdCodes;        // ICD编码
    boolean isTable;              // 是否包含表格
    // 文档级元数据（每个chunk都携带）
    String documentTopic;         // 文档主题（从文档标题提取）
    int documentSectionCount;     // 文档总章节数
}
```

**分片流水线（6阶段，比初版多一个结构解析阶段）：**

```
Phase 1: 预处理保护
  ├── 剂量正则占位（复用 RecursiveTextSplitter.DOSAGE_PATTERN）
  └── 表格区块检测 + 占位

Phase 2: 文档结构解析 ★核心新增★
  ├── 扫描全文，检测所有标题模式
  │   优先级: 【...】 > Markdown ##/### > 编号段落(一、/1.1) > 粗体子标题
  ├── 构建 SectionNode 树（parent/children 关系）
  ├── 计算每个节点的层级深度、段落编号
  └── 计算兄弟关系（prevSibling / nextSibling）

Phase 3: 章节级切分
  ├── 沿结构树深度优先遍历
  ├── 叶节点内容 ≤ chunkSize → 整个节点作为一个 chunk
  ├── 叶节点内容 > chunkSize → 递归降级到 RecursiveTextSplitter
  └── 每个 chunk 注入层级元数据（从 SectionNode 树提取）

Phase 4: 同级合并
  ├── 相邻的同层级小 chunk 尝试合并（如连续的 bullet points）
  └── 合并后不超过 chunkSize 则合并，保持语义组完整

Phase 5: 重叠 + 还原
  ├── 滑动窗口重叠（与 RecursiveTextSplitter 一致）
  └── 还原剂量/表格占位符

Phase 6: 过滤
  └── 丢弃 < minChunkSize 的 chunk，trim
```

**结构解析的关键算法：**

标题检测正则（按优先级排序）：

| 优先级 | 模式 | 正则 | 示例 |
|--------|------|------|------|
| L1 | `【】` 括号标题 | `^【([^】]+)】` | 【治疗方案】 |
| L2 | Markdown 标题 | `^(#{1,3})\s+(.+)$` | ## 急性发作期治疗 |
| L3 | 中文编号章节 | `^第[一二三四五六七八九十\d]+[章节]` | 第九章 |
| L4 | 中文序号标题 | `^[一二三四五六七八九十]、` | 一、概述 |
| L5 | 数字编号标题 | `^\d+(\.\d+)*\s+\S` | 2.1 流行病学 |
| L6 | 粗体/强调子标题 | 行首短文本（<30字），后紧跟换行+缩进内容 | 急性发作期治疗（SABA为核心）： |

层级分配规则：
- 文档标题（首个 `\d+\. ` 或 `第\d+章`）→ level 0
- `【...】` → level 1
- `##` 或 `一、` 或 `1.` → level 2
- `###` 或 `(1)` 或 `•` → level 3
- 嵌套在父标题下的列表项保持相对层级

### 2. 修改 `src/main/java/com/student/config/RagConfig.java`

在 `ChunkConfig` 内部类中新增 `MedicalChunkConfig`：

```java
@Data
public static class MedicalChunkConfig {
    /** 是否启用章节层级解析 */
    private boolean sectionHierarchy = true;
    /** 是否保护表格 */
    private boolean tableProtection = true;
    /** 是否提取ICD编码 */
    private boolean icdDetection = true;
    /** 是否注入兄弟章节上下文 */
    private boolean siblingContext = true;
    /** 最小标题长度（字符） */
    private int minSectionHeaderLength = 2;
    /** 最小表格行数 */
    private int minTableRows = 2;
    /** 同级小chunk合并阈值（字符，低于此值的相邻兄弟chunk尝试合并） */
    private int siblingMergeThreshold = 150;
    /** 最大层级深度（超过此深度的标题不再拆分新节点） */
    private int maxDepth = 5;
}
```

在 `ChunkConfig` 中添加：`private MedicalChunkConfig medical = new MedicalChunkConfig();`

### 3. 修改 `DocumentProcessorServiceImpl.java`

- `chunkText()` 方法新增 `"medical"` 策略分支
- 新增 `buildChunksWithMetadata()` 方法构造增强 metadata JSON
- JSON 结构如上面的元数据示例

### 4. 修改 `src/main/resources/application.yml`

```yaml
chunk:
  strategy: semantic          # 默认不变
  medical:
    section-hierarchy: true
    table-protection: true
    icd-detection: true
    sibling-context: true
    min-section-header-length: 2
    min-table-rows: 2
    sibling-merge-threshold: 150
    max-depth: 5
```

### 5. 新建 `src/test/java/com/student/utils/MedicalDocumentSplitterTest.java`

除基础测试外，重点覆盖层级元数据：

| 测试用例 | 验证点 |
|----------|--------|
| `testHierarchy_breadcrumb` | 面包屑路径正确：`治疗方案 > 急性发作期治疗` |
| `testHierarchy_parentChild` | parent/children 关系正确构建 |
| `testHierarchy_siblingLinks` | prevSibling / nextSibling 正确 |
| `testHierarchy_deepNesting` | 4层嵌套（章 > 节 > 小节 > 列表项）元数据不丢失 |
| `testHierarchy_sectionCount` | documentSectionCount 准确 |
| `testHierarchy_siblingMerge` | 同层级小 bullet points 合并为一个chunk |
| `testMetadata_jsonFormat` | 元数据 JSON 格式正确可解析 |

## 对你的文档示例的分片效果

```
Chunk #0 (level=0, breadcrumb="儿童支气管哮喘")
  "哮喘是儿童最常见的慢性呼吸道疾病，以反复发作性喘息、咳嗽为特征..."
  metadata: { section_level: 0, paragraph_number: "9",
              document_topic: "儿童支气管哮喘", icd_codes: ["J45"] }

Chunk #1 (level=1, breadcrumb="儿童支气管哮喘 > 病因与发病机制")
  "• 遗传因素：过敏体质（特应性）家族史；多基因遗传\n
   • 过敏原暴露：尘螨（最主要）、动物皮屑、蟑螂、花粉...\n
   • 促发因素：病毒感染（占儿童哮喘发作80%）、运动、冷空气...\n
   • 发病机制：Th2介导的慢性气道炎症 → 气道高反应性 → 可逆性气道阻塞"
  metadata: { section_path: ["病因与发病机制"], section_level: 1,
              parent_section: null, root_section: "病因与发病机制",
              prev_sibling: null, next_sibling: "临床表现与诊断要点" }

Chunk #2 (level=1, breadcrumb="儿童支气管哮喘 > 临床表现与诊断要点")
  "| 症状/体征 | 诊断要点 |\n|------------|----------|\n
   | 反复发作性喘息（以呼气性为主）| 听诊可闻及双肺广泛哮鸣音 |\n..."
  metadata: { section_path: ["临床表现与诊断要点"], is_table: true,
              prev_sibling: "病因与发病机制", next_sibling: "诊断标准" }

Chunk #3 (level=2, breadcrumb="儿童支气管哮喘 > 治疗方案 > 急性发作期治疗（SABA为核心）")
  "• 轻中度：沙丁胺醇雾化0.15mg/kg（最小2.5mg），每20分钟一次 × 3次\n
   • 重度：异丙托溴铵联合SABA雾化；全身激素（甲泼尼龙1～2mg/kg静注）\n
   • 危重：氦氧混合气、MgSO₄静脉（25～75mg/kg），必要时机械通气"
  metadata: { section_path: ["治疗方案", "急性发作期治疗（SABA为核心）"],
              section_level: 2, parent_section: "治疗方案",
              root_section: "治疗方案",
              prev_sibling: null, next_sibling: "长期控制治疗（阶梯方案）",
              breadcrumb: "治疗方案 > 急性发作期治疗（SABA为核心）" }

Chunk #4 (level=2, breadcrumb="儿童支气管哮喘 > 治疗方案 > 长期控制治疗（阶梯方案）")
  "• 第一阶：按需SABA（轻度间歇）\n
   • 第二阶：低剂量ICS（如布地奈德100μg/次，bid）——控制治疗基石\n..."
  metadata: { section_path: ["治疗方案", "长期控制治疗（阶梯方案）"],
              prev_sibling: "急性发作期治疗（SABA为核心）",
              next_sibling: "吸入技术教育" }
```

## 向后兼容性

- 默认策略不变（`semantic`），所有现有行为不受影响
- `RecursiveTextSplitter` 不做任何修改
- metadata JSON 字段是**增量添加**的，现有使用 metadata 的代码不会出错

## 验证方式

1. `mvn compile` 编译通过
2. `mvn test -Dtest=MedicalDocumentSplitterTest` 单元测试全部通过
3. 用儿童支气管哮喘完整文档跑一遍 `splitWithMetadata()`，打印每个 chunk 的 breadcrumb + section_path，验证层级正确
4. 验证 metadata JSON 可被 `DocumentChunk` 实体正确序列化存储
