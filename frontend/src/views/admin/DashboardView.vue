<template>
  <div v-loading="loading" class="dashboard-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">系统统计</h1>
        <p class="page-subtitle">平台核心运行指标一览</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadAll">
        <span class="material-symbols-outlined refresh-icon">refresh</span>
        刷新
      </el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">
          <span class="material-symbols-outlined stat-icon primary">person</span>
          <span>用户总数</span>
        </div>
        <!-- total 由后端 Long 序列化为字符串，Number() 归一后才有千分位格式化 -->
        <div class="stat-value">{{ Number(userTotal).toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">
          <span class="material-symbols-outlined stat-icon primary">description</span>
          <span>文档总数</span>
        </div>
        <div class="stat-value">{{ Number(docTotal).toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">
          <span class="material-symbols-outlined stat-icon primary">analytics</span>
          <span>分诊请求数</span>
        </div>
        <div class="stat-value">{{ Number(triageTotal).toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">
          <span class="material-symbols-outlined stat-icon danger">spellcheck</span>
          <span>待审核术语</span>
        </div>
        <div class="stat-value danger">{{ pendingCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">
          <span class="material-symbols-outlined stat-icon success">check_circle</span>
          <span>今日已通过</span>
        </div>
        <div class="stat-value">{{ approvedToday }}</div>
      </div>
    </div>

    <!-- 主网格：左图表区 + 右侧栏 -->
    <div class="dashboard-grid">
      <!-- 左：图表区 -->
      <div class="charts-area">
        <div class="charts-row">
          <div class="chart-card">
            <h3 class="chart-title">用户角色分布</h3>
            <div ref="roleChartEl" class="chart-box" />
          </div>
          <div class="chart-card">
            <h3 class="chart-title">文档处理状态</h3>
            <div ref="docChartEl" class="chart-box" />
          </div>
        </div>
        <div class="charts-row">
          <div class="chart-card">
            <h3 class="chart-title">热门分诊科室</h3>
            <div ref="deptChartEl" class="chart-box" />
          </div>
          <div class="chart-card">
            <h3 class="chart-title">紧急程度分布</h3>
            <div ref="urgencyChartEl" class="chart-box" />
          </div>
        </div>
      </div>

      <!-- 右：性能 / 状态 / 告警 -->
      <div class="side-column">
        <!-- 性能指标 -->
        <div class="side-card">
          <h3 class="chart-title">性能指标</h3>
          <p class="metric-label">分诊成功率</p>
          <div class="ring-wrap">
            <svg class="ring" viewBox="0 0 100 100">
              <circle class="ring-track" cx="50" cy="50" r="40" />
              <circle
                class="ring-bar"
                cx="50"
                cy="50"
                r="40"
                :stroke-dasharray="251.2"
                :stroke-dashoffset="ringOffset"
              />
            </svg>
            <span class="ring-text">{{ successPercent }}%</span>
          </div>
          <div class="metric-list">
            <div class="metric-row">
              <span class="metric-name">规则引擎</span>
              <span class="metric-value">{{ formatDuration(ruleAvg) }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-name">大语言模型</span>
              <span class="metric-value secondary">{{ formatDuration(llmAvg) }}</span>
            </div>
          </div>
        </div>

        <!-- 服务状态 -->
        <div class="side-card">
          <h3 class="chart-title">服务状态</h3>
          <div class="service-row">
            <div class="service-name">
              <span class="dot" :class="qaOnline ? 'dot-on' : 'dot-off'" />
              <span>问答服务 (QA)</span>
            </div>
            <span class="service-badge" :class="qaOnline ? 'badge-on' : 'badge-off'">
              {{ qaOnline ? '运行中' : '离线' }}
            </span>
          </div>
          <div class="service-row">
            <div class="service-name">
              <span class="dot" :class="triageOnline ? 'dot-on' : 'dot-off'" />
              <span>分诊服务 (Triage)</span>
            </div>
            <span class="service-badge" :class="triageOnline ? 'badge-on' : 'badge-off'">
              {{ triageOnline ? '运行中' : '离线' }}
            </span>
          </div>
        </div>

        <!-- 术语审查警报（仅告警触发时显示） -->
        <div v-if="alertTriggered" class="alert-card">
          <div class="alert-head">
            <span class="material-symbols-outlined">warning</span>
            <h3>术语审查警报</h3>
          </div>
          <p class="alert-desc">未映射术语比例超出阈值，需人工介入处理。</p>
          <div class="alert-bar">
            <div class="alert-bar-fill" :style="{ width: alertWidth + '%' }" />
          </div>
          <div class="alert-legend">
            <span>当前: {{ unmappedRate }}%</span>
            <span>阈值: {{ alertThreshold }}%</span>
          </div>
          <el-button class="alert-btn" @click="goTermReview">前往处理</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
// echarts 按需引入：仅注册本页 4 张图实际用到的图表与组件（pie/bar + grid/tooltip/legend + canvas 渲染）
import * as echarts from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
// type-only 导入，编译期擦除，不增加产物体积
import type { EChartsOption } from 'echarts'

echarts.use([PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
import { dashboardService } from '@/api/services/dashboard.service'
import { documentService } from '@/api/services/document.service'
import { adminService } from '@/api/services/admin.service'
import { ROLE_LABELS } from '@/config/menus'
import { formatDuration } from '@/utils/format'

const router = useRouter()

/** 急诊分级中文映射 */
const EMERGENCY_LABELS: Record<string, string> = {
  CRITICAL: '危急',
  HIGH: '高危',
  MEDIUM: '中危',
  LOW: '低危'
}

/** 文档状态中文映射 */
const DOC_STATUS_LABELS: Record<string, string> = {
  UPLOADED: '待处理',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败'
}

// ===== 统计状态 =====
const loading = ref(false)
const userTotal = ref(0)
const docTotal = ref(0)
const triageTotal = ref(0)
const pendingCount = ref(0)
const approvedToday = ref(0)

const roleDist = ref<Array<{ name: string; value: number }>>([])
const docDist = ref<Array<{ name: string; value: number }>>([])
const topDepts = ref<Array<{ name: string; value: number }>>([])
const urgencyDist = ref<Array<{ name: string; value: number }>>([])

const successRate = ref(0)
const ruleAvg = ref(0)
const llmAvg = ref(0)
const qaOnline = ref(false)
const triageOnline = ref(false)

const unmappedRate = ref(0)
const alertThreshold = ref(0)
const alertTriggered = ref(false)

// ===== 展示计算 =====
/** 成功率（0~1 → 百分比，后端可能直接给百分比则原样使用） */
const successPercent = computed(() => {
  const r = successRate.value
  const pct = r > 1 ? r : r * 100
  return Math.min(100, Math.round(pct * 10) / 10)
})

/** 环形进度条偏移量（周长 251.2） */
const ringOffset = computed(() => 251.2 * (1 - successPercent.value / 100))

/** 告警进度条宽度（相对阈值） */
const alertWidth = computed(() => {
  if (alertThreshold.value <= 0) return 0
  return Math.min(100, Math.round((unmappedRate.value / alertThreshold.value) * 100))
})

// ===== 图表 =====
const roleChartEl = ref<HTMLDivElement>()
const docChartEl = ref<HTMLDivElement>()
const deptChartEl = ref<HTMLDivElement>()
const urgencyChartEl = ref<HTMLDivElement>()

type EChartsInstance = ReturnType<typeof echarts.init>
let roleChart: EChartsInstance | null = null
let docChart: EChartsInstance | null = null
let deptChart: EChartsInstance | null = null
let urgencyChart: EChartsInstance | null = null

/** 图表公共配置 */
const chartBase: EChartsOption = {
  textStyle: { fontFamily: 'Inter, sans-serif' },
  tooltip: { backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e0e3e5' }
}

const renderRoleChart = () => {
  const el = roleChartEl.value
  if (!el) return
  if (!roleChart) roleChart = echarts.init(el)
  roleChart.setOption({
    ...chartBase,
    color: ['#005eb8', '#505f76', '#2e7d32', '#00838f', '#8e24aa', '#ed6c02'],
    legend: { bottom: '5%', left: 'center', icon: 'circle', textStyle: { color: '#191c1e', fontSize: 12 } },
    series: [
      {
        name: '角色',
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: roleDist.value
      }
    ]
  } as EChartsOption)
}

const renderDocChart = () => {
  const el = docChartEl.value
  if (!el) return
  if (!docChart) docChart = echarts.init(el)
  docChart.setOption({
    ...chartBase,
    color: ['#2e7d32', '#00838f', '#505f76', '#d32f2f'],
    legend: { bottom: '5%', left: 'center', icon: 'circle', textStyle: { color: '#191c1e', fontSize: 12 } },
    series: [
      {
        name: '状态',
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: docDist.value
      }
    ]
  } as EChartsOption)
}

const renderDeptChart = () => {
  const el = deptChartEl.value
  if (!el) return
  if (!deptChart) deptChart = echarts.init(el)
  deptChart.setOption({
    ...chartBase,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '5%', containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#e0e3e5' } }, axisLabel: { color: '#6e797e' } },
    yAxis: {
      type: 'category',
      data: topDepts.value.map((d) => d.name),
      axisLabel: { color: '#191c1e' },
      axisLine: { lineStyle: { color: '#e0e3e5' } }
    },
    series: [
      {
        name: '请求数',
        type: 'bar',
        itemStyle: { color: '#005eb8', borderRadius: [0, 4, 4, 0] },
        barWidth: '50%',
        data: topDepts.value.map((d) => d.value)
      }
    ]
  } as EChartsOption)
}

const renderUrgencyChart = () => {
  const el = urgencyChartEl.value
  if (!el) return
  if (!urgencyChart) urgencyChart = echarts.init(el)
  urgencyChart.setOption({
    ...chartBase,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '5%', containLabel: true },
    xAxis: {
      type: 'category',
      data: urgencyDist.value.map((d) => d.name),
      axisLabel: { color: '#191c1e' },
      axisLine: { lineStyle: { color: '#e0e3e5' } }
    },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#e0e3e5' } }, axisLabel: { color: '#6e797e' } },
    series: [
      {
        name: '病例数',
        type: 'bar',
        itemStyle: {
          color: (params: { dataIndex: number }) => {
            const list = ['#d32f2f', '#ed6c02', '#00838f', '#2e7d32']
            return list[params.dataIndex % list.length] || '#00838f'
          },
          borderRadius: [4, 4, 0, 0]
        },
        barWidth: '40%',
        data: urgencyDist.value.map((d) => d.value)
      }
    ]
  } as EChartsOption)
}

const renderCharts = () => {
  renderRoleChart()
  renderDocChart()
  renderDeptChart()
  renderUrgencyChart()
}

/** 窗口尺寸变化时同步图表 */
const handleResize = () => {
  roleChart?.resize()
  docChart?.resize()
  deptChart?.resize()
  urgencyChart?.resize()
}

// ===== 数据加载（各模块独立容错，失败不阻塞整页） =====
const loadAll = async () => {
  loading.value = true
  const [userRes, docRes, triageRes, adminRes, qaRes, triageHealthRes] = await Promise.allSettled([
    dashboardService.getUsersStats(),
    documentService.getDocumentStats(),
    dashboardService.getTriageStats(),
    adminService.getStats({ silent: true }),
    dashboardService.checkQaHealth(),
    dashboardService.checkTriageHealth()
  ])

  // 用户维度
  if (userRes.status === 'fulfilled') {
    userTotal.value = userRes.value.total
    roleDist.value = userRes.value.byRole.map(([role, count]) => ({
      name: ROLE_LABELS[role] || role,
      value: count
    }))
  }

  // 文档维度
  if (docRes.status === 'fulfilled') {
    docTotal.value = docRes.value.total
    docDist.value = Object.entries(docRes.value.byStatus).map(([status, count]) => ({
      name: DOC_STATUS_LABELS[status] || status,
      value: count
    }))
  }

  // 导诊维度
  if (triageRes.status === 'fulfilled') {
    const s = triageRes.value
    triageTotal.value = s.totalRequests
    successRate.value = s.successRate
    ruleAvg.value = s.ruleEngineAvgTime
    llmAvg.value = s.llmAvgTime
    topDepts.value = Object.entries(s.topDepartments || {})
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([name, value]) => ({ name, value }))
    urgencyDist.value = Object.entries(s.emergencyDistribution || {}).map(([key, value]) => ({
      name: EMERGENCY_LABELS[key] || key,
      value
    }))
  }

  // 词条审核维度
  if (adminRes.status === 'fulfilled') {
    pendingCount.value = adminRes.value.pendingCount
    approvedToday.value = adminRes.value.approvedTodayCount
    unmappedRate.value = adminRes.value.unmappedRate
    alertThreshold.value = adminRes.value.alertThreshold
    alertTriggered.value = adminRes.value.alertTriggered
  }

  // 服务状态
  if (qaRes.status === 'fulfilled') qaOnline.value = qaRes.value
  if (triageHealthRes.status === 'fulfilled') triageOnline.value = triageHealthRes.value

  loading.value = false
  renderCharts()
}

const goTermReview = () => router.push('/term-review')

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  roleChart?.dispose()
  docChart?.dispose()
  deptChart?.dispose()
  urgencyChart?.dispose()
  roleChart = docChart = deptChart = urgencyChart = null
})
</script>

<style scoped>
/* ===== 页面容器 ===== */
.dashboard-page {
  max-width: 80rem;
  margin: 0 auto;
  padding-bottom: 3rem;
  min-height: 24rem;
}

/* ===== 页头 ===== */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  padding: 0 0 1.5rem;
  border-bottom: 1px solid var(--theme-outline-variant);
  margin-bottom: 1.5rem;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--theme-on-surface);
  margin-bottom: 0.5rem;
  letter-spacing: -0.01em;
}

.page-subtitle {
  font-size: 0.875rem;
  color: var(--theme-on-surface-variant);
}

.refresh-icon {
  font-size: 1.125rem;
}

/* ===== 统计卡片 ===== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.stat-card {
  background: #ffffff;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 0.75rem;
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
}

.stat-icon {
  font-size: 1.25rem;
  padding: 0.25rem;
  border-radius: 0.375rem;
}

.stat-icon.primary {
  color: var(--theme-brand);
  background: rgba(0, 94, 184, 0.08);
}

.stat-icon.danger {
  color: var(--theme-error);
  background: rgba(211, 47, 47, 0.08);
}

.stat-icon.success {
  color: var(--theme-success);
  background: rgba(46, 125, 50, 0.08);
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--theme-on-surface);
  letter-spacing: -0.01em;
}

.stat-value.danger {
  color: var(--theme-error);
}

/* ===== 主网格 ===== */
.dashboard-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 1.5rem;
  align-items: start;
}

.charts-area {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

/* ===== 卡片 ===== */
.chart-card,
.side-card {
  background: #ffffff;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1.25rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.chart-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  padding-bottom: 0.75rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid #e0e3e5;
}

.chart-box {
  width: 100%;
  height: 15.625rem;
}

/* ===== 右侧栏 ===== */
.side-column {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.metric-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  text-align: center;
  margin-bottom: 0.75rem;
}

.ring-wrap {
  position: relative;
  width: 8rem;
  height: 8rem;
  margin: 0 auto 1rem;
}

.ring {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-track {
  fill: none;
  stroke: #e0e3e5;
  stroke-width: 8;
}

.ring-bar {
  fill: none;
  stroke: var(--theme-success);
  stroke-width: 8;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.4s ease;
}

.ring-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--theme-success);
}

.metric-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.metric-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f2f4f6;
  border-radius: 0.375rem;
  padding: 0.5rem 0.75rem;
  font-size: 0.875rem;
  color: var(--theme-on-surface);
}

.metric-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.8125rem;
  color: var(--theme-brand);
}

.metric-value.secondary {
  color: #505f76;
}

/* ===== 服务状态 ===== */
.service-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.625rem 0;
  border-bottom: 1px dashed #e0e3e5;
}

.service-row:last-child {
  border-bottom: none;
}

.service-name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--theme-on-surface);
}

.dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 9999px;
}

.dot-on {
  background: var(--theme-success);
}

.dot-off {
  background: var(--theme-error);
}

.service-badge {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.25rem 0.625rem;
  border-radius: 0.375rem;
}

.badge-on {
  color: var(--theme-success);
  background: rgba(46, 125, 50, 0.1);
}

.badge-off {
  color: var(--theme-error);
  background: rgba(211, 47, 47, 0.1);
}

/* ===== 术语审查警报 ===== */
.alert-card {
  background: #fde8e8;
  border: 1px solid #f5c2c0;
  border-radius: 0.75rem;
  padding: 1.25rem;
}

.alert-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--theme-error);
  margin-bottom: 0.5rem;
}

.alert-head h3 {
  font-size: 1rem;
  font-weight: 600;
}

.alert-desc {
  font-size: 0.8125rem;
  color: #3e484d;
  margin-bottom: 0.75rem;
}

.alert-bar {
  width: 100%;
  background: #e0e3e5;
  border-radius: 9999px;
  height: 0.625rem;
  overflow: hidden;
  margin-bottom: 0.25rem;
}

.alert-bar-fill {
  height: 100%;
  background: var(--theme-error);
  border-radius: 9999px;
  transition: width 0.4s ease;
}

.alert-legend {
  display: flex;
  justify-content: space-between;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.75rem;
  color: #3e484d;
  margin-bottom: 0.75rem;
}

.alert-btn {
  width: 100%;
}

/* ===== 响应式 ===== */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>
