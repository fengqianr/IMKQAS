<template>
  <div class="drug-search-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">药物查询</h1>
        <p class="page-subtitle">检索药品信息，检查药物相互作用</p>
      </div>
    </div>

    <!-- 搜索卡片 -->
    <div class="search-card">
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
    </div>

    <!-- 主区：左侧药品网格 + 右侧相互作用面板 -->
    <div class="main-area">
      <div class="grid-wrap">
        <!-- 药品卡片网格 -->
        <div v-if="drugs.length" class="drug-grid">
          <div
            v-for="d in drugs"
            :key="d.id"
            class="drug-card"
            :class="{ 'drug-card-selected': isSelected(d.id) }"
            @click="openDetail(d)"
          >
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
                <span v-if="d.hasInteractions === 1" class="badge badge-interactions">
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
            <span class="material-symbols-outlined panel-icon">science</span>
            相互作用分析
          </h3>
          <button class="panel-close" @click="clearSelected">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>
        <div class="panel-body">
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
const selected = ref<number[]>([])
const interactions = ref<DrugInteraction[]>([])
const checking = ref(false)

/** 详情弹窗状态 */
const detailVisible = ref(false)
const currentDrug = ref<Drug | null>(null)

/** 相互作用面板引用（移动端浮动按钮滚动定位用） */
const panelRef = ref<HTMLElement | null>(null)

/** 药品是否已勾选 */
const isSelected = (id: number) => selected.value.includes(id)

/** 由 ID 反查药品名称（batch 接口不返回药品名，需从已加载列表反查） */
const drugNameById = (id: number) => {
  const d = drugs.value.find((x) => x.id === id)
  return d ? drugTitle(d) : `#${id}`
}

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
  // 切换搜索结果时清空已选药品与检查结果，避免 ID 反查失效
  selected.value = []
  interactions.value = []
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
  } else {
    selected.value.push(d.id)
  }
}

/** 清空已选药品（关闭相互作用面板） */
const clearSelected = () => {
  selected.value = []
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
}

/* ===== 页头 ===== */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  padding: 0 0 1.5rem;
  border-bottom: 1px solid #c2c6d4;
  margin-bottom: 1.5rem;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: #191c1d;
  margin-bottom: 0.5rem;
  letter-spacing: -0.01em;
}

.page-subtitle {
  font-size: 0.875rem;
  color: #4a5f83;
}

/* ===== 搜索卡片 ===== */
.search-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  margin-bottom: 1.5rem;
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
  color: #4a5f83;
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
  color: #6e797e;
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
}

/* ===== 药品卡片网格 ===== */
.drug-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1rem;
}

.drug-card {
  position: relative;
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.5rem;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}

.drug-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border-color: #727783;
}

.drug-card-selected {
  border: 2px solid #005eb8;
  box-shadow: 0 4px 12px rgba(0, 94, 184, 0.12);
}

/* 右上角勾选框 */
.select-box {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 0.25rem;
  border: 2px solid #c2c6d4;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  z-index: 2;
  transition: all 0.15s;
}

.select-box:hover {
  border-color: #005eb8;
}

.select-box-checked {
  background: #005eb8;
  border-color: #005eb8;
}

.select-box-checked .material-symbols-outlined {
  font-size: 1rem;
}

/* 卡片主体 */
.card-body {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e2e8f0;
}

.drug-name {
  font-size: 1rem;
  font-weight: 600;
  color: #191c1d;
  margin: 0 0 0.125rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 1.5rem;
}

.drug-subtitle {
  font-size: 0.75rem;
  color: #727783;
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
  border-radius: 4px;
  font-size: 0.6875rem;
  font-weight: 600;
  line-height: 1.3;
}

.badge-class {
  background: #e7e8e9;
  color: #424752;
}

.badge-interactions {
  background: #ba1a1a;
  color: #ffffff;
}

.badge-icon {
  font-size: 0.875rem;
}

/* 卡片底部：规格 / 厂商 */
.card-footer {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 8px;
  padding: 0.5rem 0.75rem;
  background: #f8f9fa;
  font-size: 0.75rem;
}

.footer-label {
  color: #424752;
  font-weight: 600;
}

.footer-value {
  color: #4a5f83;
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
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.5rem;
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
  background: #edeeef;
  border-bottom: 1px solid #c2c6d4;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 1rem;
  font-weight: 600;
  color: #191c1d;
  margin: 0;
}

.panel-icon {
  color: #ba1a1a;
  font-size: 1.25rem;
}

.panel-close {
  border: none;
  background: none;
  cursor: pointer;
  color: #424752;
  padding: 0.25rem;
  display: flex;
  align-items: center;
  border-radius: 0.25rem;
}

.panel-close:hover {
  color: #191c1d;
  background: #e7e8e9;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
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
  background: #fef2f2;
  border-color: #fecaca;
}

.card-tone-warning {
  background: #fff7ed;
  border-color: #fcd9a8;
}

.card-tone-info {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.card-tone-muted {
  background: #f8fafc;
  border-color: #e2e8f0;
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
  color: #191c1d;
}

.pair-name {
  max-width: 7rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pair-icon {
  font-size: 1rem;
  color: #727783;
}

.type-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.625rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.sev {
  opacity: 0.9;
}

.badge-tone-danger {
  background: #ba1a1a;
  color: #ffffff;
}

.badge-tone-warning {
  background: #793100;
  color: #ffffff;
}

.badge-tone-info {
  background: #c0d5ff;
  color: #004a9e;
}

.badge-tone-muted {
  background: #e7e8e9;
  color: #424752;
}

.interaction-desc,
.interaction-mech {
  font-size: 0.8125rem;
  color: #191c1d;
  line-height: 1.5;
  margin: 0;
}

.interaction-mech {
  color: #424752;
}

.interaction-rec {
  display: flex;
  gap: 0.375rem;
  align-items: flex-start;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(186, 26, 26, 0.2);
  border-radius: 0.375rem;
  padding: 0.5rem 0.625rem;
  font-size: 0.8125rem;
  font-weight: 500;
  color: #191c1d;
  line-height: 1.5;
}

.rec-icon {
  color: #ba1a1a;
  font-size: 1.125rem;
  margin-top: 1px;
  flex-shrink: 0;
}

.panel-loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #4a5f83;
  font-size: 0.8125rem;
  padding: 1rem 0;
}

.loading-dot {
  width: 1rem;
  height: 1rem;
  border-radius: 50%;
  border: 2px solid #c0d5ff;
  border-top-color: #005eb8;
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
  color: #727783;
  padding: 2rem 0;
  font-size: 0.8125rem;
}

.panel-empty .material-symbols-outlined {
  font-size: 2rem;
  color: #2e7d32;
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
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  padding: 2rem;
}

.empty-icon {
  width: 4rem;
  height: 4rem;
  border-radius: 9999px;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #005eb8;
  margin-bottom: 0.5rem;
}

.empty-icon .material-symbols-outlined {
  font-size: 2.25rem;
}

.empty-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #191c1d;
}

.empty-desc {
  font-size: 0.875rem;
  color: #727783;
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
  background: #005eb8;
  color: #ffffff;
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
  background: #00478d;
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
  color: #424752;
  letter-spacing: 0.02em;
  padding-bottom: 0.375rem;
  border-bottom: 1px solid #e2e8f0;
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
  color: #727783;
  flex-shrink: 0;
}

.drug-detail-dialog .info-value {
  color: #191c1d;
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
  background: #e7e8e9;
  color: #424752;
}

.drug-detail-dialog .tag-danger {
  background: #fef2f2;
  color: #ba1a1a;
  border: 1px solid #fecaca;
}

.drug-detail-dialog .empty-text {
  color: #727783;
  font-size: 0.8125rem;
  margin: 0;
}

.drug-detail-dialog .section-text {
  color: #191c1d;
  font-size: 0.875rem;
  line-height: 1.7;
  margin: 0;
  white-space: pre-wrap;
}
</style>
