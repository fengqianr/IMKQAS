<template>
  <div class="triage-page">
    <div class="triage-main">
      <!-- 左侧：症状清单输入面板 -->
      <aside class="input-card">
        <div class="input-card-head">
          <h2 class="input-card-title">症状清单</h2>
          <span class="symptom-count">{{ symptomCount }} / 20</span>
        </div>
        <p class="input-card-sub">逐行输入患者症状描述，或复制批量病历后分行粘贴</p>

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
          <span class="material-symbols-outlined">model_training</span>
          批量导诊
        </el-button>
        <p v-if="symptomCount >= 20" class="input-hint">已达 20 条上限，请删除后继续添加</p>
      </aside>

      <!-- 右侧：导诊结果区 -->
      <section class="result-area">
        <!-- 急诊警示条（关键安全警报，error 语义） -->
        <div v-if="emergencyItems.length" class="emergency-banner">
          <div class="emergency-title-row">
            <span class="material-symbols-outlined emergency-icon">warning</span>
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
              <span class="result-index" :class="{ 'result-index-emergency': r.emergencyCheck?.emergency }">Case #{{ i + 1 }}</span>
              <p class="result-symptom">{{ r.symptoms }}</p>
              <span class="source-badge">{{ sourceText(r.source) }}</span>
            </div>
            <div class="result-meta">
              <span v-if="r.confidence != null">置信度 {{ confidenceText(r.confidence) }}</span>
              <span v-if="r.processingTimeMs != null">耗时 {{ r.processingTimeMs }}ms</span>
            </div>

            <div class="recommendations">
              <div v-for="(rec, ri) in r.recommendations" :key="ri" class="rec-item">
                <div class="rec-main">
                  <div class="rec-dept">
                    <div class="rec-dept-label">推荐科室</div>
                    <div class="rec-name">
                      <span class="material-symbols-outlined rec-dept-icon" :class="{ 'rec-dept-icon-emergency': rec.emergency }">file_copy</span>
                      <span>{{ rec.departmentName }}</span>
                      <span v-if="rec.emergency" class="rec-emergency-tag">急诊</span>
                    </div>
                  </div>
                  <div class="rec-conf">
                    <div class="rec-conf-head">
                      <span>AI 置信度</span>
                      <span class="rec-confidence">{{ confidenceText(rec.confidence) }}</span>
                    </div>
                    <div class="rec-bar">
                      <div class="rec-bar-fill" :style="{ width: confidenceWidth(rec.confidence) }" />
                    </div>
                  </div>
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
            <span class="material-symbols-outlined">Filter</span>
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

/* ===== 主区：左输入 + 右结果 ===== */
.triage-main {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  align-items: stretch;
}

@media (min-width: 1024px) {
  .triage-main {
    flex-direction: row;
    align-items: flex-start;
  }
}

/* ===== 左侧输入面板（lg 下占 45%） ===== */
.input-card {
  width: 100%;
  flex-shrink: 0;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

@media (min-width: 1024px) {
  .input-card {
    width: 45%;
    position: sticky;
    top: 1.25rem;
  }
}

.input-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.input-card-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0;
  letter-spacing: -0.01em;
}

.input-card-sub {
  font-size: 0.8125rem;
  color: var(--theme-on-surface-variant);
  line-height: 1.5;
  margin: 0;
}

.symptom-count {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  background: var(--theme-surface-container);
  padding: 0.25rem 0.625rem;
  border-radius: 9999px;
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
  border-radius: 9999px;
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
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
  color: var(--theme-outline);
  border-radius: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 150ms, color 150ms;
}

.symptom-remove:hover {
  background: var(--theme-error-container);
  color: var(--theme-error);
}

.symptom-remove .material-symbols-outlined {
  font-size: 1rem;
}

.add-symptom-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  border: 1px dashed var(--theme-outline-variant);
  background: var(--theme-surface-container-low);
  color: var(--theme-on-surface-variant);
  font-size: 0.8125rem;
  font-weight: 600;
  border-radius: 0.5rem;
  padding: 0.5rem;
  cursor: pointer;
  transition: border-color 150ms, color 150ms;
}

.add-symptom-btn:hover:not(:disabled) {
  border-color: var(--theme-primary);
  color: var(--theme-primary);
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

.triage-submit-btn .material-symbols-outlined {
  font-size: 1.125rem;
  margin-right: 0.25rem;
}

.input-hint {
  font-size: 0.75rem;
  color: var(--theme-outline);
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
  background: var(--theme-surface-container-low);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1.25rem;
}

/* 急诊警示条：error-container 底，仅用于关键安全警报 */
.emergency-banner {
  background: var(--theme-error-container);
  border: 1px solid rgba(186, 26, 26, 0.25);
  border-radius: 0.75rem;
  padding: 1rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  flex-shrink: 0;
}

.emergency-title-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.emergency-icon {
  color: var(--theme-error);
  font-size: 1.25rem;
  font-variation-settings: 'FILL' 1;
}

.emergency-title {
  font-size: 0.875rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--theme-on-error-container);
}

.emergency-level-badge {
  margin-left: auto;
  background: var(--theme-error);
  color: var(--theme-on-error);
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.25rem 0.625rem;
  border-radius: 9999px;
}

.emergency-item {
  border-top: 1px solid rgba(186, 26, 26, 0.2);
  padding-top: 0.625rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.emergency-symptom {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-error-container);
  margin: 0;
}

.emergency-warning,
.emergency-action {
  font-size: 0.8125rem;
  color: var(--theme-on-error-container);
  line-height: 1.6;
  margin: 0;
  opacity: 0.9;
}

/* 错误态卡片 */
.error-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
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
  border-radius: 9999px;
  background: var(--theme-error-container);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-error);
}

.error-icon-wrap .material-symbols-outlined {
  font-size: 2rem;
}

.error-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0;
}

.error-desc {
  font-size: 0.875rem;
  color: var(--theme-outline);
  max-width: 28rem;
  line-height: 1.6;
  margin: 0;
}

.error-retry {
  margin-top: 0.5rem;
}

/* 加载态 */
.loading-box {
  flex: 1;
  min-height: 20rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  color: var(--theme-on-surface-variant);
  font-size: 0.875rem;
}

.loading-dot {
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 9999px;
  border: 2px solid var(--theme-primary-container);
  border-top-color: var(--theme-primary);
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
  color: var(--theme-on-surface-variant);
}

.result-summary .material-symbols-outlined {
  font-size: 1.125rem;
  color: var(--theme-primary);
}

/* 结果卡片：左色条编码风险 */
.result-card {
  position: relative;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

/* 左侧色条（默认中性） */
.result-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 0.375rem;
  background: var(--theme-outline-variant);
}

/* 急诊结果：error 色条 */
.result-card-emergency {
  border-color: rgba(186, 26, 26, 0.3);
}

.result-card-emergency::before {
  background: var(--theme-error);
}

.result-head {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
}

.result-index {
  flex-shrink: 0;
  font-size: 0.6875rem;
  font-weight: 700;
  color: var(--theme-primary);
  background: var(--theme-primary-soft);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.25rem;
  padding: 0.25rem 0.5rem;
  font-family: 'JetBrains Mono', monospace;
  white-space: nowrap;
}

.result-index-emergency {
  color: var(--theme-error);
  background: rgba(186, 26, 26, 0.1);
  border-color: rgba(186, 26, 26, 0.2);
}

.result-symptom {
  flex: 1;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--theme-on-surface-variant);
  line-height: 1.5;
  margin: 0;
  padding-left: 0.75rem;
  border-left: 2px solid rgba(195, 198, 208, 0.3);
}

.result-symptom::before {
  content: '“';
}

.result-symptom::after {
  content: '”';
}

.source-badge {
  flex-shrink: 0;
  font-size: 0.6875rem;
  font-weight: 700;
  color: var(--theme-primary);
  background: var(--theme-primary-soft);
  border-radius: 9999px;
  padding: 0.25rem 0.625rem;
}

.result-meta {
  display: flex;
  gap: 1rem;
  font-size: 0.75rem;
  color: var(--theme-outline);
  padding-left: 0.875rem;
}

/* 科室推荐 */
.recommendations {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-left: 0.875rem;
}

.rec-item {
  background: var(--theme-surface-container);
  border-radius: 0.5rem;
  padding: 0.875rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.rec-main {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

@media (min-width: 768px) {
  .rec-main {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
  }
}

.rec-dept {
  min-width: 0;
}

.rec-dept-label {
  font-size: 0.625rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--theme-on-surface-variant);
  margin-bottom: 0.25rem;
}

.rec-name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1rem;
  font-weight: 600;
  color: var(--theme-on-surface);
}

.rec-dept-icon {
  font-size: 1.125rem;
  color: var(--theme-on-surface-variant);
}

.rec-dept-icon-emergency {
  color: var(--theme-error);
}

.rec-emergency-tag {
  font-size: 0.625rem;
  font-weight: 700;
  color: var(--theme-on-error);
  background: var(--theme-error);
  border-radius: 9999px;
  padding: 0.125rem 0.5rem;
}

.rec-conf {
  width: 100%;
  flex-shrink: 0;
}

@media (min-width: 768px) {
  .rec-conf {
    width: 14rem;
  }
}

.rec-conf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.375rem;
  font-size: 0.625rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--theme-on-surface-variant);
}

.rec-confidence {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--theme-primary);
  font-family: 'JetBrains Mono', monospace;
  text-transform: none;
  letter-spacing: 0;
}

.rec-bar {
  height: 0.5rem;
  background: var(--theme-outline-variant);
  border-radius: 9999px;
  overflow: hidden;
  opacity: 0.5;
}

.rec-bar-fill {
  height: 100%;
  background: var(--theme-primary);
  border-radius: 9999px;
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
  color: var(--theme-on-surface-variant);
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 9999px;
  padding: 0.125rem 0.5rem;
}

.rec-reason {
  font-size: 0.8125rem;
  color: var(--theme-on-surface-variant);
  line-height: 1.6;
  margin: 0;
}

/* 综合建议 */
.result-advice {
  display: flex;
  gap: 0.375rem;
  align-items: flex-start;
  font-size: 0.8125rem;
  color: var(--theme-on-surface-variant);
  line-height: 1.6;
  background: var(--theme-primary-soft);
  border-radius: 0.5rem;
  padding: 0.625rem 0.75rem;
  margin: 0 0 0 0.875rem;
}

.advice-icon {
  font-size: 1rem;
  color: var(--theme-primary);
  flex-shrink: 0;
  margin-top: 1px;
}

/* 空态 */
.empty-box {
  flex: 1;
  min-height: 20rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
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
  margin: 0;
}

.empty-desc {
  font-size: 0.875rem;
  color: var(--theme-outline);
  max-width: 28rem;
  line-height: 1.6;
  margin: 0;
}

.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
