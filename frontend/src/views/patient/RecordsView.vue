<template>
  <div class="records-page">
    <!-- 页头（粘性，滚动时保持在顶部） -->
    <div class="page-header">
      <h1 class="page-title">问卷记录</h1>
      <p class="page-subtitle">查看您历次健康问卷的评估结果，追踪健康状况变化</p>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="state-box">
      <span class="material-symbols-outlined state-spin">refresh</span>
      <p>加载中...</p>
    </div>

    <template v-else>
      <!-- Overview 概览 Bento -->
      <div v-if="records.length" class="overview-grid">
        <div class="overview-card">
          <div class="ov-head">
            <div class="ov-icon sage">
              <span class="material-symbols-outlined">trending_up</span>
            </div>
            <span class="ov-pill">{{ overviewLatest ? overviewStatusText : '—' }}</span>
          </div>
          <div>
            <p class="ov-label">最近综合得分</p>
            <div class="ov-value-row">
              <span class="ov-value">{{ overviewLatest ? formatScore(overviewLatest.score) : '—' }}</span>
              <span class="ov-unit">分</span>
            </div>
          </div>
        </div>

        <div class="overview-card">
          <div class="ov-head">
            <div class="ov-icon blue">
              <span class="material-symbols-outlined">assignment</span>
            </div>
          </div>
          <div>
            <p class="ov-label">已参与问卷</p>
            <div class="ov-value-row">
              <span class="ov-value">{{ questionnaireCount }}</span>
              <span class="ov-unit">种</span>
            </div>
          </div>
        </div>

        <div class="overview-card followup">
          <div class="ov-head">
            <div class="ov-icon rose">
              <span class="material-symbols-outlined">notification_important</span>
            </div>
          </div>
          <div>
            <p class="ov-label">待跟进记录</p>
            <div class="ov-value-row">
              <span class="ov-value">{{ followUpCount }}</span>
              <span class="ov-unit">条</span>
            </div>
            <p class="ov-hint">中/高严重度记录建议持续关注</p>
          </div>
        </div>
      </div>

      <!-- 过滤器 -->
      <div class="filter-bar">
        <div class="filter-left">
          <label class="filter-label">问卷类型:</label>
          <el-select v-model="typeFilter" class="type-select" placeholder="全部问卷">
            <el-option label="全部问卷" value="" />
            <el-option label="心理健康 (SAS/PHQ-9)" value="mental" />
            <el-option label="基础健康状况" value="basic" />
            <el-option label="生活习惯评估" value="lifestyle" />
          </el-select>
        </div>
        <button class="sort-btn" @click="toggleSort">
          时间排序
          <span class="material-symbols-outlined">{{ sortAsc ? 'arrow_upward' : 'arrow_downward' }}</span>
        </button>
      </div>

      <!-- 记录列表（横向卡） -->
      <div v-if="filteredRecords.length" class="record-list">
        <div
          v-for="record in filteredRecords"
          :key="recordKey(record)"
          class="record-card"
          :class="{ 'record-expanded': isExpanded(record) }"
        >
          <!-- 卡片头 -->
          <div class="record-head" @click="toggleExpand(record)">
            <div class="record-left">
              <div class="record-icon" :class="`icon-${riskLevel(record.severity)}`">
                <span class="material-symbols-outlined">{{ riskIcon(record.severity) }}</span>
              </div>
              <div class="record-main">
                <div class="record-title-row">
                  <h3 class="record-title">{{ record.questionnaireTitle || '未命名问卷' }}</h3>
                  <span class="status-pill" :class="`pill-${riskLevel(record.severity)}`">
                    <span class="status-dot" />
                    {{ record.severity || '未知' }}
                  </span>
                </div>
                <p class="record-date">
                  <span class="material-symbols-outlined">calendar_today</span>
                  {{ formatDate(record.authoredDate) }}
                </p>
              </div>
            </div>
            <div class="record-meta">
              <div class="score-area">
                <p class="meta-label">得分</p>
                <div class="score">{{ formatScore(record.score) }} <span class="score-unit">分</span></div>
              </div>
              <div class="trend-area">
                <p class="meta-label">趋势</p>
                <span class="trend-diff" :class="diffClass(record)">
                  <span class="material-symbols-outlined">{{ diffIcon(record) }}</span>
                  {{ trendText(record) }}
                </span>
              </div>
              <button v-if="record.sessionId" class="detail-btn" @click.stop="showReport(record)">
                查看详情
                <span class="material-symbols-outlined">chevron_right</span>
              </button>
            </div>
          </div>

          <!-- 展开区：得分趋势 -->
          <div v-if="isExpanded(record)" class="record-detail">
            <div class="trend-head">
              <h4>得分趋势 (近3次)</h4>
              <span v-if="trendDiff(record) !== null" class="trend-diff" :class="diffClass(record)">
                <span class="material-symbols-outlined">{{ diffIcon(record) }}</span>
                较上次{{ diffText(record) }}
              </span>
            </div>
            <div v-if="recentPoints(record).length" class="trend-chart">
              <div v-for="point in recentPoints(record)" :key="point.date" class="trend-col">
                <span class="trend-score">{{ formatScore(point.score) }}分</span>
                <div class="trend-bar-wrap">
                  <div
                    class="trend-bar"
                    :class="`bar-${riskLevel(point.severity || record.severity)}`"
                    :style="{ height: barHeight(point.score, record) + '%' }"
                  />
                </div>
                <span class="trend-date">{{ formatShortDate(point.date) }}</span>
              </div>
            </div>
            <div v-else class="trend-empty">暂无趋势数据</div>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-else class="empty-box">
        <div class="empty-icon">
          <span class="material-symbols-outlined">assignment</span>
        </div>
        <h3 class="empty-title">还没有完成过健康问卷</h3>
        <p class="empty-desc">您可以通过发起新的分析来开始您的首次健康问卷评估。</p>
        <button class="btn-primary" @click="goQa">
          <span class="material-symbols-outlined">add</span>
          发起评估
        </button>
      </div>
    </template>

    <!-- 分析报告弹窗 -->
    <el-dialog
      v-model="reportVisible"
      :title="report?.riskAssessment?.level || '分析报告'"
      width="640px"
      class="report-dialog"
    >
      <div v-if="reportLoading" class="report-loading">
        <span class="material-symbols-outlined">refresh</span>
        报告加载中...
      </div>
      <div v-else-if="report">
        <p class="report-summary">{{ report.summary }}</p>
        <div class="report-block">
          <h4>风险等级</h4>
          <p class="report-risk">
            {{ report.riskAssessment.level }}
            <span v-if="report.riskAssessment.requiresUrgentAttention" class="urgent-tag">建议紧急关注</span>
          </p>
          <p class="report-desc">{{ report.riskAssessment.description }}</p>
        </div>
        <div class="report-block">
          <h4>立即建议</h4>
          <ul class="report-list">
            <li v-for="(item, i) in report.recommendations.immediate" :key="i">
              {{ item.title }}：{{ item.description }}
            </li>
            <li v-if="!report.recommendations.immediate?.length">暂无</li>
          </ul>
        </div>
        <div class="report-block">
          <h4>后续随访</h4>
          <p class="report-desc">建议日期：{{ report.followUp.suggestedDate }}</p>
          <p class="report-desc">{{ report.followUp.rationale }}</p>
        </div>
        <p class="report-disclaimer">{{ report.disclaimer }}</p>
      </div>
      <div v-else class="report-empty">暂无分析报告</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { interviewService, type TrendPoint } from '@/api/services/interview.service'
import type { AnalysisReport } from '@/api/types/interview'

const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.userId)

/** 历史记录项（/his/interview/history 返回） */
interface HistoryRecord {
  sessionId?: string | null
  fhirId?: string
  questionnaireId?: string
  questionnaireTitle?: string
  score?: number
  severity?: string
  authoredDate?: string
  status?: string
}

// ===== 页面状态 =====
const loading = ref(true)
const records = ref<HistoryRecord[]>([])
const typeFilter = ref('')
const sortAsc = ref(false) // 默认按时间降序
const expandedKey = ref('')
const trends = reactive<Record<string, TrendPoint[]>>({})

// ===== 报告弹窗状态 =====
const reportVisible = ref(false)
const reportLoading = ref(false)
const report = ref<AnalysisReport | null>(null)

// ===== Overview 概览（真实数据） =====
/** 最近一条记录（按时间最新） */
const overviewLatest = computed<HistoryRecord | undefined>(() => {
  const list = [...records.value].sort((a, b) => {
    return new Date(b.authoredDate || 0).getTime() - new Date(a.authoredDate || 0).getTime()
  })
  return list[0]
})

/** 最近记录的严重度文案（无记录显示「—」） */
const overviewStatusText = computed(() => overviewLatest.value?.severity || '—')

/** 已参与问卷数（按问卷 ID/标题去重） */
const questionnaireCount = computed(() => {
  const set = new Set(records.value.map((r) => r.questionnaireId || r.questionnaireTitle || ''))
  return set.size
})

/** 待跟进记录数（中/高严重度） */
const followUpCount = computed(() => {
  return records.value.filter((r) => {
    const lv = riskLevel(r.severity)
    return lv === 'mid' || lv === 'high'
  }).length
})

// ===== 数据加载 =====
const loadRecords = async () => {
  loading.value = true
  try {
    const list = await interviewService.getHistory(userId.value)
    records.value = list || []
    // 默认展开第一条（最新记录）
    if (records.value.length) {
      expand(records.value[0])
    }
  } finally {
    loading.value = false
  }
}

// ===== 筛选与排序 =====
/** 问卷类型分类（按标题关键字归组） */
const categorize = (title: string): string => {
  if (/(SAS|PHQ|焦虑|抑郁|心理)/i.test(title)) return 'mental'
  if (/(基础|健康)/.test(title)) return 'basic'
  return 'lifestyle'
}

const filteredRecords = computed(() => {
  const list = records.value.filter((r) => {
    if (!typeFilter.value) return true
    return categorize(r.questionnaireTitle || '') === typeFilter.value
  })
  const dir = sortAsc.value ? 1 : -1
  return [...list].sort((a, b) => {
    const ta = new Date(a.authoredDate || 0).getTime()
    const tb = new Date(b.authoredDate || 0).getTime()
    return (ta - tb) * dir
  })
})

const toggleSort = () => {
  sortAsc.value = !sortAsc.value
}

// ===== 展开与趋势 =====
const recordKey = (r: HistoryRecord): string => r.sessionId || r.fhirId || ''

const isExpanded = (r: HistoryRecord): boolean => expandedKey.value === recordKey(r)

const toggleExpand = (r: HistoryRecord) => {
  if (isExpanded(r)) {
    expandedKey.value = ''
  } else {
    expand(r)
  }
}

const expand = (r: HistoryRecord) => {
  expandedKey.value = recordKey(r)
  loadTrend(r)
}

const loadTrend = async (r: HistoryRecord) => {
  if (!r.questionnaireId || !userId.value) return
  const key = recordKey(r)
  if (trends[key]) return
  const data = await interviewService.getTrend(userId.value, r.questionnaireId)
  trends[key] = data
}

/** 近3次趋势点 */
const recentPoints = (r: HistoryRecord): TrendPoint[] => {
  const data = trends[recordKey(r)] || []
  return data.slice(-3)
}

/** 柱高：相对该问卷趋势最大值（百分比） */
const barHeight = (score: number, r: HistoryRecord): number => {
  const data = trends[recordKey(r)] || []
  const max = Math.max(...data.map((p) => p.score || 0))
  if (!max) return 10
  return Math.max(8, Math.round((score / max) * 100))
}

// ===== 较上次变化 =====
const trendDiff = (r: HistoryRecord): number | null => {
  const pts = trends[recordKey(r)] || []
  if (pts.length < 2) return null
  const last = pts[pts.length - 1].score
  const prev = pts[pts.length - 2].score
  return Math.round((last - prev) * 10) / 10
}

const diffIcon = (r: HistoryRecord): string => {
  const d = trendDiff(r)
  if (d === null) return 'trending_flat'
  if (d > 0) return 'trending_up'
  if (d < 0) return 'trending_down'
  return 'trending_flat'
}

const diffClass = (r: HistoryRecord): string => {
  const d = trendDiff(r)
  if (d === null) return ''
  if (d > 0) return 'diff-up'
  if (d < 0) return 'diff-down'
  return 'diff-flat'
}

const diffText = (r: HistoryRecord): string => {
  const d = trendDiff(r)
  if (d === null) return ''
  if (d === 0) return '持平'
  return `${d > 0 ? '增加' : '减少'} ${Math.abs(d)} 分`
}

/** 卡片右侧趋势简版文案（如 +3 分 / 持平） */
const trendText = (r: HistoryRecord): string => {
  const d = trendDiff(r)
  if (d === null) return '暂无对比'
  if (d === 0) return '持平'
  return `${d > 0 ? '+' : ''}${Math.round(d * 10) / 10} 分`
}

// ===== 风险等级映射 =====
const riskLevel = (severity?: string): string => {
  const s = severity || ''
  if (s.includes('重')) return 'high'
  if (s.includes('中')) return 'mid'
  if (s.includes('轻') || s.includes('低')) return 'low'
  if (s.includes('正') || s.includes('无')) return 'normal'
  return 'unknown'
}

const riskIcon = (severity?: string): string => {
  const map: Record<string, string> = {
    high: 'warning',
    mid: 'mood_bad',
    low: 'info',
    normal: 'check_circle',
    unknown: 'assignment'
  }
  return map[riskLevel(severity)]
}

// ===== 分析报告 =====
const showReport = async (r: HistoryRecord) => {
  if (!r.sessionId) return
  if (!isExpanded(r)) expand(r)
  reportVisible.value = true
  reportLoading.value = true
  report.value = null
  try {
    report.value = await interviewService.getAnalysisReport(r.sessionId)
  } finally {
    reportLoading.value = false
  }
}

// ===== 工具函数 =====
const formatScore = (score?: number): string => (score == null ? '—' : String(Math.round(score)))
const formatDate = (d?: string): string => (d ? d.slice(0, 10) : '—')
const formatShortDate = (d: string): string => (d ? d.slice(5, 10) : '')

const goQa = () => {
  router.push('/qa')
}

onMounted(loadRecords)
</script>

<style scoped>
/* ===== 页面容器 ===== */
.records-page {
  max-width: 64rem;
  margin: 0 auto;
  padding-bottom: 3rem;
}

/* ===== 页头（粘性） ===== */
.page-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--theme-surface-bright);
  padding: 0.25rem 0 1.25rem;
  border-bottom: 1px solid var(--theme-warm-sand);
  margin-bottom: 1.5rem;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--theme-on-surface);
  margin-bottom: 0.25rem;
  letter-spacing: -0.01em;
}

.page-subtitle {
  font-size: 0.875rem;
  color: var(--theme-soft-stone);
  margin: 0;
}

/* ===== 加载/空态容器 ===== */
.state-box {
  min-height: 24rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  color: var(--theme-outline);
  font-size: 0.875rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
}

.state-spin {
  font-size: 1.75rem;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-box {
  min-height: 24rem;
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
  background: var(--theme-primary-soft);
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

/* ===== 主按钮 ===== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.5rem;
  background: var(--theme-primary);
  color: var(--theme-on-primary);
  font-size: 0.875rem;
  font-weight: 600;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(70, 101, 88, 0.25);
  transition: all 150ms;
}

.btn-primary:hover {
  background: var(--theme-primary-strong);
}

.btn-primary:active {
  transform: scale(0.97);
}

/* ===== Overview 概览 ===== */
.overview-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

@media (min-width: 768px) {
  .overview-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.overview-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid rgba(233, 227, 216, 0.7);
  border-radius: 0.75rem;
  box-shadow: 0 10px 30px rgba(139, 166, 193, 0.05);
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 0.875rem;
}

.overview-card.followup {
  background: var(--theme-surface-container-low);
}

.ov-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ov-icon {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ov-icon .material-symbols-outlined {
  font-size: 1.25rem;
}

.ov-icon.sage {
  background: rgba(134, 166, 151, 0.12);
  color: var(--theme-healing-sage);
}

.ov-icon.blue {
  background: rgba(139, 166, 193, 0.12);
  color: var(--theme-therapeutic-blue);
}

.ov-icon.rose {
  background: rgba(193, 139, 139, 0.12);
  color: var(--theme-error-rose);
}

.ov-pill {
  padding: 0.125rem 0.625rem;
  background: var(--theme-primary-soft);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--theme-primary);
}

.ov-label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--theme-soft-stone);
  margin: 0 0 0.25rem;
}

.ov-value-row {
  display: flex;
  align-items: baseline;
  gap: 0.375rem;
}

.ov-value {
  font-size: 2.25rem;
  font-weight: 700;
  color: var(--theme-primary);
  line-height: 1.1;
}

.ov-unit {
  font-size: 0.875rem;
  color: var(--theme-soft-stone);
}

.ov-hint {
  font-size: 0.75rem;
  color: var(--theme-outline);
  margin: 0.375rem 0 0;
}

/* ===== 过滤器 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 1.25rem;
  padding: 0.75rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.filter-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  white-space: nowrap;
}

.type-select {
  width: 200px;
}

.sort-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.375rem 0.75rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.25rem;
  cursor: pointer;
  transition: all 150ms;
}

.sort-btn:hover {
  color: var(--theme-primary);
}

.sort-btn .material-symbols-outlined {
  font-size: 1rem;
}

/* ===== 记录列表 ===== */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.record-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid rgba(233, 227, 216, 0.7);
  border-radius: 0.75rem;
  box-shadow: 0 10px 30px rgba(139, 166, 193, 0.05);
  overflow: hidden;
  transition: border-color 150ms;
}

.record-card:hover {
  border-color: rgba(139, 166, 193, 0.5);
}

.record-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 1.25rem;
  cursor: pointer;
  transition: background 150ms;
  border-bottom: 1px solid transparent;
}

.record-head:hover {
  background: var(--theme-surface-container-low);
}

.record-expanded .record-head {
  border-bottom-color: var(--theme-outline-variant);
}

/* 左侧：图标 + 标题 + 状态 */
.record-left {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  min-width: 0;
}

.record-icon {
  width: 3rem;
  height: 3rem;
  border-radius: 0.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.record-icon .material-symbols-outlined {
  font-size: 1.375rem;
}

.record-main {
  min-width: 0;
}

.record-title-row {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  flex-wrap: wrap;
  margin-bottom: 0.375rem;
}

.record-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
}

.record-date {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8125rem;
  color: var(--theme-soft-stone);
  margin: 0;
}

.record-date .material-symbols-outlined {
  font-size: 0.875rem;
}

/* 状态 pill（设计稿：warm-sand / secondary-fixed / primary-fixed） */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.125rem 0.625rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}

.status-dot {
  width: 0.375rem;
  height: 0.375rem;
  border-radius: 9999px;
}

.pill-high {
  background: var(--theme-warm-sand);
  color: var(--theme-on-tertiary-container);
}

.pill-high .status-dot {
  background: var(--theme-error-rose);
}

.pill-mid {
  background: var(--theme-secondary-container);
  color: var(--theme-on-secondary-container);
}

.pill-mid .status-dot {
  background: var(--theme-therapeutic-blue);
}

.pill-low,
.pill-normal {
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
}

.pill-low .status-dot,
.pill-normal .status-dot {
  background: var(--theme-healing-sage);
}

.pill-unknown {
  background: var(--theme-surface-container);
  color: var(--theme-on-surface-variant);
}

.pill-unknown .status-dot {
  background: var(--theme-outline);
}

/* 右侧：得分 + 趋势 + 详情 */
.record-meta {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-shrink: 0;
}

.score-area,
.trend-area {
  text-align: right;
}

.meta-label {
  font-size: 0.6875rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--theme-soft-stone);
  margin: 0 0 0.125rem;
}

.score {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--theme-on-surface);
  line-height: 1.2;
}

.score-unit {
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--theme-outline);
}

.trend-diff {
  display: inline-flex;
  align-items: center;
  gap: 0.125rem;
  font-size: 0.8125rem;
  font-weight: 600;
}

.trend-diff .material-symbols-outlined {
  font-size: 1rem;
}

.diff-up {
  color: var(--theme-error);
}

.diff-down {
  color: var(--theme-success);
}

.diff-flat {
  color: var(--theme-outline);
}

.detail-btn {
  display: none;
  align-items: center;
  gap: 0.125rem;
  color: var(--theme-primary);
  font-size: 0.8125rem;
  font-weight: 600;
  background: none;
  border: none;
  cursor: pointer;
  white-space: nowrap;
}

.record-head:hover .detail-btn {
  display: inline-flex;
}

.detail-btn .material-symbols-outlined {
  font-size: 1.125rem;
}

/* ===== 展开区：趋势 ===== */
.record-detail {
  padding: 1rem 1.25rem;
  background: var(--theme-surface-container-low);
}

.trend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.trend-head h4 {
  font-size: 0.8125rem;
  font-weight: 700;
  color: var(--theme-on-surface-variant);
  letter-spacing: 0.05em;
  margin: 0;
}

.trend-chart {
  height: 8rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.5rem;
  padding: 0.5rem 1.5rem;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.5rem;
}

.trend-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.375rem;
  width: 33.33%;
}

.trend-score {
  font-size: 0.625rem;
  color: var(--theme-on-surface-variant);
}

.trend-bar-wrap {
  display: flex;
  align-items: flex-end;
  height: 4rem;
}

.trend-bar {
  width: 2rem;
  border-radius: 0.125rem 0.125rem 0 0;
  transition: height 250ms ease;
  min-height: 0.25rem;
}

.trend-date {
  font-size: 0.625rem;
  color: var(--theme-outline);
}

.trend-empty {
  padding: 1rem;
  text-align: center;
  font-size: 0.8125rem;
  color: var(--theme-outline);
}

/* ===== 风险色板（语义色，跨主题一致） ===== */
/* 图标背景 */
.icon-high { background: rgba(186, 26, 26, 0.1); color: var(--theme-error); }
.icon-mid { background: rgba(237, 108, 2, 0.12); color: var(--theme-processing); }
.icon-low { background: rgba(234, 179, 8, 0.12); color: #b45309; }
.icon-normal { background: rgba(46, 125, 50, 0.12); color: var(--theme-success); }
.icon-unknown { background: var(--theme-surface-container); color: var(--theme-outline); }

/* 趋势柱 */
.bar-high { background: var(--theme-error); }
.bar-mid { background: var(--theme-processing); }
.bar-low { background: #eab308; }
.bar-normal { background: var(--theme-success); }
.bar-unknown { background: var(--theme-outline); }

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>

<style>
/* 报告弹窗样式（el-dialog teleport 到 body，需全局作用域） */
.report-dialog .report-loading,
.report-dialog .report-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 2rem;
  color: var(--theme-outline);
  font-size: 0.875rem;
}

.report-dialog .report-loading .material-symbols-outlined {
  animation: report-spin 1s linear infinite;
}

@keyframes report-spin {
  to {
    transform: rotate(360deg);
  }
}

.report-summary {
  font-size: 0.9375rem;
  line-height: 1.7;
  color: var(--theme-on-surface);
  padding: 0.75rem 1rem;
  background: var(--theme-surface-container-low);
  border-radius: 0.5rem;
  margin-bottom: 1rem;
}

.report-block {
  margin-bottom: 1rem;
}

.report-block h4 {
  font-size: 0.8125rem;
  font-weight: 700;
  color: var(--theme-on-surface-variant);
  letter-spacing: 0.05em;
  margin-bottom: 0.5rem;
}

.report-risk {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--theme-on-surface);
  margin-bottom: 0.25rem;
}

.urgent-tag {
  display: inline-block;
  margin-left: 0.5rem;
  padding: 0.125rem 0.5rem;
  font-size: 0.6875rem;
  font-weight: 700;
  color: var(--theme-error);
  background: rgba(186, 26, 26, 0.1);
  border: 1px solid rgba(186, 26, 26, 0.25);
  border-radius: 9999px;
  vertical-align: middle;
}

.report-desc {
  font-size: 0.875rem;
  color: var(--theme-on-surface-variant);
  line-height: 1.6;
}

.report-list {
  padding-left: 1.25rem;
  font-size: 0.875rem;
  color: var(--theme-on-surface-variant);
  line-height: 1.7;
}

.report-disclaimer {
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--theme-outline-variant);
  font-size: 0.75rem;
  color: var(--theme-outline);
  line-height: 1.6;
}
</style>
