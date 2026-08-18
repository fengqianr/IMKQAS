<template>
  <div class="patient-detail-page">
    <!-- 返回检索 -->
    <button class="back-link" @click="goBack">
      <span class="material-symbols-outlined">arrow_back</span>
      返回检索
    </button>

    <!-- 患者不存在 -->
    <div v-if="!loading && !patient" class="empty-box">
      <div class="empty-icon">
        <span class="material-symbols-outlined">person_off</span>
      </div>
      <h3 class="empty-title">未找到该患者档案</h3>
      <p class="empty-desc">患者可能已被删除，或链接无效。</p>
      <button class="btn-outline" @click="goBack">返回检索</button>
    </div>

    <template v-else>
      <!-- 患者 Hero 卡 -->
      <div class="quick-header">
        <div class="quick-top">
          <div class="quick-left">
            <div class="quick-avatar">{{ patientInitial(patient) }}</div>
            <div>
              <div class="quick-name-row">
                <h1 class="quick-name">{{ patientName(patient) }}</h1>
                <span class="quick-badge">{{ genderText(patient?.gender) }} · {{ ageText }} 岁</span>
                <span class="hero-status" :class="'hero-status-' + statusMeta.tone">{{ statusMeta.text }}</span>
              </div>
              <p class="patient-id">患者编号: {{ patient?.id }}</p>
              <div class="hero-recent">
                <span class="material-symbols-outlined hero-recent-icon">schedule</span>
                {{ recentVisitText }}
              </div>
            </div>
          </div>
          <div class="quick-actions">
            <button class="btn-primary" @click="handleNewConsult">
              <span class="material-symbols-outlined">add</span>
              新建问诊
            </button>
            <button class="btn-outline" @click="handlePrint">
              <span class="material-symbols-outlined">print</span>
              打印报告
            </button>
          </div>
        </div>
        <!-- 核心信息条（原「基本信息」Tab 并入 Hero） -->
        <div class="hero-basic">
          <div class="basic-item">
            <span class="basic-label">出生日期</span>
            <span class="basic-value">{{ formatDate(patient?.birthDate) }}</span>
          </div>
          <div class="basic-item">
            <span class="basic-label">联系电话</span>
            <span class="basic-value">{{ maskPhone(phoneOf) }}</span>
          </div>
          <div class="basic-item">
            <span class="basic-label">证件号码</span>
            <span class="basic-value">{{ maskIdNumber(identifierOf?.value) }}</span>
          </div>
          <div class="basic-item">
            <span class="basic-label">家庭住址</span>
            <span class="basic-value">{{ addressOf }}</span>
          </div>
        </div>
      </div>

      <!-- 主体：Tab 区 + AI 摘要侧栏 -->
      <div class="detail-body">
        <div class="detail-main">
          <!-- Tab 导航 -->
          <div class="tab-nav">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="tab-btn"
              :class="{ active: activeTab === tab.key }"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>

          <!-- Tab 内容：病情记录 -->
          <div v-if="activeTab === 'condition'" class="tab-content">
            <div v-if="conditions.length" class="list-card">
              <div v-for="c in conditions" :key="c.id" class="list-item">
                <div class="list-item-head">
                  <span class="item-title">{{ conditionName(c) }}</span>
                  <span class="status-badge" :class="statusColorOf(c)">
                    {{ statusTextOf(c) }}
                  </span>
                </div>
                <div class="item-sub">
                  <span v-if="c.onsetDateTime">发病 {{ formatDate(c.onsetDateTime) }}</span>
                  <span v-if="c.recordedDate">记录 {{ formatDate(c.recordedDate) }}</span>
                </div>
                <p v-if="conditionNote(c)" class="item-note">{{ conditionNote(c) }}</p>
              </div>
            </div>
            <div v-else class="empty-inline">
              <span class="material-symbols-outlined">folder_open</span>
              暂无病情记录
            </div>
          </div>

          <!-- Tab 内容：健康档案 -->
          <div v-else-if="activeTab === 'health'" class="tab-content">
            <div v-if="hasHealthProfile && healthProfile" class="info-card core-card">
              <h2 class="card-title">
                <span class="material-symbols-outlined">health_and_safety</span>
                健康档案
              </h2>
              <div class="hp-section">
                <h3 class="hp-label">基本资料</h3>
                <div class="info-grid">
                  <div class="info-item">
                    <span class="info-label">姓名</span>
                    <span class="info-value">{{ healthProfile.name || '—' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">性别</span>
                    <span class="info-value">{{ genderText(healthProfile.gender) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">年龄</span>
                    <span class="info-value">{{ healthProfile.age != null ? healthProfile.age + ' 岁' : '—' }}</span>
                  </div>
                </div>
              </div>
              <div v-for="group in healthGroups" :key="group.label" class="hp-section">
                <h3 class="hp-label">{{ group.label }}</h3>
                <ul v-if="group.items.length" class="hp-list">
                  <li v-for="(item, i) in group.items" :key="i" class="hp-item">{{ item }}</li>
                </ul>
                <p v-else class="hp-empty">暂无{{ group.label }}</p>
              </div>
            </div>
            <div v-else class="empty-inline">
              <span class="material-symbols-outlined">health_and_safety</span>
              患者未填写健康档案
            </div>
          </div>

          <!-- Tab 内容：问卷记录 -->
          <div v-else-if="activeTab === 'questionnaire'" class="tab-content">
            <div v-if="questionnaireRecords.length" class="list-card">
              <div v-for="qr in questionnaireRecords" :key="qr.fhirId || qr.sessionId" class="list-item">
                <div class="list-item-head">
                  <div class="qr-title-wrap">
                    <span class="material-symbols-outlined qr-icon">assignment</span>
                    <span class="item-title">{{ qr.questionnaireTitle || '问卷记录' }}</span>
                  </div>
                  <div class="qr-meta">
                    <span class="score-badge" :class="severityTone(qr.severity)">
                      {{ qr.score != null ? qr.score + ' 分' : '—' }} · {{ qr.severity || '未知' }}
                    </span>
                    <span v-if="qr.authoredDate">{{ formatDate(qr.authoredDate) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="empty-inline">
              <span class="material-symbols-outlined">fact_check</span>
              暂无问卷记录
            </div>
          </div>

          <!-- Tab 内容：检验与观察 -->
          <div v-else-if="activeTab === 'observation'" class="tab-content">
            <div v-if="observations.length" class="list-card">
              <div v-for="o in observations" :key="o.id" class="list-item">
                <div class="list-item-head">
                  <span class="item-title">{{ observationName(o) }}</span>
                  <span class="item-result" :class="{ abnormal: hasAbnormal(o) }">
                    {{ observationValue(o) }}
                  </span>
                </div>
                <div class="item-sub">
                  <span class="status-badge neutral">{{ observationStatusText(o.status) }}</span>
                  <span v-if="o.effectiveDateTime">{{ formatDate(o.effectiveDateTime) }}</span>
                  <span v-if="hasAbnormal(o)" class="abnormal-hint">异常</span>
                </div>
              </div>
            </div>
            <div v-else class="empty-inline">
              <span class="material-symbols-outlined">biotech</span>
              暂无检验与观察记录
            </div>
          </div>
        </div>

        <!-- AI 智能摘要侧栏（常驻，由病情/检验/问卷数据自动拼装） -->
        <aside class="detail-aside">
          <div class="ai-card">
            <div class="ai-glow" />
            <div class="ai-head">
              <span class="material-symbols-outlined ai-head-icon">psychology_alt</span>
              <h2 class="ai-title">AI 智能摘要</h2>
            </div>
            <p class="ai-text">{{ aiSummary }}</p>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fhirService, type PatientOverviewRecord } from '@/api/services/fhir.service'
import type { HealthProfile } from '@/api/services/user.service'
import {
  type FhirPatient,
  type FhirCondition,
  type FhirObservation,
  patientInitial,
  patientName,
  genderText,
  calcAge,
  patientPhone,
  maskPhone,
  patientIdentifier,
  maskIdNumber,
  patientAddress,
  formatDate,
  conditionName,
  conditionNote,
  clinicalStatusMeta,
  observationName,
  observationValue,
  observationStatusText,
  hasAbnormal
} from '@/api/types/fhir'

const route = useRoute()
const router = useRouter()

/** 当前患者 fhirId（路由参数） */
const fhirId = computed(() => route.params.id as string)

// 页面状态
const loading = ref(true)
const patient = ref<FhirPatient | null>(null)
const conditions = ref<FhirCondition[]>([])
const observations = ref<FhirObservation[]>([])
// 健康档案与问卷记录来自聚合接口 /his/fhir/Patient/{fhirId}/overview
const hasHealthProfile = ref(false)
const healthProfile = ref<HealthProfile | null>(null)
const questionnaireRecords = ref<PatientOverviewRecord[]>([])
// 默认进入「病情记录」Tab（对齐设计稿第一页；原「基本信息」并入 Hero 卡）
const activeTab = ref('condition')

/** Tab 配置（基本信息已并入 Hero） */
const tabs = [
  { key: 'condition', label: '病情记录' },
  { key: 'health', label: '健康档案' },
  { key: 'questionnaire', label: '问卷记录' },
  { key: 'observation', label: '检验与观察' }
]

// ==================== 展示辅助 ====================

/** 年龄显示 */
const ageText = computed(() => {
  const age = calcAge(patient.value?.birthDate)
  return age === null ? '—' : age
})

const phoneOf = computed(() => patientPhone(patient.value))
const identifierOf = computed(() => patientIdentifier(patient.value))
const addressOf = computed(() => {
  const addr = patientAddress(patient.value)
  return addr || '—'
})

/** 病情状态显示 */
const statusOf = (c: FhirCondition) => clinicalStatusMeta(c.clinicalStatus?.coding?.[0]?.code)
const statusTextOf = (c: FhirCondition) => statusOf(c).text
const statusColorOf = (c: FhirCondition) => statusOf(c).color

/** AI 摘要：由健康档案/病情/检验/问卷数据自动拼装 */
const aiSummary = computed(() => {
  const parts: string[] = []
  const hp = healthProfile.value
  if (hp) {
    if (hp.chronicDiseases?.length) parts.push(`慢性病：${hp.chronicDiseases.join('、')}`)
    if (hp.allergies?.length) parts.push(`过敏：${hp.allergies.join('、')}`)
    if (hp.medicationHistory?.length) parts.push(`用药：${hp.medicationHistory.join('、')}`)
  }
  if (conditions.value.length) parts.push(`共 ${conditions.value.length} 条病情记录`)
  if (observations.value.length) parts.push(`${observations.value.length} 条检验观察`)
  if (questionnaireRecords.value.length) parts.push(`${questionnaireRecords.value.length} 份问卷`)
  const dates = observations.value.map(o => o.effectiveDateTime).filter(Boolean) as string[]
  if (dates.length) {
    parts.push(`最近检验 ${formatDate(dates.sort().reverse()[0])}`)
  }
  if (!parts.length) {
    return '暂无足够的病情与检验数据生成摘要，建议后续关注。'
  }
  return `患者当前共有${parts.join('、')}。建议结合上述记录持续关注健康状况。`
})

/** Hero 当前状态徽标：依据临床记录与健康档案推断（有病情→在管；有检验/问卷→随访中；否则新患者） */
const statusMeta = computed<{ text: string; tone: string }>(() => {
  if (conditions.value.length) return { text: '在管患者', tone: 'active' }
  if (observations.value.length || questionnaireRecords.value.length) return { text: '随访中', tone: 'follow' }
  return { text: '新患者', tone: 'new' }
})

/** 最近一条临床记录时间（检验/问卷的最近时间，用于 Hero 展示） */
const recentVisitText = computed(() => {
  const dates: string[] = []
  observations.value.forEach(o => {
    if (o.effectiveDateTime) dates.push(o.effectiveDateTime)
  })
  questionnaireRecords.value.forEach(qr => {
    if (qr.authoredDate) dates.push(qr.authoredDate)
  })
  if (!dates.length) return '暂无就诊记录'
  const latest = dates.sort().reverse()[0]
  return `最近记录 ${formatDate(latest)}`
})

// ==================== 健康档案辅助 ====================

/** 健康档案分组展示（过滤空值项） */
const healthGroups = computed(() => {
  const hp = healthProfile.value
  const mk = (label: string, items?: string[]) => ({ label, items: items?.filter(Boolean) || [] })
  return [
    mk('过敏史', hp?.allergies),
    mk('慢性病史', hp?.chronicDiseases),
    mk('用药史', hp?.medicationHistory),
    mk('手术史', hp?.surgicalHistory),
    mk('家族病史', hp?.familyHistory)
  ]
})

/** 问卷严重程度色系（重度红/中度橙/其余灰蓝） */
const severityTone = (s?: string | null) => {
  if (!s) return ''
  if (s.includes('重度') || s.includes('严重')) return 'severity-high'
  if (s.includes('中度')) return 'severity-mid'
  return 'severity-low'
}

// ==================== 动作 ====================

// 返回检索页
const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/patients')
  }
}

// 新建问诊：跳转智能问答页
const handleNewConsult = () => {
  router.push('/qa')
}

// 打印报告（浏览器打印当前页面）
const handlePrint = () => {
  window.print()
}

// 初始化：并行加载患者档案概览（含健康档案与问卷记录）与病情/检验记录
onMounted(async () => {
  loading.value = true
  try {
    const id = fhirId.value
    const [overview, cs, obs] = await Promise.all([
      fhirService.getPatientOverview(id),
      fhirService.getConditions(id),
      fhirService.getObservations(id)
    ])
    patient.value = overview?.patient ?? null
    hasHealthProfile.value = overview?.hasHealthProfile ?? false
    healthProfile.value = overview?.healthProfile ?? null
    questionnaireRecords.value = overview?.questionnaireResponses ?? []
    conditions.value = cs
    observations.value = obs
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ===== 页面容器 ===== */
.patient-detail-page {
  max-width: 80rem;
  margin: 0 auto;
  padding-bottom: 3rem;
}

/* ===== 返回 ===== */
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  background: none;
  border: none;
  color: var(--theme-on-surface-variant);
  font-size: 0.8125rem;
  cursor: pointer;
  padding: 0.25rem 0;
  margin-bottom: 1.25rem;
  transition: color 150ms;
}

.back-link:hover {
  color: var(--theme-primary);
}

.back-link .material-symbols-outlined {
  font-size: 1rem;
}

/* ===== 患者 Hero 卡 ===== */
.quick-header {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.quick-top {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

@media (min-width: 640px) {
  .quick-top {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

.quick-left {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  min-width: 0;
}

.quick-avatar {
  width: 5rem;
  height: 5rem;
  border-radius: 9999px;
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.75rem;
  font-weight: 700;
  flex-shrink: 0;
  border: 1px solid var(--theme-outline-variant);
}

.quick-name-row {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  flex-wrap: wrap;
  margin-bottom: 0.375rem;
}

.quick-name {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--theme-on-surface);
  letter-spacing: -0.02em;
  margin: 0;
}

.quick-badge {
  padding: 0.125rem 0.625rem;
  border-radius: 0.25rem;
  background: var(--theme-surface-container);
  color: var(--theme-on-surface-variant);
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.patient-id {
  font-size: 0.8125rem;
  color: var(--theme-on-surface-variant);
  font-family: 'JetBrains Mono', monospace;
  margin: 0 0 0.25rem;
}

.hero-recent {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: var(--theme-outline);
}

.hero-recent-icon {
  font-size: 0.875rem;
}

/* Hero 状态徽标 */
.hero-status {
  padding: 0.125rem 0.625rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}

.hero-status-active {
  background: var(--theme-primary-soft);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-primary);
}

.hero-status-follow {
  background: rgba(237, 108, 2, 0.1);
  border: 1px solid rgba(237, 108, 2, 0.3);
  color: #9a3412;
}

.hero-status-new {
  background: var(--theme-surface-container);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-on-surface-variant);
}

.quick-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

/* 核心信息条 */
.hero-basic {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.875rem 1.25rem;
  border-top: 1px solid var(--theme-outline-variant);
  padding-top: 1.25rem;
}

@media (min-width: 768px) {
  .hero-basic {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .hero-basic {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.basic-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.basic-label {
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
}

.basic-value {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--theme-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== Tab 导航 ===== */
.tab-nav {
  display: flex;
  gap: 0.25rem;
  border-bottom: 1px solid var(--theme-outline-variant);
  margin-bottom: 1.5rem;
  overflow-x: auto;
}

.tab-btn {
  padding: 0.75rem 1rem;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  cursor: pointer;
  white-space: nowrap;
  transition: color 150ms;
}

.tab-btn:hover {
  color: var(--theme-primary);
}

.tab-btn.active {
  color: var(--theme-primary);
  border-bottom-color: var(--theme-primary);
}

/* ===== 主体布局：Tab 区（8/12）+ AI 摘要侧栏（4/12） ===== */
.detail-body {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  align-items: start;
}

@media (min-width: 1024px) {
  .detail-body {
    grid-template-columns: repeat(12, minmax(0, 1fr));
  }
}

.detail-main {
  min-width: 0;
}

@media (min-width: 1024px) {
  .detail-main {
    grid-column: span 8;
  }
}

.detail-aside {
  position: sticky;
  top: 1.5rem;
}

@media (min-width: 1024px) {
  .detail-aside {
    grid-column: span 4;
  }
}

.info-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1.25rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  border-bottom: 1px solid var(--theme-outline-variant);
  padding-bottom: 0.75rem;
  margin-bottom: 1rem;
}

.card-title .material-symbols-outlined {
  color: var(--theme-on-surface-variant);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem 1.25rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label {
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
}

.info-value {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--theme-on-surface);
}

/* ===== 健康档案 ===== */
.hp-section {
  padding: 0.875rem 0 0.25rem;
  border-top: 1px dashed var(--theme-outline-variant);
  margin-top: 0.875rem;
}

.hp-section:first-of-type {
  border-top: none;
  padding-top: 0.5rem;
  margin-top: 0;
}

.hp-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  margin-bottom: 0.5rem;
}

.hp-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  list-style: none;
  padding: 0;
  margin: 0;
}

.hp-item {
  padding: 0.25rem 0.75rem;
  background: var(--theme-surface-container-low);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 9999px;
  font-size: 0.8125rem;
  color: var(--theme-on-surface);
}

.hp-empty {
  font-size: 0.8125rem;
  color: var(--theme-outline);
}

/* ===== AI 摘要侧栏 ===== */
.ai-card {
  position: relative;
  background: var(--theme-surface-container-low);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 22, 48, 0.06);
}

.ai-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 5rem;
  background: linear-gradient(to bottom, var(--theme-primary-soft), transparent);
  pointer-events: none;
}

.ai-head {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  position: relative;
}

.ai-head-icon {
  color: var(--theme-primary);
  font-size: 1.25rem;
}

.ai-title {
  display: flex;
  align-items: center;
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0;
}

.ai-text {
  position: relative;
  font-size: 0.875rem;
  line-height: 1.7;
  color: var(--theme-on-surface-variant);
  margin: 0;
}

/* ===== 列表 Tab 通用 ===== */
.list-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.list-item {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid rgba(195, 198, 208, 0.5);
}

.list-item:last-child {
  border-bottom: none;
}

.list-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.list-item-head.clickable {
  cursor: pointer;
}

.item-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--theme-on-surface);
}

.item-sub {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.375rem;
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
}

.status-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}

.status-badge.neutral {
  background: var(--theme-surface-container);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-on-surface-variant);
}

.item-note {
  margin-top: 0.5rem;
  font-size: 0.8125rem;
  color: var(--theme-on-surface-variant);
  background: var(--theme-surface-container-low);
  border-radius: 0.375rem;
  padding: 0.5rem 0.75rem;
}

.item-result {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  font-family: 'JetBrains Mono', monospace;
}

.item-result.abnormal {
  color: var(--theme-error);
}

.abnormal-hint {
  color: var(--theme-error);
  font-weight: 600;
}

/* ===== 问卷明细 ===== */
.qr-title-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.qr-icon {
  font-size: 1.125rem;
  color: var(--theme-primary);
}

.qr-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
  flex-shrink: 0;
}

/* 问卷评分徽标（按严重程度着色） */
.score-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
  background: var(--theme-surface-container);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-on-surface-variant);
}

.score-badge.severity-high {
  background: rgba(186, 26, 26, 0.1);
  border-color: rgba(186, 26, 26, 0.3);
  color: var(--theme-error);
}

.score-badge.severity-mid {
  background: rgba(237, 108, 2, 0.1);
  border-color: rgba(237, 108, 2, 0.3);
  color: #9a3412;
}

.score-badge.severity-low {
  background: var(--theme-primary-soft);
  border-color: var(--theme-outline-variant);
  color: var(--theme-primary);
}

/* ===== 空态（整页 / 内联） ===== */
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

.empty-inline {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 3rem 1rem;
  border: 1px dashed var(--theme-outline-variant);
  border-radius: 0.75rem;
  background: var(--theme-surface-container-lowest);
  color: var(--theme-outline);
  font-size: 0.875rem;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--theme-primary);
  border: 1px solid var(--theme-primary);
  color: var(--theme-on-primary);
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
  box-shadow: 0 4px 12px rgba(0, 22, 48, 0.2);
}

.btn-primary:hover {
  background: var(--theme-primary-strong);
  border-color: var(--theme-primary-strong);
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-on-surface-variant);
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}

.btn-outline:hover {
  background: var(--theme-surface-container);
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
