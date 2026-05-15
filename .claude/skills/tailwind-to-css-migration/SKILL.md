---
name: tailwind-to-css-migration
description: 根据原型图全量样式分析，将 Vue 项目从 Tailwind 迁移至自定义 CSS 类（无硬编码颜色）。使用 Playwright MCP 提取原型图计算样式，生成自定义 CSS 类，批量替换所有 .vue 文件中的 Tailwind 类名，并通过视觉回归验证一致性。
---

# Tailwind → 自定义 CSS 迁移技能

## 角色定义

你是一名前端自动化与 Playwright MCP 专家。任务：分析设计原型图和现有 Vue 页面的**所有计算样式**（颜色、背景、边框、字体、间距、圆角、阴影等），彻底移除 Tailwind CSS，并将所有样式替换为基于原型图分析生成的自定义 CSS 类。**不做任何颜色或样式的硬编码**——所有自定义类的值均来自原型图的真实计算样式。

## 核心约束

1. **完全移除 Tailwind CSS**：
   - 删除 `tailwind.config.js` 中所有配置（包括颜色、字体、间距等）。
   - 移除项目中所有 Tailwind 类名的引用（如 `text-*`、`bg-*`、`p-*`、`m-*`、`border-*` 等）。
   - 删除 Tailwind 的 CDN / PostCSS 引入。
2. **全部样式必须通过自定义 CSS 类控制**：
   - 类名采用语义化或组件化命名（如 `.card`、`.btn-primary`），样式规则完全复制原型图计算值。
   - 不允许出现任何硬编码样式值（除了自定义类定义内部）。
3. **基于实际样式分析**：使用 Playwright MCP 提取原型图页面和 Vue 页面中每个元素的最终计算样式（`getComputedStyle`），作为生成自定义类的唯一依据。
4. **只改样式不改功能**：保持布局结构、交互行为、脚本逻辑完全不变。

## 输入信息

需要用户提供：
- **原型图页面 URL / 本地路径**
- **Vue 项目根目录路径**
- **Vue 页面 URL（开发服务器）**

## 执行步骤

### 步骤 1：环境准备

1. 确认 Playwright MCP 可用（`playwright_navigate`、`playwright_evaluate`、`playwright_screenshot` 等工具）。
2. 启动 Vue 开发服务器（若用户提供的是本地项目）：
   ```bash
   cd {Vue 项目根目录}
   npm run dev
   ```
3. 分别记录原型图页面 URL 和 Vue 页面 URL。

### 步骤 2：分析原型图页面的所有计算样式

使用 `playwright_navigate` 加载原型图页面，使用 `playwright_evaluate` 提取每个 DOM 元素的完整样式：

```javascript
// 辅助函数：将 rgb/rgba 转换为 HEX
function rgbToHex(rgb) {
  if (!rgb || rgb === 'rgba(0, 0, 0, 0)') return null;
  const match = rgb.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (!match) return rgb;
  const r = parseInt(match[1]).toString(16).padStart(2, '0');
  const g = parseInt(match[2]).toString(16).padStart(2, '0');
  const b = parseInt(match[3]).toString(16).padStart(2, '0');
  return `#${r}${g}${b}`.toLowerCase();
}

// 判断是否为 Tailwind 类名
function isTailwindClass(cls) {
  return /^(m[trblxy]?|p[trblxy]?|text-|bg-|border-|rounded-|shadow-|flex|grid|gap-|w-|h-|min-|max-|space-|font-|leading-|tracking-|opacity-|z-|justify-|items-|content-|self-|col-|row-|order-)/.test(cls);
}

const allElements = document.querySelectorAll('*');
const styleMap = new Map();

for (const el of allElements) {
  const computed = getComputedStyle(el);
  const importantProps = [
    'color', 'backgroundColor', 'borderColor', 'borderTopColor', 'borderRightColor',
    'borderBottomColor', 'borderLeftColor', 'borderWidth', 'borderTopWidth',
    'borderRightWidth', 'borderBottomWidth', 'borderLeftWidth', 'borderStyle',
    'fontSize', 'fontFamily', 'fontWeight', 'lineHeight', 'textAlign',
    'marginTop', 'marginRight', 'marginBottom', 'marginLeft',
    'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
    'width', 'height', 'minWidth', 'minHeight', 'display', 'position',
    'top', 'left', 'right', 'bottom', 'borderRadius', 'boxShadow',
    'opacity', 'zIndex', 'backgroundImage', 'backgroundSize', 'backgroundRepeat',
    'backgroundPosition', 'overflow', 'flexDirection', 'flexWrap', 'justifyContent',
    'alignItems', 'gap', 'gridTemplateColumns', 'gridTemplateRows',
    'letterSpacing', 'whiteSpace', 'textOverflow', 'wordBreak',
    'transition', 'transform', 'cursor', 'outline', 'outlineWidth',
    'outlineColor', 'outlineStyle', 'fill', 'stroke'
  ];
  const styles = {};
  for (const prop of importantProps) {
    let value = computed[prop];
    if (prop.includes('Color') || prop === 'color' || prop === 'fill' || prop === 'stroke') {
      if (value && value !== 'rgba(0, 0, 0, 0)' && value !== 'transparent') {
        value = rgbToHex(value);
      } else {
        continue; // 跳过透明/默认值
      }
    }
    if (value && value !== '' && value !== 'none' && !value.startsWith('initial') && !value.startsWith('revert')) {
      styles[prop] = value;
    }
  }

  // 生成选择器标识
  let selector = '';
  if (el.id) {
    selector = `#${el.id}`;
  } else if (el.className && typeof el.className === 'string') {
    const classes = el.className.trim().split(/\s+/).filter(c => c && !isTailwindClass(c));
    if (classes.length > 0) {
      selector = `.${classes.join('.')}`;
    } else {
      selector = el.tagName.toLowerCase();
    }
  } else {
    selector = el.tagName.toLowerCase();
  }

  if (!styleMap.has(selector)) styleMap.set(selector, []);
  styleMap.get(selector).push({ tag: el.tagName.toLowerCase(), styles, hasTailwind: el.className && typeof el.className === 'string' ? el.className.split(/\s+/).some(isTailwindClass) : false });
}

// 聚合同一选择器的样式（取出现频率最高的值）
const aggregated = [];
for (const [selector, entries] of styleMap) {
  const aggregatedStyles = {};
  const allProps = new Set();
  entries.forEach(e => Object.keys(e.styles).forEach(p => allProps.add(p)));

  for (const prop of allProps) {
    const valueCount = new Map();
    entries.forEach(e => {
      if (e.styles[prop] !== undefined) {
        const v = e.styles[prop];
        valueCount.set(v, (valueCount.get(v) || 0) + 1);
      }
    });
    // 取出现频率最高的值
    let maxCount = 0;
    let bestValue = null;
    for (const [value, count] of valueCount) {
      if (count > maxCount) {
        maxCount = count;
        bestValue = value;
      }
    }
    if (bestValue !== null) {
      aggregatedStyles[prop] = bestValue;
    }
  }

  aggregated.push({ selector, styles: aggregatedStyles, count: entries.length });
}

// 输出按 DOM 树路径组织的结构化数据
const result = {
  url: window.location.href,
  timestamp: new Date().toISOString(),
  elements: aggregated
};
JSON.stringify(result, null, 2);
```

将提取结果保存为 `prototype-styles.json`。

### 步骤 3：分析 Vue 页面的当前样式

1. 使用 `playwright_navigate` 加载 Vue 页面（确保开发服务器运行中）。
2. 使用与步骤 2 相同的脚本提取所有计算样式，同时额外提取：
   - 当前应用的 Tailwind 类名
   - 内联 `style` 属性
   - `<style>` 块中的规则

提取 Vue 页面每个元素的样式和类名信息：

```javascript
const allElements = document.querySelectorAll('*');
const results = [];

for (const el of allElements) {
  const tag = el.tagName.toLowerCase();
  const id = el.id || '';
  const classList = Array.from(el.classList);
  const inlineStyle = el.getAttribute('style') || '';
  const computed = getComputedStyle(el);

  const tailwindClasses = classList.filter(c => isTailwindClass(c));
  const nonTailwindClasses = classList.filter(c => !isTailwindClass(c));

  const importantProps = ['color', 'backgroundColor', 'borderColor', 'fontSize', 'fontWeight', 'lineHeight', 'textAlign', 'marginTop', 'marginRight', 'marginBottom', 'marginLeft', 'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft', 'width', 'height', 'display', 'position', 'borderRadius', 'boxShadow', 'fontFamily', 'borderWidth', 'borderStyle', 'opacity', 'gap', 'flexDirection', 'justifyContent', 'alignItems', 'overflow'];

  const styles = {};
  for (const prop of importantProps) {
    let value = computed[prop];
    if (prop.includes('Color') || prop === 'color') {
      if (value && value !== 'rgba(0, 0, 0, 0)' && value !== 'transparent') {
        value = rgbToHex(value);
      } else {
        continue;
      }
    }
    if (value && value !== '' && value !== 'none') {
      styles[prop] = value;
    }
  }

  results.push({
    tag,
    id,
    tailwindClasses,
    nonTailwindClasses,
    inlineStyle,
    styles,
    text: (el.textContent || '').trim().slice(0, 50)
  });
}

JSON.stringify({ url: window.location.href, elements: results }, null, 2);
```

将提取结果保存为 `vue-current-styles.json`。

### 步骤 4：对比与样式映射设计

编写对比脚本，遍历原型图清单中的每个组件，在 Vue 清单中找到对应元素，生成自定义类映射表：

```javascript
// 伪代码逻辑
const prototypeStyles = require('./prototype-styles.json');
const vueStyles = require('./vue-current-styles.json');

const mappingTable = [];

for (const proto of prototypeStyles.elements) {
  // 在 Vue 样式中寻找匹配元素（通过标签名 + 文本内容 + 结构位置）
  const matches = vueStyles.elements.filter(v => {
    // 匹配策略：选择器名相似 或 标签+文本匹配
    const protoBase = proto.selector.replace(/^[.#]/, '');
    const vueClassMatch = v.nonTailwindClasses.some(c => c.includes(protoBase) || protoBase.includes(c));
    const tagMatch = v.tag === proto.selector.toLowerCase() || proto.selector.toLowerCase() === v.tag;
    return vueClassMatch || (tagMatch && v.text && proto.elements[0]?.text?.includes(v.text));
  });

  if (matches.length > 0) {
    const vueEl = matches[0];
    const diffStyles = {};

    for (const [prop, protoVal] of Object.entries(proto.styles)) {
      const vueVal = vueEl.styles[prop];
      if (vueVal && vueVal !== protoVal) {
        diffStyles[prop] = { from: vueVal, to: protoVal };
      } else if (!vueVal && protoVal) {
        diffStyles[prop] = { from: null, to: protoVal };
      }
    }

    if (Object.keys(diffStyles).length > 0) {
      mappingTable.push({
        componentSelector: proto.selector,
        originalTailwindClasses: vueEl.tailwindClasses,
        generatedClassName: proto.selector.replace(/^[.#]/, ''),
        textSample: proto.elements[0]?.text || '',
        stylesToApply: Object.fromEntries(
          Object.entries(diffStyles).map(([prop, { to }]) => [prop, to])
        )
      });
    } else {
      mappingTable.push({
        componentSelector: proto.selector,
        originalTailwindClasses: vueEl.tailwindClasses,
        generatedClassName: proto.selector.replace(/^[.#]/, ''),
        textSample: proto.elements[0]?.text || '',
        stylesToApply: {},
        note: '样式已匹配，无需变更'
      });
    }
  } else {
    mappingTable.push({
      componentSelector: proto.selector,
      originalTailwindClasses: [],
      generatedClassName: proto.selector.replace(/^[.#]/, ''),
      textSample: proto.elements[0]?.text || '',
      stylesToApply: proto.styles,
      note: 'Vue 中未找到精确匹配，建议人工确认'
    });
  }
}

JSON.stringify(mappingTable, null, 2);
```

**命名规则**：
- 优先使用元素已有的语义化类名（如 `.header`、`.btn`）。
- 若无合适类名，根据标签+功能生成（如 `.card-title`、`.nav-link`）。
- 保证类名在项目中唯一（跨文件搜索验证）。

### 步骤 5：生成全局自定义 CSS 文件

创建 `src/styles/custom-styles.css`（或 `src/assets/global.css`）：

```css
/* ============================================
   自定义样式系统 — 基于原型图计算样式生成
   生成日期: {date}
   来源原型图: {prototypeUrl}
   注意: 所有值均来自原型图真实计算样式，禁止手动修改
   ============================================ */

/* ===== 布局容器 ===== */

/* ===== 导航组件 ===== */

/* ===== 卡片组件 ===== */

/* ===== 按钮组件 ===== */

/* ===== 表单组件 ===== */

/* ===== 文字排版 ===== */

/* ===== 列表/表格 ===== */

/* ===== 弹窗/对话框 ===== */

/* ===== 辅助工具类 ===== */
```

在 Vue 入口文件中的修改：
- **删除** Tailwind 的导入（`import 'tailwindcss/tailwind.css'` 或类似）。
- **添加** 自定义 CSS 导入：`import './styles/custom-styles.css'`。

### 步骤 6：批量替换 Vue 文件中的样式应用

创建 Node.js 替换脚本 `scripts/migrate-styles.js`：

```javascript
/**
 * Tailwind → 自定义 CSS 迁移脚本
 * 用法: node scripts/migrate-styles.js
 *
 * 功能:
 * 1. 扫描 src/ 下所有 .vue 文件
 * 2. 根据映射表替换 Tailwind 类名为自定义类名
 * 3. 移除内联 style 属性中的样式
 * 4. 清理 <style> 块中的硬编码值
 * 5. 生成修改前后对比报告
 */

const fs = require('fs');
const path = require('path');

// 配置
const VUE_SRC = path.resolve(__dirname, '../src');
const MAPPING_FILE = path.resolve(__dirname, '../style-mapping.json');
const REPORT_FILE = path.resolve(__dirname, '../migration-report.json');

// 加载映射表
const mapping = JSON.parse(fs.readFileSync(MAPPING_FILE, 'utf-8'));

// Tailwind 类名识别正则
const TAILWIND_PATTERN = /\b(m[trblxy]?-[0-9a-z.-]+|p[trblxy]?-[0-9a-z.-]+|text-[a-z0-9-]+|bg-[a-z0-9-]+|border-[a-z0-9-]+|rounded-[a-z0-9-]+|shadow-[a-z0-9-]+|w-[0-9a-z./-]+|h-[0-9a-z./-]+|gap-[0-9a-z.-]+|space-[xyr]-[0-9a-z.-]+|font-[a-z0-9-]+|leading-[0-9a-z.-]+|tracking-[a-z0-9-]+|opacity-[0-9]+|z-[0-9]+|flex|grid|inline-flex|inline-block|inline|hidden|relative|absolute|fixed|sticky|static|overflow-[a-z-]+)\b/g;

function scanVueFiles(dir) {
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory() && !entry.name.startsWith('.')) {
      files.push(...scanVueFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith('.vue')) {
      files.push(fullPath);
    }
  }
  return files;
}

function replaceTailwindClasses(content, filePath) {
  let modified = false;
  let replacements = [];

  // 1. 替换 template 中的 Tailwind 类名
  const templateMatch = content.match(/<template>([\s\S]*)<\/template>/);
  if (templateMatch) {
    let template = templateMatch[1];
    const originalTemplate = template;

    // 移除 class 属性中的 Tailwind 类名
    template = template.replace(/class="([^"]*)"/g, (match, classStr) => {
      const classes = classStr.trim().split(/\s+/);
      const nonTailwind = classes.filter(c => !TAILWIND_PATTERN.test(c));
      if (nonTailwind.length !== classes.length) {
        const removed = classes.filter(c => TAILWIND_PATTERN.test(c));
        replacements.push({ type: 'tailwind_class', removed: removed.join(', ') });
        modified = true;
        if (nonTailwind.length > 0) {
          return `class="${nonTailwind.join(' ')}"`;
        }
        return '';
      }
      return match;
    });

    // 根据映射表为元素添加自定义类名
    for (const item of mapping) {
      if (!item.generatedClassName || Object.keys(item.stylesToApply).length === 0) continue;

      // 在 template 中查找匹配的元素并添加类名
      // （具体匹配逻辑取决于实际 DOM 结构，这里简化处理）
    }

    if (template !== originalTemplate) {
      content = content.replace(templateMatch[1], template);
    }
  }

  // 2. 替换 <style> 中的 @apply 指令
  const styleMatch = content.match(/<style[^>]*>([\s\S]*)<\/style>/);
  if (styleMatch) {
    let style = styleMatch[1];
    if (style.includes('@apply')) {
      // 移除 @apply 行
      style = style.replace(/^\s*@apply\s+.*;?\s*$/gm, '');
      modified = true;
    }
    content = content.replace(styleMatch[1], style);
  }

  return { content, modified, replacements };
}

// 主流程
const vueFiles = scanVueFiles(VUE_SRC);
console.log(`找到 ${vueFiles.length} 个 .vue 文件`);

const report = {
  timestamp: new Date().toISOString(),
  totalFiles: vueFiles.length,
  modifiedFiles: [],
  skippedFiles: [],
  totalReplacements: 0
};

for (const file of vueFiles) {
  const original = fs.readFileSync(file, 'utf-8');
  const { content, modified, replacements } = replaceTailwindClasses(original, file);

  if (modified) {
    // 创建备份
    fs.writeFileSync(file + '.bak', original, 'utf-8');
    // 写入修改后内容
    fs.writeFileSync(file, content, 'utf-8');
    report.modifiedFiles.push({
      file: path.relative(VUE_SRC, file),
      replacements
    });
    report.totalReplacements += replacements.length;
    console.log(`[已修改] ${file} (${replacements.length} 处替换)`);
  } else {
    report.skippedFiles.push(path.relative(VUE_SRC, file));
  }
}

fs.writeFileSync(REPORT_FILE, JSON.stringify(report, null, 2), 'utf-8');
console.log(`\n迁移完成！修改了 ${report.modifiedFiles.length} 个文件，共 ${report.totalReplacements} 处替换`);
console.log(`报告已保存至: ${REPORT_FILE}`);
```

执行脚本：
```bash
node scripts/migrate-styles.js
```

### 步骤 7：验证与视觉回归

1. **重新编译并启动开发服务器**：
   ```bash
   cd {Vue 项目根目录}
   npm run dev
   ```

2. **使用 Playwright MCP 截取全屏截图**：
   ```javascript
   // 原型图截图
   await playwright_navigate({ url: '{prototypeUrl}', waitUntil: 'networkidle' });
   await playwright_screenshot({ path: 'prototype-fullpage.png' });

   // Vue 页面截图
   await playwright_navigate({ url: '{vueDevServerUrl}', waitUntil: 'networkidle' });
   await playwright_screenshot({ path: 'vue-after-migration.png' });
   ```

3. **视觉差异对比**：
   重新提取替换后 Vue 页面的计算样式清单，与原型图清单逐属性对比。

4. **功能验证**：
   - 检查页面交互是否正常（点击、悬停、滚动等）。
   - 确认响应式布局未受影响（缩小浏览器窗口测试）。
   - 验证动态样式绑定仍正常工作。

### 步骤 8：生成迁移报告

在项目根目录创建 `MIGRATION_REPORT.md`：

```markdown
# Tailwind → 自定义 CSS 迁移报告

## 概述

- **迁移日期**: {date}
- **原型图来源**: {prototypeUrl}
- **Vue 项目路径**: {vueProjectPath}

## 变更内容

### 已删除的 Tailwind 配置及引入

- `tailwind.config.js` — 已清空配置
- `postcss.config.js` — 已移除 Tailwind 插件
- `src/main.js` — 已移除 `import 'tailwindcss/tailwind.css'`

### 生成的自定义 CSS 类

| 类名 | 选择器 | 主要样式规则 |
|------|--------|-------------|
| `.xxx` | `.xxx` | `color: #...; font-size: ...;` |

### 修改的 Vue 文件

| 文件路径 | 替换类型 | 替换数量 |
|----------|---------|---------|
| src/views/xxx.vue | Tailwind 类名、内联样式 | 15 处 |

## 验证结果

- **像素匹配度**: xx%
- **功能测试**: 通过/未通过（列出未通过项）
- **响应式布局**: 正常/异常

## 边界情况与建议

1. 动态绑定的类名（`:class` 中的表达式）可能需要手动检查。
2. 第三方组件库的样式未受影响，若需要统一样式请手动调整。
3. ...
```

## 注意事项

1. **备份原始文件**：所有修改前自动创建 `.bak` 备份文件。
2. **颜色归一化**：使用 `rgbToHex` 函数将所有颜色值转换为 HEX，避免格式差异导致的误判。
3. **选择性忽略**：对 `fontFamily` 等系统相关属性，若值与操作系统有关可选择性忽略。
4. **增量迁移**：对于大型项目，建议分模块迁移，每次处理一个页面。
5. **回滚方案**：若迁移后出现问题，可使用备份文件恢复：
   ```bash
   find src/ -name '*.vue.bak' -exec sh -c 'mv "$1" "${1%.bak}"' _ {} \;
   ```

## 辅助函数库

`rgbToHex` 实现：

```javascript
function rgbToHex(rgb) {
  if (!rgb || !rgb.startsWith('rgb')) return rgb;
  const match = rgb.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (!match) return rgb;
  const r = parseInt(match[1]).toString(16).padStart(2, '0');
  const g = parseInt(match[2]).toString(16).padStart(2, '0');
  const b = parseInt(match[3]).toString(16).padStart(2, '0');
  return `#${r}${g}${b}`.toLowerCase();
}
```

Tailwind 类名校验正则：

```javascript
const TAILWIND_CLASS_RE = /^(m[trblxy]?|p[trblxy]?|text-|bg-|border-|rounded-|shadow-|w-|h-|min-|max-|gap-|space-|font-|leading-|tracking-|opacity-|z-|justify-|items-|content-|self-|col-|row-|order-|flex|grid|inline-flex|inline-block|inline|hidden|relative|absolute|fixed|sticky|static|overflow-|truncate|select-|whitespace-|break-|list-|object-|inset-|translate-|scale-|rotate-|skew-|transition-|duration-|ease-|delay-|animate-|sr-only|not-sr-only|visible|invisible|cursor-|resize|pointer-events-)/;
function isTailwindClass(cls) { return TAILWIND_CLASS_RE.test(cls); }
```
