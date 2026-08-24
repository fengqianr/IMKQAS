<template>
  <div class="term-review-page">
    <!-- 主内容区域（导航由外部布局框架提供） -->
    <main class="term-review-main">
      <div class="term-review-main-inner">
        <!-- 统计卡片：匹配原型 4 列布局 -->
        <section class="custom-grid custom-grid-cols-4 custom-gap-6 custom-mb-8">
          <!-- 卡片1：待审核数 -->
          <div class="stat-card">
            <div>
              <p class="stat-card-label">待审核数</p>
              <h3 class="stat-card-value">{{ stats.pendingCount }}</h3>
              <p class="stat-card-sub">
                <span class="material-symbols-outlined text-sm text-error">trending_up</span>
                较昨日持平
              </p>
            </div>
            <div class="stat-card-icon-wrap" style="background-color: rgba(0, 71, 141, 0.1)">
              <span class="material-symbols-outlined text-primary">pending_actions</span>
            </div>
          </div>
          <!-- 卡片2：未映射率 -->
          <div class="stat-card" :class="{ 'border-l-4 border-l-error': stats.alertTriggered }">
            <div>
              <p class="stat-card-label">未映射率</p>
              <h3 class="stat-card-value-neutral" :class="{ 'text-error': stats.alertTriggered }">
                {{ Number(stats.unmappedRate).toFixed(1) }}%
              </h3>
              <p class="stat-card-sub">
                <span class="material-symbols-outlined text-sm text-primary">check_circle</span>
                数据状态良好
              </p>
            </div>
            <div class="stat-card-icon-wrap bg-secondary-fixed">
              <span class="material-symbols-outlined" style="color: #021b3c">analytics</span>
            </div>
          </div>
          <!-- 卡片3：今日已审核 -->
          <div class="stat-card">
            <div>
              <p class="stat-card-label">今日已审核</p>
              <h3 class="stat-card-value-neutral">{{ stats.approvedTodayCount }}</h3>
              <p class="stat-card-sub">
                <span class="material-symbols-outlined text-sm text-on-surface-variant">notifications</span>
                等待开始任务
              </p>
            </div>
            <div class="stat-card-icon-wrap bg-surface-container-high">
              <span class="material-symbols-outlined text-on-surface-variant">fact_check</span>
            </div>
          </div>
          <!-- 卡片4：告警状态 -->
          <div class="stat-card">
            <div>
              <p class="stat-card-label">告警状态</p>
              <h3 class="stat-card-value">{{ stats.alertTriggered ? '告警中' : '正常' }}</h3>
              <p class="stat-card-sub">
                <span class="material-symbols-outlined text-sm text-primary">verified</span>
                系统运行稳定
              </p>
            </div>
            <div class="stat-card-icon-wrap" style="background-color: rgba(0, 71, 141, 0.1)">
              <span class="material-symbols-outlined text-primary">security</span>
            </div>
          </div>
        </section>

        <!-- 主内容：词条审核表格 -->
        <section class="table-card">
          <!-- 表格头部栏 -->
          <div class="table-header-bar">
            <h2 class="table-header-title">待审核列表</h2>
            <button class="btn-batch" :disabled="selectedRows.length === 0" @click="showBatchApprove">
              <span class="material-symbols-outlined text-sm">done_all</span>
              批量通过
            </button>
          </div>

          <!-- 筛选栏 -->
          <div class="filter-bar">
            <div class="custom-flex custom-items-center custom-gap-4">
              <div class="filter-search-wrap">
                <span class="filter-search-icon material-symbols-outlined">search</span>
                <input
                  v-model="searchKeyword"
                  class="filter-search-input"
                  placeholder="搜索词条、上下文或LLM猜测..."
                  type="text"
                  @input="debouncedFilter"
                />
              </div>
              <select v-model="filterStatus" class="filter-select" @change="loadData">
                <option value="PENDING">待审核</option>
                <option value="APPROVED">已通过</option>
                <option value="REJECTED">已拒绝</option>
                <option value="ALL">全部</option>
              </select>
            </div>
            <span class="text-xs text-on-surface-variant">共 {{ total }} 条记录</span>
          </div>

          <!-- 表格 -->
          <div class="custom-overflow-x-auto">
            <table class="term-table">
              <colgroup>
                <col class="col-select" style="width: 40px" />
                <col class="col-term" style="width: 140px" />
                <!-- 上下文列：宽度由拖拽手柄控制 -->
                <col class="col-context" :style="{ width: contextColWidth + 'px' }" />
                <col class="col-entity" style="width: 90px" />
                <col class="col-llm" style="width: 160px" />
                <col class="col-confidence" style="width: 100px" />
                <col class="col-count" style="width: 60px" />
                <col class="col-date" style="width: 100px" />
                <col class="col-standard" style="width: 230px" />
              </colgroup>
              <thead>
                <tr>
                  <th class="custom-w-10">
                    <input type="checkbox" :checked="isAllSelected" @change="toggleSelectAll" />
                  </th>
                  <th>口语表达</th>
                  <th class="th-context">
                    上下文
                    <span class="col-resizer" title="拖拽调整列宽" @mousedown.prevent="startResize" />
                  </th>
                  <th>实体类型</th>
                  <th>LLM猜测</th>
                  <th>置信度</th>
                  <th>频次</th>
                  <th>首次出现</th>
                  <!-- 标准化列：固定在表格右侧，编辑操作始终可见 -->
                  <th class="col-sticky">标准化</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="9">
                    <LoadingState />
                  </td>
                </tr>
                <tr v-else-if="displayTerms.length === 0">
                  <td colspan="9">
                    <EmptyState icon="text_snippet" title="暂无词条数据" />
                  </td>
                </tr>
                <tr v-for="row in displayTerms" :key="row.id" :class="{ 'row-selected': selectedIds.has(row.id) }">
                  <td>
                    <input type="checkbox" :checked="selectedIds.has(row.id)" @change="toggleRow(row)" />
                  </td>
                  <td>
                    <span class="term-name">{{ row.term }}</span>
                  </td>
                  <td>
                    <!-- 悬停 title 提示查看被截断的上下文全文 -->
                    <span class="term-context" :title="row.contextQuery || '-'">{{ row.contextQuery || '-' }}</span>
                  </td>
                  <td>
                    <span :class="['entity-badge', entityBadgeClass(row.guessedEntityType)]">
                      {{ row.guessedEntityType || '-' }}
                    </span>
                  </td>
                  <td class="text-sm text-on-surface-variant custom-max-w-36 truncate">
                    {{ row.llmGuess || '-' }}
                  </td>
                  <td>
                    <div v-if="row.llmConfidence != null" class="confidence-bar-wrap">
                      <div class="confidence-bar-bg">
                        <div class="confidence-bar-fill" :style="{ width: row.llmConfidence * 100 + '%' }" />
                      </div>
                      <span class="confidence-bar-text" :class="confidenceClass(row.llmConfidence)">
                        {{ (row.llmConfidence * 100).toFixed(0) }}%
                      </span>
                    </div>
                    <span v-else class="text-xs text-on-surface-variant">-</span>
                  </td>
                  <td class="text-sm text-on-surface-variant text-center">{{ row.occurrenceCount }}</td>
                  <td class="text-xs text-on-surface-variant">{{ formatDate(row.firstSeenAt) }}</td>
                  <td class="col-sticky">
                    <div v-if="row.status === 'PENDING'" class="custom-flex custom-items-center custom-gap-2">
                      <input v-model="approveInputs[row.id]" class="standard-input" placeholder="输入标准术语" />
                      <button class="action-btn-approve" title="通过" @click="approveOne(row)">
                        <span class="material-symbols-outlined text-lg">check_circle</span>
                      </button>
                      <button class="action-btn-reject" title="拒绝" @click="rejectOne(row)">
                        <span class="material-symbols-outlined text-lg">cancel</span>
                      </button>
                    </div>
                    <StatusBadge
                      v-else
                      :tone="row.status === 'APPROVED' ? 'success' : 'danger'"
                      :icon="row.status === 'APPROVED' ? 'check' : 'block'"
                    >
                      {{ row.status === 'APPROVED' ? '已通过' : '已拒绝' }}
                    </StatusBadge>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 分页：匹配原型页码式分页 -->
          <Pager
            v-model:current="currentPage"
            :total-pages="totalPages"
            :info="`显示 ${displayTerms.length} 条，共 ${total} 条`"
            @change="loadData"
          />
        </section>
        <!-- /table-card -->
      </div>
      <!-- /term-review-main-inner -->
    </main>

    <!-- 批量标注弹窗 -->
    <Teleport to="body">
      <div v-if="batchDialogVisible" class="modal-overlay" @click.self="batchDialogVisible = false">
        <div class="modal-card">
          <h3 class="font-headline font-bold text-lg text-on-surface custom-mb-2">批量标注</h3>
          <p class="text-sm text-on-surface-variant custom-mb-6">
            已选择 <b class="text-primary">{{ selectedRows.length }}</b> 个词条，请输入统一的标准术语
          </p>
          <input v-model="batchStandardTerm" class="modal-input custom-mb-6" placeholder="标准医学术语" />
          <div class="custom-flex custom-items-center custom-gap-3 custom-justify-end">
            <button class="btn-cancel" @click="batchDialogVisible = false">取消</button>
            <button class="btn-confirm" :disabled="batchSubmitting" @click="doBatchApprove">
              {{ batchSubmitting ? '提交中...' : '确认标注' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminService } from '@/api/services/admin.service'
import type { UnmappedTermItem, AdminStats } from '@/api/services/admin.service'
import { apiErrorMessage } from '@/utils/error'
import StatusBadge from '@/components/StatusBadge.vue'
import Pager from '@/components/Pager.vue'
import LoadingState from '@/components/LoadingState.vue'
import EmptyState from '@/components/EmptyState.vue'

const terms = ref<UnmappedTermItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const filterStatus = ref('PENDING')
const searchKeyword = ref('')
const selectedRows = ref<UnmappedTermItem[]>([])
const selectedIds = computed(() => new Set(selectedRows.value.map((r) => r.id)))
const approveInputs = ref<Record<number, string>>({})

const stats = ref<AdminStats>({
  pendingCount: 0,
  approvedTodayCount: 0,
  unmappedRate: 0,
  alertThreshold: 5.0,
  alertTriggered: false,
  totalAlertCount: 0,
  topUnmappedTerms: []
})

const batchDialogVisible = ref(false)
const batchStandardTerm = ref('')
const batchSubmitting = ref(false)

let filterTimeout: ReturnType<typeof setTimeout> | null = null

const isAllSelected = computed(() => {
  return displayTerms.value.length > 0 && displayTerms.value.every((t) => selectedIds.value.has(t.id))
})

const displayTerms = computed(() => {
  if (!searchKeyword.value) return terms.value
  const kw = searchKeyword.value.toLowerCase()
  return terms.value.filter(
    (t) =>
      t.term.toLowerCase().includes(kw) ||
      (t.contextQuery && t.contextQuery.toLowerCase().includes(kw)) ||
      (t.llmGuess && t.llmGuess.toLowerCase().includes(kw))
  )
})

function debouncedFilter() {
  if (filterTimeout) clearTimeout(filterTimeout)
  filterTimeout = setTimeout(() => {
    currentPage.value = 1
    loadData()
  }, 400)
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminService.getUnmappedTerms({
      page: currentPage.value,
      size: pageSize.value,
      status: filterStatus.value === 'ALL' ? undefined : filterStatus.value
    })
    terms.value = res.data || []
    total.value = res.total
    currentPage.value = res.page
  } catch (e: any) {
    ElMessage.error('加载词条列表失败: ' + apiErrorMessage(e, '未知错误'))
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await adminService.getStats()
  } catch {
    // 静默失败
  }
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedRows.value = []
  } else {
    selectedRows.value = [...displayTerms.value]
  }
}

function toggleRow(row: UnmappedTermItem) {
  const idx = selectedRows.value.findIndex((r) => r.id === row.id)
  if (idx >= 0) {
    selectedRows.value.splice(idx, 1)
  } else {
    selectedRows.value.push(row)
  }
}

async function approveOne(row: UnmappedTermItem) {
  const standardTerm = approveInputs.value[row.id]?.trim()
  if (!standardTerm) {
    ElMessage.warning('请输入标准术语')
    return
  }
  try {
    await adminService.approveTerm(row.id, standardTerm)
    ElMessage.success(`标注成功: ${row.term} → ${standardTerm}`)
    delete approveInputs.value[row.id]
    await loadData()
    await loadStats()
  } catch (e: any) {
    ElMessage.error('标注失败: ' + apiErrorMessage(e, '未知错误'))
  }
}

async function rejectOne(row: UnmappedTermItem) {
  try {
    await ElMessageBox({
      title: '确认拒绝',
      message: `确定拒绝词条 "${row.term}" 吗？`,
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      type: 'warning',
      showCancelButton: true
    })
    await adminService.rejectTerm(row.id, '审核拒绝')
    ElMessage.info(`已拒绝: ${row.term}`)
    await loadData()
    await loadStats()
  } catch {
    // 用户取消
  }
}

function showBatchApprove() {
  batchStandardTerm.value = ''
  batchDialogVisible.value = true
}

async function doBatchApprove() {
  const term = batchStandardTerm.value?.trim()
  if (!term) {
    ElMessage.warning('请输入标准术语')
    return
  }
  batchSubmitting.value = true
  try {
    const requests = selectedRows.value.map((r) => ({
      id: r.id,
      standardTerm: term
    }))
    const result = await adminService.batchApprove(requests)
    ElMessage.success(`批量标注完成: 成功 ${result.success} 条`)
    batchDialogVisible.value = false
    selectedRows.value = []
    batchStandardTerm.value = ''
    await loadData()
    await loadStats()
  } catch (e: any) {
    ElMessage.error('批量标注失败: ' + apiErrorMessage(e, '未知错误'))
  } finally {
    batchSubmitting.value = false
  }
}

function confidenceClass(conf: number): string {
  if (conf >= 0.85) return 'text-success'
  if (conf >= 0.6) return 'text-processing'
  return 'text-red-500'
}

function entityBadgeClass(type: string): string {
  if (!type) return 'entity-badge-default'
  const t = type.toLowerCase()
  if (t === 'symptom' || t === '症状') return 'entity-badge-symptom'
  if (t === 'drug' || t === '药品' || t === '药物') return 'entity-badge-drug'
  if (t === 'disease' || t === '疾病') return 'entity-badge-symptom'
  if (t === 'examination' || t === '检查') return 'entity-badge-drug'
  return 'entity-badge-default'
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

onMounted(() => {
  loadData()
  loadStats()
})

// ========== 上下文列宽可拖拽（列宽伸缩） ==========
// 初始宽度（px）；拖拽范围限制在 120~480，避免过窄/过宽
const contextColWidth = ref(220)
let resizeStartX = 0
let resizeStartWidth = 0

/** 同步上下文列宽度到 colgroup 与文本截断宽度（内容区 = 列宽 - 单元格 padding 32px） */
function applyContextColWidth(width: number) {
  contextColWidth.value = width
  document.querySelectorAll<HTMLElement>('.term-context').forEach((el) => {
    el.style.maxWidth = `${width - 32}px`
  })
}

function onResizeMove(e: MouseEvent) {
  const next = resizeStartWidth + (e.clientX - resizeStartX)
  applyContextColWidth(Math.min(480, Math.max(120, next)))
}

function startResize(e: MouseEvent) {
  resizeStartX = e.clientX
  resizeStartWidth = contextColWidth.value
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function stopResize() {
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onUnmounted(() => {
  stopResize()
})
</script>

<style scoped>
/* ===== 页面根容器：内容区高度 ===== */
.term-review-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--theme-surface); /* surface / background */
}

/* ===== 主内容区：占满布局内容区 ===== */
.term-review-main {
  flex: 1;
  overflow: visible;
  padding-top: 1.5rem;
  padding-bottom: 3rem;
  padding-left: 2rem;
  padding-right: 2rem;
}

.term-review-main-inner {
  max-width: 1600px;
  margin: 0 auto;
}

/* ===== 统计卡片 ===== */
.stat-card {
  background-color: #ffffff;
  padding: 1.5rem;
  border-radius: 0.75rem;
  box-shadow: 0 12px 40px rgba(0, 71, 141, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-card-label {
  color: #424752;
  font-size: 0.875rem;
  font-weight: 500;
  margin-bottom: 0.25rem;
}

.stat-card-value {
  font-family: 'Manrope', sans-serif;
  font-size: 1.875rem;
  font-weight: 700;
  color: var(--theme-primary);
}

.stat-card-value-neutral {
  font-family: 'Manrope', sans-serif;
  font-size: 1.875rem;
  font-weight: 700;
  color: var(--theme-on-surface);
}

.stat-card-sub {
  font-size: 0.75rem;
  color: #424752;
  margin-top: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.stat-card-icon-wrap {
  width: 3rem;
  height: 3rem;
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* ===== 表格卡片容器 ===== */
.table-card {
  background-color: #ffffff;
  border-radius: 0.75rem;
  box-shadow: 0 12px 40px rgba(0, 71, 141, 0.06);
  overflow: hidden;
}

.table-header-bar {
  padding: 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: rgba(243, 244, 245, 0.3);
}

.table-header-title {
  font-family: 'Manrope', sans-serif;
  font-size: 1.125rem;
  font-weight: 700;
}

/* ===== 筛选栏 ===== */
.filter-bar {
  padding: 0 1.5rem 1rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.filter-search-wrap {
  position: relative;
}

.filter-search-icon {
  position: absolute;
  left: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--theme-outline);
  font-size: 1.25rem;
}

.filter-search-input {
  padding-left: 2.5rem;
  padding-right: 1rem;
  padding-top: 0.625rem;
  padding-bottom: 0.625rem;
  background-color: var(--theme-surface-container-low);
  border: none;
  border-radius: 9999px;
  font-size: 0.875rem;
  width: 16rem;
  outline: none;
  transition: box-shadow 150ms;
}

.filter-search-input:focus {
  box-shadow: 0 0 0 2px rgba(0, 71, 141, 0.2);
}

.filter-search-input::placeholder {
  color: #424752;
  opacity: 0.6;
}

.filter-select {
  padding: 0.625rem 1rem;
  background-color: var(--theme-surface-container-low);
  border: none;
  border-radius: 9999px;
  font-size: 0.875rem;
  color: #424752;
  outline: none;
}

/* ===== 表格 ===== */
.term-table {
  width: 100%;
  /* 各列宽度之和：40+140+220+90+160+100+60+100+230 = 1140（上下文列可拖拽调整） */
  min-width: 1140px;
  text-align: left;
  border-collapse: collapse;
  /* 固定布局：列宽由 colgroup 控制，上下文列可拖拽调整 */
  table-layout: fixed;
}

.term-table thead tr {
  border-bottom: 1px solid rgba(194, 198, 212, 0.1);
}
.term-table tr {
  text-align: center;
}

.term-table th {
  padding: 0.75rem 1rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: #424752;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* 上下文列表头：相对定位以容纳拖拽手柄 */
.th-context {
  position: relative;
  user-select: none;
}

/* 列宽拖拽手柄：悬停高亮，拖拽改变上下文列宽度 */
.col-resizer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  background-color: transparent;
  transition: background-color 150ms;
}

.col-resizer:hover {
  background-color: rgba(0, 71, 141, 0.3);
}

.term-table th:first-child {
  padding-left: 1.5rem;
}

.term-table td {
  padding: 1rem;
  vertical-align: middle;
}

.term-table td:first-child {
  padding-left: 1.5rem;
}

.term-table tbody tr {
  transition: background-color 150ms;
  cursor: pointer;
}

.term-table tbody tr:hover {
  background-color: rgba(243, 244, 245, 0.5);
}

.term-table tbody tr.row-selected {
  background-color: rgba(243, 244, 245, 0.4);
  border-left: 4px solid var(--theme-primary);
}

.term-table tbody {
  border-top: none;
}

.term-table tbody tr {
  border-bottom: 1px solid rgba(194, 198, 212, 0.1);
}

/* 表格内文字样式 */
.term-name {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--theme-primary);
}

.term-context {
  /* 块级显示使 max-width/ellipsis 生效；宽度随上下文列拖拽同步 */
  display: block;
  font-size: 0.75rem;
  color: #424752;
  max-width: 14rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 标准化列：sticky 固定在表格右侧，编辑操作始终可见 */
.col-sticky {
  position: sticky;
  right: 0;
  background-color: #ffffff;
  box-shadow: -1px 0 0 rgba(194, 198, 212, 0.25);
}

.term-context-highlight {
  background-color: rgba(0, 71, 141, 0.1);
  padding: 0 0.25rem;
  border-radius: 0.125rem;
}

/* 实体类型标签 */
.entity-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.entity-badge-symptom {
  background-color: #d6e3ff;
  color: #021b3c;
}

.entity-badge-drug {
  background-color: #ffdbcb;
  color: #341100;
}

.entity-badge-default {
  background-color: var(--theme-surface-container);
  color: #424752;
}

/* 置信度进度条 */
.confidence-bar-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.confidence-bar-bg {
  width: 3rem;
  height: 0.375rem;
  background-color: var(--theme-surface-container);
  border-radius: 9999px;
  overflow: hidden;
}

.confidence-bar-fill {
  height: 100%;
  background-color: var(--theme-primary);
  border-radius: 9999px;
}

.confidence-bar-text {
  font-size: 10px;
  font-weight: 700;
}

/* 操作按钮 */
.action-btn-approve {
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-primary);
  border-radius: 0.25rem;
  border: none;
  background: none;
  cursor: pointer;
  transition: background-color 150ms;
}

.action-btn-approve:hover {
  background-color: rgba(0, 71, 141, 0.1);
}

.action-btn-reject {
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ba1a1a;
  border-radius: 0.25rem;
  border: none;
  background: none;
  cursor: pointer;
  transition: background-color 150ms;
}

.action-btn-reject:hover {
  background-color: rgba(186, 26, 26, 0.1);
}

/* 标准化输入 */
.standard-input {
  font-size: 0.75rem;
  border: 1px solid rgba(194, 198, 212, 0.3);
  border-radius: 0.5rem;
  padding: 0.5rem 0.75rem;
  width: 8rem;
  outline: none;
  transition: box-shadow 150ms;
}

.standard-input:focus {
  box-shadow: 0 0 0 2px rgba(0, 71, 141, 0.2);
}

/* ===== 批量按钮 ===== */
.btn-batch {
  background-color: var(--theme-primary);
  color: #ffffff;
  padding: 0.5rem 1rem;
  border-radius: 9999px;
  font-size: 0.875rem;
  font-weight: 600;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  transition: all 150ms;
}

.btn-batch:hover {
  background-color: var(--theme-brand);
}

.btn-batch:active {
  transform: scale(0.95);
}

.btn-batch:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== Material Icons ===== */
.material-symbols-outlined {
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

/* ===== 批量弹窗（保持原有） ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.3);
}

.modal-card {
  background-color: var(--theme-surface);
  border-radius: 1rem;
  padding: 2rem;
  width: 100%;
  max-width: 28rem;
  box-shadow:
    0 20px 25px rgba(0, 0, 0, 0.1),
    0 10px 10px rgba(0, 0, 0, 0.04);
}

.modal-input {
  width: 100%;
  padding: 0.75rem 1rem;
  background-color: var(--theme-surface-container-low);
  border: none;
  border-radius: 0.75rem;
  font-size: 0.875rem;
  outline: none;
}

.btn-cancel {
  padding: 0.625rem 1.25rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: #424752;
  border-radius: 9999px;
  border: none;
  background: none;
  cursor: pointer;
  transition: background-color 150ms;
}

.btn-cancel:hover {
  background-color: var(--theme-surface-container-low);
}

.btn-confirm {
  padding: 0.625rem 1.5rem;
  font-size: 0.875rem;
  font-weight: 600;
  background-color: var(--theme-primary);
  color: #ffffff;
  border-radius: 9999px;
  border: none;
  cursor: pointer;
  transition: opacity 150ms;
}

.btn-confirm:hover {
  opacity: 0.9;
}

.btn-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .term-review-main {
    padding-left: 1rem;
    padding-right: 1rem;
  }
}
</style>
