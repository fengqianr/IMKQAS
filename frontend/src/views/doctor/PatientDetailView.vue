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
      <!-- 患者快速信息 Hero -->
      <div class="quick-header">
        <div class="quick-left">
          <div class="quick-avatar">{{ patientInitial(patient) }}</div>
          <div>
            <h1 class="quick-name">{{ patientName(patient) }}</h1>
            <div class="quick-meta">
              <span class="meta-item">
                <span class="material-symbols-outlined meta-icon">{{ genderIcon }}</span>
                {{ genderText(patient?.gender) }}
              </span>
              <span class="meta-dot" />
              <span>{{ ageText }} 岁</span>
              <span class="meta-dot" />
              <span class="patient-id">患者编号: {{ patient?.id }}</span>
            </div>
            <div class="hero-tags">
              <span class="hero-status" :class="'hero-status-' + statusMeta.tone">{{ statusMeta.text }}</span>
              <span class="hero-recent">{{ recentVisitText }}</span>
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

      <!-- Tab 内容：基本信息 -->
      <div v-if="activeTab === 'basic'" class="tab-content">
        <!-- 核心信息 -->
        <div class="info-card core-card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">id_card</span>
            核心信息
          </h2>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">出生日期</span>
              <span class="info-value">{{ formatDate(patient?.birthDate) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">联系电话</span>
              <span class="info-value">{{ maskPhone(phoneOf) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">证件号码</span>
              <span class="info-value">{{ maskIdNumber(identifierOf?.value) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">家庭住址</span>
              <span class="info-value">{{ addressOf }}</span>
            </div>
          </div>
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
                <span class="info-value">{{ healthGenderText(healthProfile.gender) }}</span>
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

      <!-- Tab 内容：病情记录 -->
      <div v-else-if="activeTab === 'condition'" class="tab-content">
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
        </div>

        <!-- AI 智能摘要侧栏（常驻，由病情/检验/问卷数据自动拼装） -->
        <aside class="detail-aside">
          <div class="ai-card">
            <h2 class="ai-title">
              <span class="material-symbols-outlined">auto_awesome</span>
              AI 智能摘要
            </h2>
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
const activeTab = ref('basic')

/** Tab 配置 */
const tabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'health', label: '健康档案' },
  { key: 'condition', label: '病情记录' },
  { key: 'observation', label: '检验与观察' },
  { key: 'questionnaire', label: '问卷记录' }
]

// ==================== 展示辅助 ====================

/** 性别图标（material symbols） */
const genderIcon = computed(() => {
  const g = patient.value?.gender
  if (g === 'male') return 'male'
  if (g === 'female') return 'female'
  return 'transgender'
})

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

/** 健康档案性别显示：MALE/FEMALE/OTHER -> 男/女/其他 */
const healthGenderText = (g?: string) => {
  if (!g) return '—'
  return ({ MALE: '男', FEMALE: '女', OTHER: '其他' } as Record<string, string>)[g] || g
}

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
  color: #4a5f83;
  font-size: 0.8125rem;
  cursor: pointer;
  padding: 0.25rem 0;
  margin-bottom: 1.25rem;
  transition: color 150ms;
}

.back-link:hover {
  color: #0891b2;
}

.back-link .material-symbols-outlined {
  font-size: 1rem;
}

/* ===== 快速信息头 ===== */
.quick-header {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

@media (min-width: 640px) {
  .quick-header {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

.quick-left {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.quick-avatar {
  width: 4rem;
  height: 4rem;
  border-radius: 9999px;
  background: #d0e1fb;
  color: #54647a;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  font-weight: 700;
  flex-shrink: 0;
}

.quick-name {
  font-size: 2rem;
  font-weight: 700;
  color: #191c1d;
  letter-spacing: -0.02em;
  margin-bottom: 0.25rem;
}

.quick-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.875rem;
  color: #4a5f83;
  flex-wrap: wrap;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.meta-icon {
  font-size: 1rem;
}

.meta-dot {
  width: 0.25rem;
  height: 0.25rem;
  border-radius: 9999px;
  background: #c2c6d4;
}

.patient-id {
  font-family: 'JetBrains Mono', monospace;
}

/* Hero 状态徽标与最近记录 */
.hero-tags {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
  flex-wrap: wrap;
}

.hero-status {
  padding: 0.125rem 0.625rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}

.hero-status-active {
  background: #cffafe;
  border: 1px solid #22d3ee;
  color: #155e75;
}

.hero-status-follow {
  background: #dbeafe;
  border: 1px solid #93c5fd;
  color: #1e40af;
}

.hero-status-new {
  background: #eceef0;
  border: 1px solid #c2c6d4;
  color: #4a5f83;
}

.hero-recent {
  font-size: 0.75rem;
  color: #727783;
}

.quick-actions {
  display: flex;
  gap: 0.5rem;
}

/* ===== Tab 导航 ===== */
.tab-nav {
  display: flex;
  gap: 1rem;
  border-bottom: 1px solid #c2c6d4;
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
  color: #3e484d;
  cursor: pointer;
  white-space: nowrap;
  transition: color 150ms;
}

.tab-btn:hover {
  color: #0891b2;
}

.tab-btn.active {
  color: #0891b2;
  border-bottom-color: #0891b2;
}

/* ===== 主体布局：Tab 区 + AI 摘要侧栏 ===== */
.detail-body {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  align-items: start;
}

@media (min-width: 1024px) {
  .detail-body {
    grid-template-columns: 1fr 20rem;
  }
}

.detail-main {
  min-width: 0;
}

.detail-aside {
  position: sticky;
  top: 1.5rem;
}

.info-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.5rem;
  padding: 1.25rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.125rem;
  font-weight: 600;
  color: #191c1d;
  border-bottom: 1px solid #c2c6d4;
  padding-bottom: 0.75rem;
  margin-bottom: 1rem;
}

.card-title .material-symbols-outlined {
  color: #4a5f83;
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
  color: #4a5f83;
}

.info-value {
  font-size: 0.875rem;
  font-weight: 500;
  color: #191c1d;
}

/* ===== 健康档案 ===== */
.hp-section {
  padding: 0.875rem 0 0.25rem;
  border-top: 1px dashed #e2e8f0;
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
  color: #4a5f83;
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
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 9999px;
  font-size: 0.8125rem;
  color: #191c1d;
}

.hp-empty {
  font-size: 0.8125rem;
  color: #727783;
}

.ai-card {
  background: linear-gradient(180deg, #f0f9fb 0%, #ffffff 100%);
  border-color: rgba(8, 145, 178, 0.25);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  box-shadow: 0 4px 16px rgba(8, 145, 178, 0.08);
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #0891b2;
}

.ai-title .material-symbols-outlined {
  font-size: 1.125rem;
}

.ai-text {
  font-size: 0.875rem;
  line-height: 1.6;
  color: #3e484d;
}

/* ===== 列表 Tab 通用 ===== */
.list-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.5rem;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.list-item {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e2e8f0;
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
  color: #191c1d;
}

.item-sub {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.375rem;
  font-size: 0.75rem;
  color: #4a5f83;
}

.status-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}

.status-badge.neutral {
  background: #eceef0;
  border: 1px solid #c2c6d4;
  color: #4a5f83;
}

.item-note {
  margin-top: 0.5rem;
  font-size: 0.8125rem;
  color: #3e484d;
  background: #f2f4f6;
  border-radius: 0.375rem;
  padding: 0.5rem 0.75rem;
}

.item-result {
  font-size: 0.9375rem;
  font-weight: 600;
  color: #191c1d;
  font-family: 'JetBrains Mono', monospace;
}

.item-result.abnormal {
  color: #ba1a1a;
}

.abnormal-hint {
  color: #ba1a1a;
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
  color: #0891b2;
}

.qr-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.75rem;
  color: #4a5f83;
  flex-shrink: 0;
}

/* 问卷评分徽标（按严重程度着色） */
.score-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
  background: #eceef0;
  border: 1px solid #c2c6d4;
  color: #4a5f83;
}

.score-badge.severity-high {
  background: #fee2e2;
  border-color: #f87171;
  color: #991b1b;
}

.score-badge.severity-mid {
  background: #ffedd5;
  border-color: #fb923c;
  color: #9a3412;
}

.score-badge.severity-low {
  background: #dbeafe;
  border-color: #93c5fd;
  color: #1e40af;
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
  color: #0891b2;
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

.empty-inline {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 3rem 1rem;
  border: 1px dashed #c2c6d4;
  border-radius: 0.5rem;
  background: #ffffff;
  color: #727783;
  font-size: 0.875rem;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: #0891b2;
  border: 1px solid #0891b2;
  color: #ffffff;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
  box-shadow: 0 4px 12px rgba(8, 145, 178, 0.25);
}

.btn-primary:hover {
  background: #0e7490;
  border-color: #0e7490;
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #4a5f83;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}

.btn-outline:hover {
  background: #f2f4f6;
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
