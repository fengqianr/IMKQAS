<template>
  <div class="triage-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">批量导诊</h1>
        <p class="page-subtitle">批量输入患者症状描述，自动推荐就诊科室并识别急诊风险</p>
      </div>
    </div>

    <div class="triage-main">
      <!-- 左侧：症状清单输入卡片 -->
      <aside class="input-card">
        <div class="input-card-head">
          <h2 class="input-card-title">症状清单</h2>
          <span class="symptom-count">{{ symptomCount }} / 20</span>
        </div>

        <div class="symptom-list">
          <div v-for="(_, i) in symptoms" :key="i" class="symptom-row">
            <span class="symptom-index">{{ i + 1 }}</span>
            <el-input
              v-model="symptoms[i]"
              class="symptom-input"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 3 }"
              :maxlength="200"
              :placeholder="`请输入症状描述 ${i + 1}，如：发热伴咳嗽`"
              @keyup.enter="addSymptom"
            />
            <button class="symptom-remove" title="删除该条" @click="removeSymptom(i)">
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
        </div>

        <button
          class="add-symptom-btn"
          :disabled="symptomCount >= 20"
          @click="addSymptom"
        >
          <span class="material-symbols-outlined">add</span>
          添加症状
        </button>

        <el-button
          type="primary"
          class="triage-submit-btn"
          :loading="loading"
          :disabled="symptomCount === 0"
          @click="handleBatchTriage"
        >
          <span class="material-symbols-outlined">triage</span>
          批量导诊
        </el-button>
        <p v-if="symptomCount >= 20" class="input-hint">已达 20 条上限，请删除后继续添加</p>
      </aside>

      <!-- 右侧：结果区 -->
      <section class="result-area">
        <!-- 急诊警示条（关键安全警报，danger 语义） -->
        <div v-if="emergencyItems.length" class="emergency-banner">
          <div class="emergency-title-row">
            <span class="material-symbols-outlined emergency-icon">emergency</span>
            <span class="emergency-title">检测到 {{ emergencyItems.length }} 条急诊风险</span>
            <span v-if="highestLevel" class="emergency-level-badge">{{ emergencyLevelText(highestLevel) }}</span>
          </div>
          <div v-for="(item, i) in emergencyItems" :key="i" class="emergency-item">
            <p class="emergency-symptom">{{ item.symptoms }}</p>
            <p v-if="item.emergencyCheck?.warningMessage" class="emergency-warning">
              {{ item.emergencyCheck.warningMessage }}
            </p>
            <p v-if="item.emergencyCheck?.immediateAction" class="emergency-action">
              <strong>立即行动：</strong>{{ item.emergencyCheck.immediateAction }}
            </p>
          </div>
        </div>

        <!-- 错误态：分诊服务不可用 -->
        <div v-if="error" class="error-card">
          <div class="error-icon-wrap">
            <span class="material-symbols-outlined">cloud_off</span>
          </div>
          <h3 class="error-title">分诊服务不可用</h3>
          <p class="error-desc">{{ error }}</p>
          <el-button class="error-retry" @click="handleBatchTriage">重试</el-button>
        </div>

        <!-- 加载态 -->
        <div v-else-if="loading" class="loading-box">
          <div class="loading-dot" />
          <p>正在分析症状，请稍候…</p>
        </div>

        <!-- 结果列表 -->
        <template v-else-if="results.length">
          <div class="result-summary">
            <span class="material-symbols-outlined">fact_check</span>
            共 {{ results.length }} 条症状已完成导诊
          </div>
          <div
            v-for="(r, i) in results"
            :key="i"
            class="result-card"
            :class="{ 'result-card-emergency': r.emergencyCheck?.emergency }"
          >
            <div class="result-head">
              <span class="result-index">#{{ i + 1 }}</span>
              <p class="result-symptom">{{ r.symptoms }}</p>
              <span class="source-badge">{{ sourceText(r.source) }}</span>
            </div>
            <div class="result-meta">
              <span v-if="r.confidence != null">置信度 {{ confidenceText(r.confidence) }}</span>
              <span v-if="r.processingTimeMs != null">耗时 {{ r.processingTimeMs }}ms</span>
            </div>

            <div class="recommendations">
              <div v-for="(rec, ri) in r.recommendations" :key="ri" class="rec-item">
                <div class="rec-head">
                  <span class="rec-name">{{ rec.departmentName }}</span>
                  <span v-if="rec.emergency" class="rec-emergency-tag">急诊</span>
                  <span class="rec-confidence">{{ confidenceText(rec.confidence) }}</span>
                </div>
                <div class="rec-bar">
                  <div class="rec-bar-fill" :style="{ width: confidenceWidth(rec.confidence) }" />
                </div>
                <div v-if="rec.matchedSymptoms?.length" class="rec-chips">
                  <span v-for="m in rec.matchedSymptoms" :key="m" class="rec-chip">{{ m }}</span>
                </div>
                <p v-if="rec.reason" class="rec-reason">{{ rec.reason }}</p>
              </div>
            </div>

            <p v-if="r.advice" class="result-advice">
              <span class="material-symbols-outlined advice-icon">lightbulb</span>
              {{ r.advice }}
            </p>
          </div>
        </template>

        <!-- 空态引导 -->
        <div v-else class="empty-box">
          <div class="empty-icon">
            <span class="material-symbols-outlined">triage</span>
          </div>
          <h3 class="empty-title">待进行批量导诊</h3>
          <p class="empty-desc">在左侧输入症状清单（最多 20 条），点击「批量导诊」获取科室推荐结果。</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { triageService } from '@/api/services/triage.service'
import {
  type DepartmentTriageResult,
  sourceText,
  emergencyLevelText,
  confidenceText
} from '@/api/types/triage'

/** 症状清单：每行一条，最多 20 条 */
const symptoms = ref<string[]>([''])
const loading = ref(false)
const error = ref('')
const results = ref<DepartmentTriageResult[]>([])

/** 已填写的症状条数（过滤空白后） */
const symptomCount = computed(() => symptoms.value.filter((s) => s.trim()).length)

/** 存在急诊风险的结果条目 */
const emergencyItems = computed(() =>
  results.value.filter((r) => r.emergencyCheck?.emergency === true)
)

/** 急诊分级（取最高等级用于横幅徽标） */
const highestLevel = computed(() => {
  const order: Record<string, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }
  let best = ''
  let bestScore = 0
  for (const item of emergencyItems.value) {
    const level = item.emergencyCheck?.emergencyLevel
    const score = level ? order[level] || 0 : 0
    if (score > bestScore) {
      bestScore = score
      best = level || ''
    }
  }
  return best
})

/** 添加一条症状（达到上限时提示） */
function addSymptom() {
  if (symptomCount.value >= 20) {
    ElMessage.warning('最多支持 20 条症状')
    return
  }
  symptoms.value.push('')
}

/** 删除指定条目的症状 */
function removeSymptom(index: number) {
  if (symptoms.value.length <= 1) {
    symptoms.value[0] = ''
    return
  }
  symptoms.value.splice(index, 1)
}

/** 置信度进度条宽度（空值回退 0） */
function confidenceWidth(conf?: number): string {
  if (conf == null || Number.isNaN(conf)) return '0%'
  return `${Math.max(0, Math.min(100, conf * 100))}%`
}

/** 批量导诊：收集非空症状提交，异常时呈现服务不可用错误态 */
async function handleBatchTriage() {
  const list = symptoms.value.map((s) => s.trim()).filter(Boolean)
  if (list.length === 0) {
    ElMessage.warning('请至少输入一条症状描述')
    return
  }
  loading.value = true
  error.value = ''
  try {
    results.value = await triageService.batchTriage({ symptomsList: list, includeEmergencyCheck: true })
  } catch (e: any) {
    error.value = e?.message || '分诊服务暂不可用，请稍后重试'
    results.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ===== 页面容器 ===== */
.triage-page {
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

/* ===== 主区：左输入 + 右结果 ===== */
.triage-main {
  display: flex;
  gap: 1.25rem;
  align-items: flex-start;
}

/* ===== 左侧输入卡片 ===== */
.input-card {
  width: 360px;
  flex-shrink: 0;
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: var(--radius-lg);
  padding: 1.25rem;
  position: sticky;
  top: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.input-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.input-card-title {
  font-size: 1rem;
  font-weight: 600;
  color: #191c1d;
  margin: 0;
}

.symptom-count {
  font-size: 0.75rem;
  font-weight: 600;
  color: #4a5f83;
  background: #f3f4f5;
  padding: 0.25rem 0.625rem;
  border-radius: var(--radius-full);
}

.symptom-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 22rem;
  overflow-y: auto;
}

.symptom-row {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.symptom-index {
  flex-shrink: 0;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: var(--radius-full);
  background: #eef2f9;
  color: #004a9e;
  font-size: 0.75rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 0.25rem;
}

.symptom-input {
  flex: 1;
  min-width: 0;
}

.symptom-remove {
  flex-shrink: 0;
  width: 1.5rem;
  height: 1.5rem;
  margin-top: 0.375rem;
  border: none;
  background: none;
  cursor: pointer;
  color: #727783;
  border-radius: var(--radius-default);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 150ms, color 150ms;
}

.symptom-remove:hover {
  background: #fef2f2;
  color: #ba1a1a;
}

.symptom-remove .material-symbols-outlined {
  font-size: 1rem;
}

.add-symptom-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  border: 1px dashed #c2c6d4;
  background: #f8f9fa;
  color: #4a5f83;
  font-size: 0.8125rem;
  font-weight: 600;
  border-radius: var(--radius-lg);
  padding: 0.5rem;
  cursor: pointer;
  transition: border-color 150ms, color 150ms;
}

.add-symptom-btn:hover:not(:disabled) {
  border-color: #005eb8;
  color: #005eb8;
}

.add-symptom-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.add-symptom-btn .material-symbols-outlined {
  font-size: 1.125rem;
}

.triage-submit-btn {
  width: 100%;
}

.input-hint {
  font-size: 0.75rem;
  color: #727783;
  text-align: center;
  margin: 0;
}

/* ===== 右侧结果区 ===== */
.result-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* 急诊警示条：仅用于关键安全警报 */
.emergency-banner {
  background: #fde8e8;
  border: 1px solid #f5b9b9;
  border-radius: var(--radius-lg);
  padding: 1rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.emergency-title-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.emergency-icon {
  color: #d32f2f;
  font-size: 1.5rem;
}

.emergency-title {
  font-size: 1rem;
  font-weight: 700;
  color: #b71c1c;
}

.emergency-level-badge {
  margin-left: auto;
  background: #d32f2f;
  color: #ffffff;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.25rem 0.625rem;
  border-radius: var(--radius-full);
}

.emergency-item {
  border-top: 1px solid #f5b9b9;
  padding-top: 0.625rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.emergency-symptom {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #b71c1c;
  margin: 0;
}

.emergency-warning,
.emergency-action {
  font-size: 0.8125rem;
  color: #9a1616;
  line-height: 1.6;
  margin: 0;
}

/* 错误态卡片 */
.error-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: var(--radius-lg);
  padding: 3rem 2rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.625rem;
  text-align: center;
}

.error-icon-wrap {
  width: 3.5rem;
  height: 3.5rem;
  border-radius: var(--radius-full);
  background: #fef2f2;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ba1a1a;
}

.error-icon-wrap .material-symbols-outlined {
  font-size: 2rem;
}

.error-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #191c1d;
  margin: 0;
}

.error-desc {
  font-size: 0.875rem;
  color: #727783;
  max-width: 28rem;
  line-height: 1.6;
  margin: 0;
}

.error-retry {
  margin-top: 0.5rem;
}

/* 加载态 */
.loading-box {
  min-height: 20rem;
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  color: #4a5f83;
  font-size: 0.875rem;
}

.loading-dot {
  width: 1.5rem;
  height: 1.5rem;
  border-radius: var(--radius-full);
  border: 2px solid #c0d5ff;
  border-top-color: #005eb8;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 结果汇总行 */
.result-summary {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #4a5f83;
}

.result-summary .material-symbols-outlined {
  font-size: 1.125rem;
  color: #005eb8;
}

/* 结果卡片 */
.result-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: var(--radius-lg);
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.result-card-emergency {
  border-left: 3px solid #d32f2f;
}

.result-head {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
}

.result-index {
  flex-shrink: 0;
  font-size: 0.75rem;
  font-weight: 700;
  color: #004a9e;
  background: #eef2f9;
  border-radius: var(--radius-default);
  padding: 0.25rem 0.5rem;
}

.result-symptom {
  flex: 1;
  font-size: 1rem;
  font-weight: 600;
  color: #191c1d;
  line-height: 1.5;
  margin: 0;
}

.source-badge {
  flex-shrink: 0;
  font-size: 0.6875rem;
  font-weight: 700;
  color: #004a9e;
  background: #d6e3ff;
  border-radius: var(--radius-full);
  padding: 0.25rem 0.625rem;
}

.result-meta {
  display: flex;
  gap: 1rem;
  font-size: 0.75rem;
  color: #727783;
}

/* 科室推荐 */
.recommendations {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.rec-item {
  background: #f8f9fa;
  border-radius: var(--radius-default);
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.rec-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.rec-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: #191c1d;
}

.rec-emergency-tag {
  font-size: 0.625rem;
  font-weight: 700;
  color: #ffffff;
  background: #d32f2f;
  border-radius: var(--radius-full);
  padding: 0.125rem 0.5rem;
}

.rec-confidence {
  margin-left: auto;
  font-size: 0.75rem;
  font-weight: 600;
  color: #004a9e;
}

.rec-bar {
  height: 0.375rem;
  background: #e7e8e9;
  border-radius: var(--radius-full);
  overflow: hidden;
}

.rec-bar-fill {
  height: 100%;
  background: #005eb8;
  border-radius: var(--radius-full);
  transition: width 300ms ease;
}

.rec-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.rec-chip {
  font-size: 0.6875rem;
  font-weight: 500;
  color: #424752;
  background: #e7e8e9;
  border-radius: var(--radius-full);
  padding: 0.125rem 0.5rem;
}

.rec-reason {
  font-size: 0.8125rem;
  color: #424752;
  line-height: 1.6;
  margin: 0;
}

/* 综合建议 */
.result-advice {
  display: flex;
  gap: 0.375rem;
  align-items: flex-start;
  font-size: 0.8125rem;
  color: #424752;
  line-height: 1.6;
  background: #f1f5f9;
  border-radius: var(--radius-default);
  padding: 0.625rem 0.75rem;
  margin: 0;
}

.advice-icon {
  font-size: 1rem;
  color: #005eb8;
  flex-shrink: 0;
  margin-top: 1px;
}

/* 空态 */
.empty-box {
  min-height: 20rem;
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  text-align: center;
  padding: 2rem;
}

.empty-icon {
  width: 4rem;
  height: 4rem;
  border-radius: var(--radius-full);
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
  margin: 0;
}

.empty-desc {
  font-size: 0.875rem;
  color: #727783;
  max-width: 28rem;
  line-height: 1.6;
  margin: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .triage-main {
    flex-direction: column;
  }
  .input-card {
    width: 100%;
    position: static;
  }
}

.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
