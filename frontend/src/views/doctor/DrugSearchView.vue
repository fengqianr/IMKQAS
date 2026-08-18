<template>
  <div class="drug-search-page">
    <!-- 搜索卡 -->
    <section class="search-card">
      <div class="search-row">
        <div class="search-name">
          <label class="field-label">药品名称</label>
          <div class="input-group">
            <el-input
              v-model="keyword"
              class="keyword-input"
              placeholder="输入通用名或商品名，如：阿司匹林"
              clearable
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <span class="material-symbols-outlined input-prefix-icon">search</span>
              </template>
            </el-input>
            <el-button type="primary" class="search-btn" :loading="loading" @click="handleSearch">
              搜索
            </el-button>
          </div>
        </div>
        <div class="search-class">
          <label class="field-label">药品分类</label>
          <el-select
            v-model="selectedClass"
            class="class-select"
            placeholder="全部分类"
            clearable
            @change="handleSearch"
          >
            <el-option v-for="c in classes" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
      </div>
    </section>

    <!-- 主区：左侧药品网格 + 右侧相互作用面板 -->
    <div class="main-area">
      <div class="grid-wrap">
        <!-- 当前用药方案（勾选集合实时渲染） -->
        <section class="regimen-card">
          <div class="regimen-head">
            <h3 class="regimen-title">当前用药方案</h3>
            <p class="regimen-sub">勾选药品以分析相互作用</p>
          </div>
          <div class="regimen-chips">
            <div v-for="id in selected" :key="id" class="drug-chip">
              <span class="material-symbols-outlined chip-icon">pill</span>
              <span class="chip-name">{{ drugNameById(id) }}</span>
              <button class="chip-remove" title="移出方案" @click="removeSelected(id)">
                <span class="material-symbols-outlined">close</span>
              </button>
            </div>
            <button class="chip-add" title="在上方搜索并勾选药品加入方案">
              <span class="material-symbols-outlined">add</span>
              添加药品
            </button>
          </div>
        </section>

        <!-- 药品卡片网格 -->
        <div v-if="drugs.length" class="drug-grid">
          <div
            v-for="d in drugs"
            :key="d.id"
            class="drug-card"
            :class="{ 'drug-card-selected': isSelected(d.id) }"
            @click="openDetail(d)"
          >
            <!-- 左侧风险色条（按与已选药品相互作用严重度编码） -->
            <div class="drug-accent" :class="drugRiskClass(d.id)" />
            <!-- 右上角勾选（参与相互作用检查），阻止冒泡避免触发详情 -->
            <div
              class="select-box"
              :class="{ 'select-box-checked': isSelected(d.id) }"
              @click.stop="toggleSelect(d)"
            >
              <span v-if="isSelected(d.id)" class="material-symbols-outlined">check</span>
            </div>
            <div class="card-body">
              <h3 class="drug-name" :title="drugTitle(d)">{{ drugTitle(d) }}</h3>
              <p class="drug-subtitle">{{ drugSubtitle(d) }}</p>
              <div class="badge-row">
                <span v-if="d.drugClass" class="badge badge-class">{{ d.drugClass }}</span>
                <span v-if="drugRiskClass(d.id) === 'drug-risk-danger'" class="badge badge-risk badge-risk-danger">
                  <span class="material-symbols-outlined badge-icon">warning</span>
                  高风险
                </span>
                <span v-else-if="drugRiskClass(d.id) === 'drug-risk-warning'" class="badge badge-risk badge-risk-warning">
                  <span class="material-symbols-outlined badge-icon">monitor</span>
                  需监测
                </span>
                <span v-else-if="d.hasInteractions === 1" class="badge badge-interactions">
                  <span class="material-symbols-outlined badge-icon">warning</span>
                  有相互作用
                </span>
              </div>
            </div>
            <div class="card-footer">
              <span class="footer-label">规格</span>
              <span class="footer-value">{{ d.specification || '—' }}</span>
              <span class="footer-label">厂商</span>
              <span class="footer-value truncate">{{ d.manufacturer || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- 空态 -->
        <div v-else-if="!loading" class="empty-box">
          <div class="empty-icon">
            <span class="material-symbols-outlined">medication</span>
          </div>
          <h3 class="empty-title">未找到匹配的药品</h3>
          <p class="empty-desc">请输入药品名称或选择药品分类进行检索。</p>
        </div>
      </div>

      <!-- 相互作用分析面板（勾选 ≥2 个药品后出现） -->
      <aside v-if="selected.length >= 2" ref="panelRef" class="interaction-panel">
        <div class="panel-header">
          <h3 class="panel-title">
            <span class="material-symbols-outlined panel-icon">healing</span>
            相互作用分析
          </h3>
          <button class="panel-close" @click="clearSelected">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>
        <div class="panel-body">
          <!-- Major Risk banner：存在最高档风险时提示 -->
          <div v-if="hasMajorRisk" class="risk-banner">
            <span class="material-symbols-outlined risk-banner-icon">gpp_bad</span>
            <div>
              <h4 class="risk-banner-title">检测到高风险相互作用</h4>
              <p class="risk-banner-desc">所选药品之间存在严重相互作用，请谨慎评估用药方案。</p>
            </div>
          </div>
          <div v-if="checking" class="panel-loading">
            <span class="loading-dot" />检查中…
          </div>
          <div v-else-if="interactions.length" class="panel-list">
            <div
              v-for="(it, i) in interactions"
              :key="i"
              class="interaction-card"
              :class="toneCard(severityTone(it.severity))"
            >
              <div class="interaction-head">
                <div class="drug-pair">
                  <span class="pair-name">{{ drugNameById(it.drugAId) }}</span>
                  <span class="material-symbols-outlined pair-icon">sync_alt</span>
                  <span class="pair-name">{{ drugNameById(it.drugBId) }}</span>
                </div>
                <span class="type-badge" :class="toneBadge(interactionTone(it.interactionType))">
                  {{ interactionTypeText(it.interactionType) }}
                  <span v-if="it.severity" class="sev">· {{ severityText(it.severity) }}</span>
                </span>
              </div>
              <p class="interaction-desc"><strong>描述：</strong>{{ it.description || '—' }}</p>
              <p class="interaction-mech"><strong>机制：</strong>{{ it.mechanism || '—' }}</p>
              <div class="interaction-rec">
                <span class="material-symbols-outlined rec-icon">warning</span>
                <span>{{ it.recommendation || '—' }}</span>
              </div>
            </div>
          </div>
          <div v-else class="panel-empty">
            <span class="material-symbols-outlined">verified_user</span>
            <p>未发现所选药品之间的相互作用</p>
          </div>
        </div>
      </aside>
    </div>

    <!-- 移动端浮动按钮 -->
    <button v-if="selected.length >= 2" class="fab" @click="scrollToPanel">
      <span class="material-symbols-outlined">science</span>
      检查相互作用 ({{ selected.length }})
    </button>

    <!-- 药品详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="dialogTitle"
      width="640px"
      custom-class="drug-detail-dialog"
    >
      <div v-if="currentDrug" class="detail-body">
        <div class="detail-section">
          <h4 class="section-title">基本信息</h4>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">通用名</span>
              <span class="info-value">{{ currentDrug.genericName || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">商品名</span>
              <span class="info-value">{{ currentDrug.brandName || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">英文名</span>
              <span class="info-value">{{ currentDrug.englishName || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">药品分类</span>
              <span class="info-value">{{ currentDrug.drugClass || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">剂型</span>
              <span class="info-value">{{ currentDrug.dosageForm || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">规格</span>
              <span class="info-value">{{ currentDrug.specification || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">生产厂商</span>
              <span class="info-value">{{ currentDrug.manufacturer || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">批准文号</span>
              <span class="info-value">{{ currentDrug.approvalNumber || '—' }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="section-title">适应症</h4>
          <div v-if="detailIndications.length" class="tag-list">
            <span v-for="t in detailIndications" :key="t" class="tag tag-neutral">{{ t }}</span>
          </div>
          <p v-else class="empty-text">—</p>
        </div>

        <div class="detail-section">
          <h4 class="section-title">禁忌症</h4>
          <div v-if="detailContraindications.length" class="tag-list">
            <span v-for="t in detailContraindications" :key="t" class="tag tag-danger">{{ t }}</span>
          </div>
          <p v-else class="empty-text">—</p>
        </div>

        <div class="detail-section">
          <h4 class="section-title">不良反应</h4>
          <div v-if="detailAdverseReactions.length" class="tag-list">
            <span v-for="t in detailAdverseReactions" :key="t" class="tag tag-neutral">{{ t }}</span>
          </div>
          <p v-else class="empty-text">—</p>
        </div>

        <div class="detail-section">
          <h4 class="section-title">用法用量</h4>
          <p class="section-text">{{ currentDrug.dosage || '—' }}</p>
        </div>

        <div class="detail-section">
          <h4 class="section-title">注意事项</h4>
          <p class="section-text">{{ currentDrug.precautions || '—' }}</p>
        </div>

        <div class="detail-section">
          <h4 class="section-title">储存条件</h4>
          <p class="section-text">{{ currentDrug.storage || '—' }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { drugService } from '@/api/services/drug.service'
import {
  type Drug,
  type DrugInteraction,
  type BadgeTone,
  drugTitle,
  drugSubtitle,
  parseArray,
  interactionTypeText,
  severityText,
  severityTone,
  interactionTone
} from '@/api/types/drug'

/** 搜索与筛选状态 */
const keyword = ref('')
const selectedClass = ref('')
const classes = ref<string[]>([])
const drugs = ref<Drug[]>([])
const loading = ref(false)

/** 相互作用检查状态 */
const selected = ref<string[]>([])
/** 已选药品对象表（id → Drug），跨搜索保留用药方案并支持名称反查 */
const selectedDrugs = ref<Record<string, Drug>>({})
const interactions = ref<DrugInteraction[]>([])
const checking = ref(false)

/** 详情弹窗状态 */
const detailVisible = ref(false)
const currentDrug = ref<Drug | null>(null)

/** 相互作用面板引用（移动端浮动按钮滚动定位用） */
const panelRef = ref<HTMLElement | null>(null)

/** 药品是否已勾选 */
const isSelected = (id: string) => selected.value.includes(id)

/** 由 ID 反查药品对象：优先已选方案表，其次当前搜索结果 */
const drugById = (id: string) =>
  selectedDrugs.value[id] || drugs.value.find((x) => x.id === id)

/** 由 ID 反查药品名称（batch 接口不返回药品名，需从已选方案/已加载列表反查） */
const drugNameById = (id: string) => {
  const d = drugById(id)
  return d ? drugTitle(d) : `#${id}`
}

/** 药品卡风险色条类：按该药与已选药品的相互作用严重度编码 */
const drugRiskClass = (id: string): string => {
  const it = interactions.value.find((x) => x.drugAId === id || x.drugBId === id)
  if (!it) return 'drug-risk-neutral'
  const t = severityTone(it.severity)
  if (t === 'danger') return 'drug-risk-danger'
  if (t === 'warning') return 'drug-risk-warning'
  return 'drug-risk-neutral'
}

/** 是否存在最高档（danger）风险 → 触发 Major Risk banner */
const hasMajorRisk = computed(() =>
  interactions.value.some((it) => severityTone(it.severity) === 'danger')
)

/** 详情弹窗标题 */
const dialogTitle = computed(() => drugTitle(currentDrug.value))

/** 详情 JSON 数组字段解析 */
const detailIndications = computed(() => parseArray(currentDrug.value?.indications))
const detailContraindications = computed(() => parseArray(currentDrug.value?.contraindications))
const detailAdverseReactions = computed(() => parseArray(currentDrug.value?.adverseReactions))

/** 配色档位 → 徽标 / 卡片 CSS 类映射 */
const TONE_BADGE: Record<BadgeTone, string> = {
  danger: 'badge-tone-danger',
  warning: 'badge-tone-warning',
  info: 'badge-tone-info',
  muted: 'badge-tone-muted'
}
const TONE_CARD: Record<BadgeTone, string> = {
  danger: 'card-tone-danger',
  warning: 'card-tone-warning',
  info: 'card-tone-info',
  muted: 'card-tone-muted'
}
const toneBadge = (t: BadgeTone) => TONE_BADGE[t]
const toneCard = (t: BadgeTone) => TONE_CARD[t]

/** 搜索：名称优先，其次按分类筛选 */
const handleSearch = async () => {
  const kw = keyword.value.trim()
  if (!kw && !selectedClass.value) {
    ElMessage.warning('请输入药品名称或选择药品分类')
    return
  }
  loading.value = true
  // 保留"当前用药方案"勾选，允许跨搜索累积药品以比较相互作用
  try {
    if (kw) {
      drugs.value = await drugService.searchByName(kw)
    } else {
      drugs.value = await drugService.getDrugsByClass(selectedClass.value)
    }
  } finally {
    loading.value = false
  }
}

/** 切换勾选状态（勾选 ≥2 由 watch 自动触发检查） */
const toggleSelect = (d: Drug) => {
  const idx = selected.value.indexOf(d.id)
  if (idx >= 0) {
    selected.value.splice(idx, 1)
    delete selectedDrugs.value[d.id]
  } else {
    selected.value.push(d.id)
    selectedDrugs.value[d.id] = d
  }
}

/** 从用药方案移除单个药品（chips 关闭按钮），剩余组合由 watch 自动重新检查 */
const removeSelected = (id: string) => {
  selected.value = selected.value.filter((x) => x !== id)
  delete selectedDrugs.value[id]
}

/** 清空已选药品（关闭相互作用面板） */
const clearSelected = () => {
  selected.value = []
  selectedDrugs.value = {}
  interactions.value = []
}

/** 勾选 ≥2 个药品后自动批量检查相互作用 */
watch(
  selected,
  async (ids) => {
    if (ids.length < 2) {
      interactions.value = []
      return
    }
    checking.value = true
    try {
      interactions.value = await drugService.checkBatch([...ids])
    } finally {
      checking.value = false
    }
  },
  { deep: true }
)

/** 打开药品详情（搜索返回完整 Drug 对象，直接复用） */
const openDetail = (d: Drug) => {
  currentDrug.value = d
  detailVisible.value = true
}

/** 移动端浮动按钮：滚动到相互作用面板 */
const scrollToPanel = async () => {
  await nextTick()
  panelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

// 初始化：加载分类列表（初始展示空态引导搜索）
onMounted(async () => {
  classes.value = await drugService.getClasses()
})
</script>

<style scoped>
/* ===== 页面容器 ===== */
.drug-search-page {
  max-width: 80rem;
  margin: 0 auto;
  padding-bottom: 3rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* ===== 搜索卡 ===== */
.search-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1.25rem 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  flex-shrink: 0;
}

.search-row {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

@media (min-width: 768px) {
  .search-row {
    flex-direction: row;
    align-items: flex-end;
  }
}

.search-name {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.search-class {
  width: 100%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

@media (min-width: 768px) {
  .search-class {
    width: 14rem;
  }
}

.field-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  margin-bottom: 0.375rem;
  display: block;
}

.class-select {
  width: 100%;
}

.input-group {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.keyword-input {
  flex: 1;
}

.search-btn {
  flex-shrink: 0;
}

.input-prefix-icon {
  font-size: 1.125rem;
  color: var(--theme-on-surface-variant);
}

/* ===== 主区：左网格 + 右面板 ===== */
.main-area {
  display: flex;
  gap: 1.25rem;
  align-items: flex-start;
}

.grid-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* ===== 当前用药方案 ===== */
.regimen-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1rem 1.25rem;
  flex-shrink: 0;
}

.regimen-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0 0 0.125rem;
}

.regimen-sub {
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
  margin: 0 0 0.75rem;
}

.regimen-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.drug-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.375rem 0.25rem 0.75rem;
  border-radius: 9999px;
  background: var(--theme-primary-soft);
  border: 1px solid rgba(0, 22, 48, 0.2);
  color: var(--theme-primary);
  font-size: 0.8125rem;
  font-weight: 500;
}

.chip-icon {
  font-size: 1rem;
}

.chip-name {
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--theme-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.125rem;
  border-radius: 9999px;
  transition: color 150ms, background 150ms;
}

.chip-remove:hover {
  color: var(--theme-error);
  background: var(--theme-error-container);
}

.chip-remove .material-symbols-outlined {
  font-size: 1rem;
}

.chip-add {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  background: var(--theme-surface-container-high);
  border: 1px dashed var(--theme-outline);
  color: var(--theme-on-surface-variant);
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 150ms, color 150ms;
}

.chip-add:hover {
  background: var(--theme-surface-container);
  color: var(--theme-primary);
}

.chip-add .material-symbols-outlined {
  font-size: 1rem;
}

/* ===== 药品卡片网格 ===== */
.drug-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

@media (min-width: 1280px) {
  .drug-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.drug-card {
  position: relative;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}

.drug-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border-color: var(--theme-primary);
}

.drug-card-selected {
  border: 2px solid var(--theme-primary);
  box-shadow: 0 4px 12px rgba(0, 22, 48, 0.12);
}

/* 左侧风险色条（绝对定位） */
.drug-accent {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 0.25rem;
  background: var(--theme-outline-variant);
}

.drug-risk-danger {
  background: var(--theme-error);
}

.drug-risk-warning {
  background: var(--theme-tertiary-container);
}

.drug-risk-neutral {
  background: var(--theme-outline-variant);
}

/* 右上角勾选框 */
.select-box {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 0.25rem;
  border: 2px solid var(--theme-outline-variant);
  background: var(--theme-surface-container-lowest);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-on-primary);
  z-index: 2;
  transition: all 0.15s;
}

.select-box:hover {
  border-color: var(--theme-primary);
}

.select-box-checked {
  background: var(--theme-primary);
  border-color: var(--theme-primary);
}

.select-box-checked .material-symbols-outlined {
  font-size: 1rem;
}

/* 卡片主体 */
.card-body {
  padding: 0.75rem 1rem 0.75rem 1.25rem;
  border-bottom: 1px solid rgba(195, 198, 208, 0.5);
}

.drug-name {
  font-size: 1rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0 0 0.125rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 1.5rem;
  transition: color 150ms;
}

.drug-card:hover .drug-name {
  color: var(--theme-primary);
}

.drug-subtitle {
  font-size: 0.75rem;
  color: var(--theme-outline);
  margin: 0 0 0.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge-row {
  display: flex;
  gap: 0.375rem;
  flex-wrap: wrap;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 8px;
  border-radius: 0.25rem;
  font-size: 0.6875rem;
  font-weight: 600;
  line-height: 1.3;
}

.badge-class {
  background: var(--theme-surface-container);
  color: var(--theme-on-surface-variant);
}

.badge-risk {
  color: var(--theme-on-error);
}

.badge-risk-danger {
  background: var(--theme-error);
}

.badge-risk-warning {
  background: var(--theme-tertiary-container);
  color: var(--theme-on-tertiary-container);
}

.badge-interactions {
  background: rgba(186, 26, 26, 0.12);
  color: var(--theme-error);
  border: 1px solid rgba(186, 26, 26, 0.25);
}

.badge-icon {
  font-size: 0.875rem;
}

/* 卡片底部：规格 / 厂商 */
.card-footer {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 8px;
  padding: 0.5rem 0.75rem 0.5rem 1.25rem;
  background: var(--theme-surface-container-low);
  font-size: 0.75rem;
}

.footer-label {
  color: var(--theme-on-surface-variant);
  font-weight: 600;
}

.footer-value {
  color: var(--theme-on-surface-variant);
}

.truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 相互作用分析面板 ===== */
.interaction-panel {
  width: 400px;
  flex-shrink: 0;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  overflow: hidden;
  position: sticky;
  top: 1.25rem;
  max-height: calc(100vh - 7rem);
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: var(--theme-surface-container);
  border-bottom: 1px solid var(--theme-outline-variant);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 1rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0;
}

.panel-icon {
  color: var(--theme-error);
  font-size: 1.25rem;
}

.panel-close {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--theme-on-surface-variant);
  padding: 0.25rem;
  display: flex;
  align-items: center;
  border-radius: 0.25rem;
}

.panel-close:hover {
  color: var(--theme-on-surface);
  background: var(--theme-surface-container-high);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* Major Risk banner */
.risk-banner {
  padding: 0.875rem 1rem;
  background: rgba(186, 26, 26, 0.08);
  border: 1px solid rgba(186, 26, 26, 0.3);
  border-radius: 0.75rem;
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
  flex-shrink: 0;
}

.risk-banner-icon {
  color: var(--theme-error);
  margin-top: 0.125rem;
  font-size: 1.375rem;
  font-variation-settings: 'FILL' 1;
}

.risk-banner-title {
  font-size: 0.875rem;
  font-weight: 700;
  color: var(--theme-error);
  margin: 0 0 0.125rem;
}

.risk-banner-desc {
  font-size: 0.8125rem;
  color: var(--theme-on-surface-variant);
  line-height: 1.5;
  margin: 0;
}

.panel-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* 相互作用结果卡片 */
.interaction-card {
  border: 1px solid;
  border-radius: 0.5rem;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.card-tone-danger {
  background: rgba(186, 26, 26, 0.06);
  border-color: rgba(186, 26, 26, 0.3);
}

.card-tone-warning {
  background: rgba(0, 48, 45, 0.05);
  border-color: rgba(0, 48, 45, 0.25);
}

.card-tone-info {
  background: rgba(0, 71, 141, 0.05);
  border-color: rgba(0, 71, 141, 0.25);
}

.card-tone-muted {
  background: var(--theme-surface-container-low);
  border-color: var(--theme-outline-variant);
}

.interaction-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.drug-pair {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
}

.pair-name {
  max-width: 7rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pair-icon {
  font-size: 1rem;
  color: var(--theme-outline);
}

.type-badge {
  padding: 2px 8px;
  border-radius: 0.25rem;
  font-size: 0.625rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.sev {
  opacity: 0.9;
}

.badge-tone-danger {
  background: var(--theme-error);
  color: var(--theme-on-error);
}

.badge-tone-warning {
  background: var(--theme-tertiary-container);
  color: var(--theme-on-tertiary-container);
}

.badge-tone-info {
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
}

.badge-tone-muted {
  background: var(--theme-surface-container);
  color: var(--theme-on-surface-variant);
}

.interaction-desc,
.interaction-mech {
  font-size: 0.8125rem;
  color: var(--theme-on-surface);
  line-height: 1.5;
  margin: 0;
}

.interaction-mech {
  color: var(--theme-on-surface-variant);
}

.interaction-rec {
  display: flex;
  gap: 0.375rem;
  align-items: flex-start;
  background: rgba(186, 26, 26, 0.06);
  border: 1px solid rgba(186, 26, 26, 0.25);
  border-radius: 0.375rem;
  padding: 0.5rem 0.625rem;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--theme-on-surface);
  line-height: 1.5;
}

.rec-icon {
  color: var(--theme-error);
  font-size: 1.125rem;
  margin-top: 1px;
  flex-shrink: 0;
}

.panel-loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--theme-on-surface-variant);
  font-size: 0.8125rem;
  padding: 1rem 0;
}

.loading-dot {
  width: 1rem;
  height: 1rem;
  border-radius: 50%;
  border: 2px solid var(--theme-primary-container);
  border-top-color: var(--theme-primary);
  animation: spin 0.6s linear infinite;
  flex-shrink: 0;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  color: var(--theme-outline);
  padding: 2rem 0;
  font-size: 0.8125rem;
}

.panel-empty .material-symbols-outlined {
  font-size: 2rem;
  color: var(--theme-success);
}

/* ===== 空态 ===== */
.empty-box {
  min-height: 20rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  text-align: center;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 2rem;
}

.empty-icon {
  width: 4rem;
  height: 4rem;
  border-radius: 9999px;
  background: var(--theme-surface-container);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-primary);
  margin-bottom: 0.5rem;
}

.empty-icon .material-symbols-outlined {
  font-size: 2.25rem;
}

.empty-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--theme-on-surface);
}

.empty-desc {
  font-size: 0.875rem;
  color: var(--theme-outline);
  max-width: 28rem;
  line-height: 1.6;
  margin-bottom: 1rem;
}

/* ===== 移动端浮动按钮 ===== */
.fab {
  position: fixed;
  bottom: 1.5rem;
  right: 1.25rem;
  z-index: 50;
  display: none;
  align-items: center;
  gap: 0.375rem;
  background: var(--theme-primary);
  color: var(--theme-on-primary);
  padding: 0.625rem 1rem;
  border: none;
  border-radius: 9999px;
  font-size: 0.8125rem;
  font-weight: 600;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.2);
  cursor: pointer;
}

.fab .material-symbols-outlined {
  font-size: 1.125rem;
}

.fab:hover {
  background: var(--theme-primary-strong);
}

@media (max-width: 1024px) {
  .fab {
    display: flex;
  }
}

/* ===== 响应式：小屏堆叠 ===== */
@media (max-width: 1024px) {
  .main-area {
    flex-direction: column;
  }
  .interaction-panel {
    width: 100%;
    position: static;
    max-height: none;
  }
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>

<!-- 药品详情弹窗样式（el-dialog teleport 到 body，需全局作用域） -->
<style>
.drug-detail-dialog .detail-body {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  max-height: 70vh;
  overflow-y: auto;
  padding: 0.25rem 0.125rem;
}

.drug-detail-dialog .detail-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.drug-detail-dialog .section-title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  letter-spacing: 0.02em;
  padding-bottom: 0.375rem;
  border-bottom: 1px solid rgba(195, 198, 208, 0.5);
  margin: 0;
}

.drug-detail-dialog .info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.625rem 1rem;
}

@media (max-width: 480px) {
  .drug-detail-dialog .info-grid {
    grid-template-columns: 1fr;
  }
}

.drug-detail-dialog .info-item {
  display: flex;
  gap: 0.5rem;
  font-size: 0.8125rem;
}

.drug-detail-dialog .info-label {
  color: var(--theme-outline);
  flex-shrink: 0;
}

.drug-detail-dialog .info-value {
  color: var(--theme-on-surface);
  font-weight: 500;
}

.drug-detail-dialog .tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.drug-detail-dialog .tag {
  padding: 0.25rem 0.625rem;
  border-radius: 0.375rem;
  font-size: 0.75rem;
  line-height: 1.4;
}

.drug-detail-dialog .tag-neutral {
  background: var(--theme-surface-container);
  color: var(--theme-on-surface-variant);
}

.drug-detail-dialog .tag-danger {
  background: rgba(186, 26, 26, 0.08);
  color: var(--theme-error);
  border: 1px solid rgba(186, 26, 26, 0.3);
}

.drug-detail-dialog .empty-text {
  color: var(--theme-outline);
  font-size: 0.8125rem;
  margin: 0;
}

.drug-detail-dialog .section-text {
  color: var(--theme-on-surface);
  font-size: 0.875rem;
  line-height: 1.7;
  margin: 0;
  white-space: pre-wrap;
}
</style>
