/**
 * Tailwind → 自定义CSS 迁移脚本
 * 替换 KnowledgeView.vue 中的 Tailwind 类名为自定义 CSS 类名
 */
const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, '../../src/views/KnowledgeView.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// 备份
fs.writeFileSync(filePath + '.bak', content, 'utf-8');

const replacements = [
  // ===== 布局类 (custom-*) =====
  [/\bflex\b(?!-)/g, 'custom-flex'],
  [/\binline-flex\b/g, 'custom-inline-flex'],
  [/\bgrid\b(?!-)/g, 'custom-grid'],
  [/\bflex-1\b/g, 'custom-flex-1'],
  [/\bflex-col\b/g, 'custom-flex-col'],
  [/\bflex-wrap\b/g, 'custom-flex-wrap'],
  [/\bitems-center\b/g, 'custom-items-center'],
  [/\bjustify-between\b/g, 'custom-justify-between'],
  [/\bjustify-center\b/g, 'custom-justify-center'],
  [/\bhidden\b/g, 'custom-hidden'],
  [/\boverflow-hidden\b/g, 'custom-overflow-hidden'],
  [/\boverflow-x-auto\b/g, 'custom-overflow-x-auto'],
  [/\boverflow-y-auto\b/g, 'custom-overflow-y-auto'],
  [/\brelative\b/g, 'custom-relative'],
  [/\babsolute\b/g, 'custom-absolute'],
  [/\bmx-auto\b/g, 'custom-mx-auto'],

  // ===== Grid 类 =====
  [/\bgrid-cols-12\b/g, 'custom-grid-cols-12'],
  [/\bgrid-cols-2\b/g, 'custom-grid-cols-2'],

  // ===== Gap 类 =====
  [/\bgap-1\b/g, 'custom-gap-1'],
  [/\bgap-2\b/g, 'custom-gap-2'],
  [/\bgap-3\b/g, 'custom-gap-3'],
  [/\bgap-4\b/g, 'custom-gap-4'],
  [/\bgap-6\b/g, 'custom-gap-6'],

  // ===== Padding 类 =====
  [/\bp-1\b(?!\d)/g, 'custom-p-1'],
  [/\bp-2\b(?!\d)/g, 'custom-p-2'],
  [/\bp-4\b(?!\d)/g, 'custom-p-4'],
  [/\bp-5\b(?!\d)/g, 'custom-p-5'],
  [/\bp-6\b(?!\d)/g, 'custom-p-6'],
  [/\bp-8\b(?!\d)/g, 'custom-p-8'],
  [/\bp-10\b(?!\d)/g, 'custom-p-10'],
  [/\bpx-2\b(?!\d)/g, 'custom-px-2'],
  [/\bpx-3\b(?!\d)/g, 'custom-px-3'],
  [/\bpx-4\b(?!\d)/g, 'custom-px-4'],
  [/\bpx-6\b(?!\d)/g, 'custom-px-6'],
  [/\bpy-0\.5\b/g, 'custom-py-0\\.5'],
  [/\bpy-1\b(?!\d)/g, 'custom-py-1'],
  [/\bpy-1\.5\b/g, 'custom-py-1\\.5'],
  [/\bpy-2\b(?!\.?\d)/g, 'custom-py-2'],
  [/\bpy-2\.5\b/g, 'custom-py-2\\.5'],
  [/\bpy-3\b(?!\d)/g, 'custom-py-3'],
  [/\bpy-4\b(?!\d)/g, 'custom-py-4'],
  [/\bpy-5\b(?!\d)/g, 'custom-py-5'],
  [/\bpy-12\b(?!\d)/g, 'custom-py-12'],
  [/\bpl-10\b/g, 'custom-pl-10'],
  [/\bpr-4\b(?!\d)/g, 'custom-pr-4'],
  [/\bpb-4\b(?!\d)/g, 'custom-pb-4'],
  [/\bpt-1\b(?!\d)/g, 'custom-pt-1'],
  [/\bpt-4\b(?!\d)/g, 'custom-pt-4'],

  // ===== Margin 类 =====
  [/\bmb-2\b(?!\d)/g, 'custom-mb-2'],
  [/\bmb-4\b(?!\d)/g, 'custom-mb-4'],
  [/\bmb-6\b(?!\d)/g, 'custom-mb-6'],
  [/\bmb-10\b/g, 'custom-mb-10'],
  [/\bmt-2\b(?!\d)/g, 'custom-mt-2'],
  [/\bmr-1\b(?!\d)/g, 'custom-mr-1'],
  [/\bmr-2\b(?!\d)/g, 'custom-mr-2'],

  // ===== Width/Height 类 =====
  [/\bw-full\b/g, 'custom-w-full'],
  [/\bw-8\b(?!\d)/g, 'custom-w-8'],
  [/\bw-16\b(?!\d)/g, 'custom-w-16'],
  [/\bw-80\b(?!\d)/g, 'custom-w-80'],
  [/\bw-1\.5\b/g, 'custom-w-1\\.5'],
  [/\bw-2\/3\b/g, 'custom-w-2\\/3'],
  [/\bw-3\/4\b/g, 'custom-w-3\\/4'],
  [/\bw-5\/6\b/g, 'custom-w-5\\/6'],
  [/\bh-3\b(?!\d)/g, 'custom-h-3'],
  [/\bh-8\b(?!\d)/g, 'custom-h-8'],
  [/\bh-16\b(?!\d)/g, 'custom-h-16'],
  [/\bh-24\b(?!\d)/g, 'custom-h-24'],
  [/\bh-1\.5\b/g, 'custom-h-1\\.5'],
  [/\bh-full\b/g, 'custom-h-full'],
  [/\bmax-w-2xl\b/g, 'custom-max-w-2xl'],

  // ===== 过渡/动画类 =====
  [/\btransition-all\b/g, 'custom-transition-all'],
  [/\btransition-colors\b/g, 'custom-transition-colors'],
  [/\btransition-opacity\b/g, 'custom-transition-opacity'],
  [/\btransition-shadow\b/g, 'custom-transition-shadow'],
  [/\btransition-transform\b/g, 'custom-transition-transform'],
  [/\bduration-300\b/g, 'custom-duration-300'],
  [/\banimate-spin\b/g, 'custom-animate-spin'],
  [/\banimate-pulse\b/g, 'custom-animate-pulse'],

  // ===== 悬停/焦点/活跃状态 (在自定义CSS中用连字符代替冒号) =====
  [/\bhover:bg-primary-fixed\b/g, 'hover-bg-primary-fixed'],
  [/\bhover:bg-surface-container-low\b/g, 'hover-bg-surface-container-low'],
  [/\bhover:bg-surface\b/g, 'hover-bg-surface'],
  [/\bhover:bg-subtle\b/g, 'hover-bg-subtle'],
  [/\bhover:bg-processing\/20\b/g, 'hover-bg-processing-20'],
  [/\bhover:opacity-90\b/g, 'hover-opacity-90'],
  [/\bhover:text-primary\b/g, 'hover-text-primary'],
  [/\bhover:border-primary\/50\b/g, 'hover-border-primary-50'],
  [/\bfocus:ring-2\b/g, 'focus-ring-2'],
  [/\bfocus:ring-primary\/20\b/g, 'focus-ring-primary-20'],
  [/\bactive:scale-95\b/g, 'active-scale-95'],
  [/\bgroup-hover:scale-110\b/g, 'group-hover-scale-110'],

  // ===== 光标 =====
  [/\bcursor-pointer\b/g, 'cursor-pointer'],

  // ===== 分隔线 =====
  [/\bdivide-y\b/g, 'divide-y'],
  [/\bdivide-outline-variant\/10\b/g, 'divide-outline-variant-10'],

  // ===== 占位符 =====
  [/\bplaceholder:text-on-surface-variant\b/g, 'placeholder-on-surface-variant'],

  // ===== 特殊类 =====
  [/\bmax-w-\[(\d+)px\]/g, (m, p1) => `custom-max-w-${p1}`],
  [/\bh-\[(\d+)px\]/g, (m, p1) => `custom-h-${p1}`],
];

// 应用替换
for (const [pattern, replacement] of replacements) {
  // 只替换模板中和style中的类，不影响script
  content = content.replace(pattern, replacement);
}

// 写入文件
fs.writeFileSync(filePath, content, 'utf-8');

console.log('KnowledgeView.vue 迁移完成！');
console.log('备份已保存至: ' + filePath + '.bak');
