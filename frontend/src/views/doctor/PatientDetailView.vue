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
      <!-- 患者快速信息头 -->
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
              <span class="patient-id">ID: {{ patient?.id }}</span>
            </div>
          </div>
        </div>
        <div class="quick-actions">
          <button class="btn-outline" @click="handlePrint">
            <span class="material-symbols-outlined">print</span>
            打印报告
          </button>
        </div>
      </div>

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
        <div class="basic-grid">
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

          <!-- AI 智能摘要（由现有病情/检验/问卷数据自动拼装） -->
          <div class="info-card ai-card">
            <h2 class="ai-title">
              <span class="material-symbols-outlined">auto_awesome</span>
              AI 智能摘要
            </h2>
            <p class="ai-text">{{ aiSummary }}</p>
          </div>
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
      <div v-else class="tab-content">
        <div v-if="questionnaires.length" class="list-card">
          <div v-for="qr in questionnaires" :key="qr.id" class="list-item">
            <div class="list-item-head clickable" @click="toggleQR(qr.id)">
              <div class="qr-title-wrap">
                <span class="material-symbols-outlined qr-icon">assignment</span>
                <span class="item-title">{{ questionnaireLabel(qr) }}</span>
              </div>
              <div class="qr-meta">
                <span class="status-badge neutral">{{ questionnaireStatusText(qr.status) }}</span>
                <span v-if="qr.authored">{{ formatDate(qr.authored) }}</span>
                <span class="material-symbols-outlined expand-icon" :class="{ expanded: expandedQR === qr.id }">
                  expand_more
                </span>
              </div>
            </div>
            <div v-if="expandedQR === qr.id" class="qr-detail">
              <div v-for="(qa, i) in flattenQR(qr.item)" :key="i" class="qr-row">
                <div class="qr-question">{{ qa.question }}</div>
                <div class="qr-answer">{{ qa.answer }}</div>
              </div>
              <div v-if="!flattenQR(qr.item).length" class="qr-empty">该问卷无回答明细</div>
            </div>
          </div>
        </div>
        <div v-else class="empty-inline">
          <span class="material-symbols-outlined">fact_check</span>
          暂无问卷记录
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fhirService } from '@/api/services/fhir.service'
import {
  type FhirPatient,
  type FhirCondition,
  type FhirObservation,
  type FhirQuestionnaireResponse,
  type FhirQuestionnaireResponseItem,
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
  hasAbnormal,
  questionnaireStatusText,
  answerText
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
const questionnaires = ref<FhirQuestionnaireResponse[]>([])
const activeTab = ref('basic')
const expandedQR = ref<string | null>(null)

/** Tab 配置 */
const tabs = [
  { key: 'basic', label: '基本信息' },
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

/** AI 摘要：由已有病情/检验/问卷数据自动拼装 */
const aiSummary = computed(() => {
  const parts: string[] = []
  if (conditions.value.length) parts.push(`共 ${conditions.value.length} 条病情记录`)
  if (observations.value.length) parts.push(`${observations.value.length} 条检验观察`)
  if (questionnaires.value.length) parts.push(`${questionnaires.value.length} 份问卷`)
  const dates = observations.value.map(o => o.effectiveDateTime).filter(Boolean) as string[]
  if (dates.length) {
    parts.push(`最近检验 ${formatDate(dates.sort().reverse()[0])}`)
  }
  if (!parts.length) {
    return '暂无足够的病情与检验数据生成摘要，建议后续关注。'
  }
  return `患者当前共有${parts.join('、')}。建议结合上述记录持续关注健康状况。`
})

/** 问卷标题 */
const questionnaireLabel = (qr: FhirQuestionnaireResponse) => {
  const ref = qr.questionnaire
  const id = ref && ref.includes('/') ? ref.split('/').pop() : ref
  return `问卷记录${id ? ` #${id}` : ''}`
}

/** 展开/收起问卷明细 */
const toggleQR = (id?: string) => {
  if (!id) return
  expandedQR.value = expandedQR.value === id ? null : id
}

/** 问答对 */
interface QnAPair {
  question: string
  answer: string
}

/** 将问卷 item 树扁平化为问答对列表 */
const flattenQR = (items?: FhirQuestionnaireResponseItem[]): QnAPair[] => {
  const result: QnAPair[] = []
  const walk = (list?: FhirQuestionnaireResponseItem[]) => {
    if (!list) return
    for (const it of list) {
      const question = it.text || it.linkId || ''
      const answer = it.answer?.map(answerText).filter(Boolean).join('、')
      if (question) {
        result.push({ question, answer: answer || '—' })
      }
      walk(it.item)
    }
  }
  walk(items)
  return result
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

// 打印报告（浏览器打印当前页面）
const handlePrint = () => {
  window.print()
}

// 初始化：并行加载患者档案与三类临床记录
onMounted(async () => {
  loading.value = true
  try {
    const id = fhirId.value
    const [p, cs, obs, qrs] = await Promise.all([
      fhirService.getPatient(id),
      fhirService.getConditions(id),
      fhirService.getObservations(id),
      fhirService.getQuestionnaireResponses(id)
    ])
    patient.value = p
    conditions.value = cs
    observations.value = obs
    questionnaires.value = qrs
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
  color: #005eb8;
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
  color: #005eb8;
}

.tab-btn.active {
  color: #005eb8;
  border-bottom-color: #005eb8;
}

/* ===== 基本信息 Tab ===== */
.basic-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
}

@media (min-width: 1024px) {
  .basic-grid {
    grid-template-columns: 2fr 1fr;
  }
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

.ai-card {
  background: #f2f4f6;
  border-color: rgba(0, 94, 184, 0.2);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #005eb8;
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
  color: #005eb8;
}

.qr-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.75rem;
  color: #4a5f83;
  flex-shrink: 0;
}

.expand-icon {
  font-size: 1.25rem;
  transition: transform 150ms;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.qr-detail {
  margin-top: 0.75rem;
  border-top: 1px dashed #c2c6d4;
  padding-top: 0.75rem;
}

.qr-row {
  display: flex;
  gap: 1rem;
  padding: 0.375rem 0;
  font-size: 0.8125rem;
}

.qr-question {
  flex: 1;
  color: #3e484d;
}

.qr-answer {
  flex: 1;
  color: #191c1d;
  font-weight: 500;
}

.qr-empty {
  font-size: 0.8125rem;
  color: #727783;
  font-style: italic;
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
