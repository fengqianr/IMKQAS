<template>
  <div class="profile-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">我的健康档案</h1>
        <p class="page-subtitle">
          {{ editMode ? '维护您的个人健康信息，用于更精准的问答与问卷评估' : '您的当前健康状况与医疗史概览' }}
        </p>
      </div>
      <div v-if="hasProfile" class="header-actions">
        <button v-if="!editMode" class="btn-edit" @click="enterEditMode">
          <span class="material-symbols-outlined">add</span>
          更新记录
        </button>
        <button v-else class="btn-outline" @click="cancelEditMode">
          <span class="material-symbols-outlined">arrow_back</span>
          返回视图
        </button>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="state-box">
      <span class="material-symbols-outlined state-spin">refresh</span>
      <p>加载中...</p>
    </div>

    <!-- 空态 -->
    <div v-else-if="!hasProfile" class="empty-box">
      <div class="empty-icon">
        <span class="material-symbols-outlined">medical_information</span>
      </div>
      <h3 class="empty-title">您还没有健康档案</h3>
      <p class="empty-desc">创建健康档案有助于系统为您提供更个性化、准确的医疗建议和评估。</p>
      <button class="btn-primary" @click="enterEdit">
        <span class="material-symbols-outlined">add</span>
        去创建
      </button>
    </div>

    <!-- 视图态：设计稿 Bento 布局（左 8 / 右 4） -->
    <div v-else-if="!editMode" class="view-grid">
      <!-- 左栏 -->
      <div class="view-left">
        <!-- 当前状况概览 -->
        <section class="overview-card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">favorite</span>
            当前状况
          </h2>
          <div class="metric-grid">
            <div class="metric">
              <p class="metric-label">慢性病</p>
              <div class="metric-value">{{ form.chronicDiseases.length }} <span class="metric-unit">项</span></div>
              <span class="metric-tag">{{ form.chronicDiseases.length ? '需关注' : '无' }}</span>
            </div>
            <div class="metric">
              <p class="metric-label">过敏</p>
              <div class="metric-value">{{ form.allergies.length }} <span class="metric-unit">项</span></div>
              <span class="metric-tag warn">{{ form.allergies.length ? '注意规避' : '无' }}</span>
            </div>
            <div class="metric">
              <p class="metric-label">在用药物</p>
              <div class="metric-value">{{ form.medicationHistory.length }} <span class="metric-unit">项</span></div>
              <span class="metric-tag">{{ form.medicationHistory.length ? '按医嘱' : '无' }}</span>
            </div>
          </div>
        </section>

        <!-- 慢性病史 / 过敏史 双卡 -->
        <div class="duo-grid">
          <section class="info-card">
            <div class="card-head">
              <h3 class="card-subtitle">
                <span class="material-symbols-outlined">coronavirus</span>
                慢性病史
              </h3>
            </div>
            <ul v-if="form.chronicDiseases.length" class="item-list">
              <li v-for="(d, i) in form.chronicDiseases" :key="i" class="item-row">
                <span class="item-dot chronic" />
                <span class="item-name">{{ d }}</span>
              </li>
            </ul>
            <p v-else class="empty-inline-text">暂无慢性病史</p>
          </section>

          <section class="info-card">
            <div class="card-head">
              <h3 class="card-subtitle">
                <span class="material-symbols-outlined">warning</span>
                过敏史
              </h3>
            </div>
            <div v-if="form.allergies.length" class="chip-list">
              <span v-for="(a, i) in form.allergies" :key="i" class="chip danger">
                {{ a }}
              </span>
            </div>
            <p v-else class="empty-inline-text">暂无过敏史</p>
          </section>

          <section class="info-card">
            <div class="card-head">
              <h3 class="card-subtitle">
                <span class="material-symbols-outlined">surgical</span>
                手术史
              </h3>
            </div>
            <ul v-if="form.surgicalHistory.length" class="item-list">
              <li v-for="(d, i) in form.surgicalHistory" :key="i" class="item-row">
                <span class="item-dot chronic" />
                <span class="item-name">{{ d }}</span>
              </li>
            </ul>
            <p v-else class="empty-inline-text">暂无手术史</p>
          </section>

          <section class="info-card">
            <div class="card-head">
              <h3 class="card-subtitle">
                <span class="material-symbols-outlined">family_restroom</span>
                家庭病史
              </h3>
            </div>
            <ul v-if="form.familyHistory.length" class="item-list">
              <li v-for="(d, i) in form.familyHistory" :key="i" class="item-row">
                <span class="item-dot chronic" />
                <span class="item-name">{{ d }}</span>
              </li>
            </ul>
            <p v-else class="empty-inline-text">暂无家庭病史</p>
          </section>
        </div>

        <!-- 健康提示 -->
        <section class="risk-card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">analytics</span>
            健康提示
          </h2>
          <div class="risk-row">
            <span class="risk-tag" :class="riskTone">{{ riskTagText }}</span>
            <p class="risk-text">{{ riskText }}</p>
          </div>
        </section>
      </div>

      <!-- 右栏 -->
      <div class="view-right">
        <!-- 在用药物 -->
        <section class="med-card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">prescriptions</span>
            在用药物
          </h2>
          <div v-if="form.medicationHistory.length" class="med-list">
            <div v-for="(m, i) in form.medicationHistory" :key="i" class="med-item">
              <span class="med-icon">
                <span class="material-symbols-outlined">medication</span>
              </span>
              <span class="med-name">{{ m }}</span>
            </div>
          </div>
          <p v-else class="med-empty">暂无在用药物</p>
          <button class="btn-link-more" @click="ElMessage.info('用药史功能即将开放')">
            查看完整用药史
            <span class="material-symbols-outlined">chevron_right</span>
          </button>
        </section>

        <!-- 健康小贴士（渐变卡） -->
        <section class="tip-card">
          <div class="tip-icon-wrap">
            <span class="material-symbols-outlined">self_improvement</span>
          </div>
          <h3 class="tip-title">健康小贴士</h3>
          <p class="tip-text">{{ tipText }}</p>
        </section>
      </div>
    </div>

    <!-- 编辑态：基础信息 + 医疗史表单 -->
    <div v-else class="form-card">
      <!-- 基础信息：只读回显，由个人中心统一维护 -->
      <section class="form-section">
        <h2 class="section-title">
          <span class="material-symbols-outlined">person</span>
          基础信息
        </h2>
        <div v-if="identityInfo && identityInfo.name" class="basic-grid">
          <div class="field">
            <label class="field-label">姓名</label>
            <div class="field-value">{{ identityInfo.name }}</div>
          </div>
          <div class="field">
            <label class="field-label">年龄（岁）</label>
            <div class="field-value">{{ computedAge != null ? computedAge : '—' }}</div>
          </div>
          <div class="field">
            <label class="field-label">性别</label>
            <div class="field-value">{{ computedGenderText || '—' }}</div>
          </div>
          <div class="field identity-hint">
            <span class="identity-hint-text">如需修改请前往个人中心</span>
          </div>
        </div>
        <div v-else class="identity-empty">
          <span class="material-symbols-outlined">person_off</span>
          <p>请先到个人中心完善身份信息</p>
          <button class="btn-link" @click="goUserCenter">
            <span class="material-symbols-outlined">open_in_new</span>
            去个人中心
          </button>
        </div>
      </section>

      <!-- 医疗史 -->
      <section class="form-section">
        <h2 class="section-title">
          <span class="material-symbols-outlined">history_edu</span>
          医疗史
        </h2>
        <div class="history-grid">
          <div
            v-for="field in historyFields"
            :key="field.key"
            class="history-card"
            :class="field.key === 'familyHistory' ? 'history-card-wide' : ''"
          >
            <div class="history-head">
              <label class="field-label">{{ field.label }}</label>
            </div>
            <div class="tag-list">
              <el-tag
                v-for="(item, idx) in form[field.key]"
                :key="idx"
                closable
                class="history-tag"
                @close="removeItem(field.key, idx)"
              >
                {{ item }}
              </el-tag>
              <span v-if="!form[field.key].length" class="tag-empty">无记录</span>
            </div>
            <div class="tag-input">
              <el-input
                v-model="draftInputs[field.key]"
                :placeholder="field.placeholder"
                size="small"
                class="tag-input-field"
                @keyup.enter="addItem(field.key)"
              />
              <el-button
                size="small"
                type="primary"
                plain
                class="tag-add-btn"
                @click="addItem(field.key)"
              >
                <span class="material-symbols-outlined">add</span>
              </el-button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 底部操作条（编辑态） -->
    <div v-if="hasProfile && editMode && !loading" class="action-bar">
      <div class="action-inner">
        <button class="btn-danger-text" @click="handleDelete">
          <span class="material-symbols-outlined">delete</span>
          删除档案
        </button>
        <button class="btn-primary" :disabled="saving" @click="handleSave">
          <span class="material-symbols-outlined">save</span>
          {{ saving ? '保存中...' : '保存档案' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { userService, type HealthProfile, type IdentityInfo } from '@/api/services/user.service'
import { apiErrorMessage } from '@/utils/error'
import { genderText, calcAge } from '@/api/types/fhir'

const authStore = useAuthStore()
const router = useRouter()
const userId = computed(() => authStore.userId)

// 页面状态
const loading = ref(true)
const saving = ref(false)
const hasProfile = ref(false)
/** 视图态（设计稿 Bento 概览） / 编辑态（医疗史表单）切换 */
const editMode = ref(false)

// 个人身份信息（人口学字段只读回显，由个人中心统一维护）
const identityInfo = ref<IdentityInfo | null>(null)
const computedAge = computed(() => calcAge(identityInfo.value?.birthDate))
const computedGenderText = computed(() => genderText(identityInfo.value?.gender))

// 表单数据：仅病史（人口学字段由 identity 回显，不在本页编辑）
const form = reactive<HealthProfile>({
  allergies: [],
  chronicDiseases: [],
  medicationHistory: [],
  surgicalHistory: [],
  familyHistory: []
})

/**
 * 跳转个人中心完善身份信息
 */
const goUserCenter = () => {
  router.push('/user')
}

// 医疗史字段的输入草稿
const draftInputs = reactive<Record<string, string>>({
  allergies: '',
  chronicDiseases: '',
  medicationHistory: '',
  surgicalHistory: '',
  familyHistory: ''
})

// 医疗史字段配置
const historyFields = [
  { key: 'allergies' as const, label: '过敏史', placeholder: '如：青霉素' },
  { key: 'chronicDiseases' as const, label: '慢性病史', placeholder: '如：高血压' },
  { key: 'medicationHistory' as const, label: '用药史', placeholder: '如：阿司匹林' },
  { key: 'surgicalHistory' as const, label: '手术史', placeholder: '如：阑尾切除术' },
  { key: 'familyHistory' as const, label: '家族病史', placeholder: '如：高血压（父系）' }
]

// ==================== 视图态（设计稿 Bento）辅助 ====================

/** 健康提示等级（有过敏→风险红 / 有慢性病→提醒橙 / 均无→正常绿） */
const riskTone = computed(() => {
  if (form.allergies.length) return 'risk-danger'
  if (form.chronicDiseases.length) return 'risk-warn'
  return 'risk-ok'
})

/** 健康提示标签文案 */
const riskTagText = computed(() => {
  if (form.allergies.length) return '过敏风险'
  if (form.chronicDiseases.length) return '慢性病关注'
  return '状态良好'
})

/** 健康提示正文 */
const riskText = computed(() => {
  if (form.allergies.length) {
    return `您存在 ${form.allergies.length} 项过敏史，就诊或用药时请主动告知医务人员，以规避过敏原。`
  }
  if (form.chronicDiseases.length) {
    return `您有 ${form.chronicDiseases.length} 项慢性病史，建议遵医嘱规律复查，并持续关注相关指标变化。`
  }
  return '当前档案未记录过敏与慢性病史，请继续保持良好生活习惯，并定期进行健康体检。'
})

/** 健康小贴士正文（结合在用药物动态生成） */
const tipText = computed(() => {
  if (form.medicationHistory.length) {
    return `当前有 ${form.medicationHistory.length} 项在用药物，请严格遵医嘱服用，避免自行停药或调整剂量。`
  }
  return '保持规律作息与适量运动，均衡饮食，有助于维持良好的健康状态。'
})

// 进入编辑态（视图 → 表单）
const enterEditMode = () => {
  editMode.value = true
}

// 返回视图态（取消编辑，回滚未保存修改）
const cancelEditMode = () => {
  editMode.value = false
  loadProfile()
}

// 加载健康档案（并行加载身份信息用于人口学字段回显）
const loadProfile = async () => {
  loading.value = true
  try {
    const [identityResult, profileResult] = await Promise.all([
      userService.getIdentity(userId.value).catch(() => null),
      userService.getHealthProfile(userId.value)
    ])
    if (identityResult?.hasIdentity && identityResult.identity) {
      identityInfo.value = identityResult.identity
    }
    if (profileResult.hasHealthProfile && profileResult.healthProfile) {
      // 只回填病史字段（人口学字段由 identity 提供，不覆盖）
      const hp = profileResult.healthProfile
      Object.assign(form, {
        allergies: hp.allergies ?? [],
        chronicDiseases: hp.chronicDiseases ?? [],
        medicationHistory: hp.medicationHistory ?? [],
        surgicalHistory: hp.surgicalHistory ?? [],
        familyHistory: hp.familyHistory ?? []
      })
      hasProfile.value = true
    } else {
      hasProfile.value = false
    }
  } catch (e: any) {
    ElMessage.error('加载健康档案失败: ' + apiErrorMessage(e, '未知错误'))
    hasProfile.value = false
  } finally {
    loading.value = false
  }
}

// 从空态进入编辑态（创建档案）
const enterEdit = () => {
  hasProfile.value = true
  editMode.value = true
}

// 添加一项病史
const addItem = (key: string) => {
  const value = (draftInputs[key] || '').trim()
  if (!value) return
  ;(form[key as keyof HealthProfile] as string[]).push(value)
  draftInputs[key] = ''
}

// 删除一项病史
const removeItem = (key: string, index: number) => {
  (form[key as keyof HealthProfile] as string[]).splice(index, 1)
}

// 保存档案（人口学字段由个人中心兜底，仅提交病史；保存后返回视图态）
const handleSave = async () => {
  saving.value = true
  try {
    await userService.updateHealthProfile(userId.value, { ...form })
    ElMessage.success('健康档案保存成功')
    editMode.value = false
  } catch (e: any) {
    ElMessage.error('保存失败: ' + apiErrorMessage(e, '未知错误'))
  } finally {
    saving.value = false
  }
}

// 删除档案
const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要删除您的健康档案吗？删除后不可恢复。',
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
    await userService.deleteHealthProfile(userId.value)
    ElMessage.success('健康档案已删除')
    hasProfile.value = false
    editMode.value = false
    // 重置表单（仅病史，人口学字段由 identity 回显保持不变）
    Object.assign(form, {
      allergies: [],
      chronicDiseases: [],
      medicationHistory: [],
      surgicalHistory: [],
      familyHistory: []
    })
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('删除失败: ' + apiErrorMessage(e, '未知错误'))
  }
}

onMounted(loadProfile)
</script>

<style scoped>
/* ===== 页面容器 ===== */
.profile-page {
  max-width: 80rem;
  margin: 0 auto;
  padding-bottom: 5rem;
}

/* ===== 页头 ===== */
.page-header {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0 0 1.5rem;
  border-bottom: 1px solid var(--theme-warm-sand);
  margin-bottom: 1.5rem;
}

@media (min-width: 768px) {
  .page-header {
    flex-direction: row;
    align-items: flex-end;
    justify-content: space-between;
  }
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

.header-actions {
  display: flex;
  gap: 0.75rem;
  flex-shrink: 0;
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

.btn-primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* ===== 视图态按钮 ===== */
.btn-edit {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 1.25rem;
  background: var(--theme-healing-sage);
  color: var(--theme-on-primary);
  border: 1px solid var(--theme-healing-sage);
  border-radius: 9999px;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
  box-shadow: 0 2px 8px rgba(134, 166, 151, 0.3);
}

.btn-edit:hover {
  background: var(--theme-primary);
  border-color: var(--theme-primary);
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 1.25rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-warm-sand);
  color: var(--theme-primary);
  border-radius: 9999px;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}

.btn-outline:hover {
  background: var(--theme-surface-container-low);
}

/* ===== 视图态栅格（左 8 / 右 4） ===== */
.view-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem 1.75rem;
}

@media (min-width: 768px) {
  .view-grid {
    grid-template-columns: repeat(12, minmax(0, 1fr));
  }
}

.view-left {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
}

@media (min-width: 768px) {
  .view-left {
    grid-column: span 8;
  }
}

.view-right {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
}

@media (min-width: 768px) {
  .view-right {
    grid-column: span 4;
  }
}

/* ===== 通用卡片（视图态） ===== */
.overview-card,
.info-card,
.risk-card,
.med-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid rgba(233, 227, 216, 0.6);
  border-radius: 0.75rem;
  box-shadow: 0 10px 30px rgba(139, 166, 193, 0.05);
  padding: 1.5rem;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0 0 1.25rem;
  padding-bottom: 0.625rem;
  border-bottom: 1px solid rgba(233, 227, 216, 0.6);
}

.card-title .material-symbols-outlined {
  color: var(--theme-healing-sage);
}

.card-subtitle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0;
}

.card-subtitle .material-symbols-outlined {
  font-size: 1.25rem;
  color: var(--theme-therapeutic-blue);
}

/* ===== 当前状况概览 ===== */
.metric-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.875rem;
}

@media (min-width: 640px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.metric {
  padding: 1rem;
  background: var(--theme-surface-container-low);
  border: 1px solid rgba(233, 227, 216, 0.5);
  border-radius: 0.5rem;
}

.metric-label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--theme-soft-stone);
  margin: 0 0 0.375rem;
}

.metric-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--theme-primary);
  line-height: 1.2;
}

.metric-unit {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--theme-soft-stone);
}

.metric-tag {
  display: inline-flex;
  align-items: center;
  margin-top: 0.5rem;
  padding: 0.125rem 0.625rem;
  background: var(--theme-primary-soft);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--theme-primary);
}

.metric-tag.warn {
  background: rgba(186, 26, 26, 0.1);
  border-color: rgba(186, 26, 26, 0.3);
  color: var(--theme-error);
}

/* ===== 慢性病 / 过敏 双卡 ===== */
.duo-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
}

@media (min-width: 768px) {
  .duo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 0.625rem;
  border-bottom: 1px solid rgba(233, 227, 216, 0.6);
  margin-bottom: 1rem;
}

.item-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.item-row {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
}

.item-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 9999px;
  flex-shrink: 0;
  margin-top: 0.375rem;
}

.item-dot.chronic {
  background: var(--theme-therapeutic-blue);
}

.item-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--theme-on-surface);
}

/* 过敏 chips */
.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.chip {
  padding: 0.375rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.8125rem;
  font-weight: 500;
}

.chip.danger {
  background: var(--theme-error-container);
  border: 1px solid rgba(193, 139, 139, 0.35);
  color: var(--theme-on-error-container);
}

.empty-inline-text {
  font-size: 0.8125rem;
  color: var(--theme-outline);
  margin: 0;
}

/* ===== 健康提示 ===== */
.risk-row {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.risk-tag {
  flex-shrink: 0;
  padding: 0.125rem 0.625rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  margin-top: 0.125rem;
}

.risk-tag.risk-danger {
  background: rgba(186, 26, 26, 0.1);
  border: 1px solid rgba(186, 26, 26, 0.3);
  color: var(--theme-error);
}

.risk-tag.risk-warn {
  background: rgba(237, 108, 2, 0.1);
  border: 1px solid rgba(237, 108, 2, 0.3);
  color: #9a3412;
}

.risk-tag.risk-ok {
  background: var(--theme-primary-soft);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-primary);
}

.risk-text {
  font-size: 0.875rem;
  line-height: 1.7;
  color: var(--theme-on-surface-variant);
  margin: 0;
}

/* ===== 在用药物 ===== */
.med-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.med-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 1px solid var(--theme-warm-sand);
  border-radius: 0.5rem;
  transition: border-color 150ms;
}

.med-item:hover {
  border-color: rgba(134, 166, 151, 0.5);
}

.med-icon {
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  background: var(--theme-primary-fixed);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.med-icon .material-symbols-outlined {
  font-size: 1.125rem;
  color: var(--theme-primary);
}

.med-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--theme-on-surface);
}

.med-empty {
  font-size: 0.8125rem;
  color: var(--theme-outline);
  margin: 0 0 1rem;
}

.btn-link-more {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  margin-top: 1rem;
  padding: 0.5rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-warm-sand);
  border-radius: 9999px;
  color: var(--theme-primary);
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 150ms;
}

.btn-link-more:hover {
  background: var(--theme-surface-container-low);
}

/* ===== 健康小贴士（渐变卡） ===== */
.tip-card {
  background: linear-gradient(135deg, var(--theme-primary-fixed), var(--theme-surface-bright));
  border: 1px solid rgba(233, 227, 216, 0.35);
  border-radius: 0.75rem;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.tip-icon-wrap {
  width: 4rem;
  height: 4rem;
  border-radius: 9999px;
  background: var(--theme-surface-container-lowest);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1rem;
}

.tip-icon-wrap .material-symbols-outlined {
  font-size: 1.75rem;
  color: var(--theme-primary);
}

.tip-title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0 0 0.375rem;
}

.tip-text {
  font-size: 0.8125rem;
  line-height: 1.7;
  color: var(--theme-on-surface-variant);
  margin: 0;
}

/* ===== 编辑态表单 ===== */
.form-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.form-section {
  padding: 1.5rem;
  border-bottom: 1px solid var(--theme-outline-variant);
}

.form-section:last-child {
  border-bottom: none;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin-bottom: 1rem;
}

.section-title .material-symbols-outlined {
  color: var(--theme-primary);
}

/* ===== 基础信息 ===== */
.basic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1.5rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.field-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
}

.field-value {
  font-size: 0.9375rem;
  color: var(--theme-on-surface);
  padding-top: 0.375rem;
  padding-bottom: 0.375rem;
}

.identity-hint {
  justify-content: flex-end;
}

.identity-hint-text {
  font-size: 0.75rem;
  color: var(--theme-outline);
  align-self: flex-end;
  padding-bottom: 0.5rem;
}

.identity-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1.5rem;
  text-align: center;
  color: var(--theme-outline);
  font-size: 0.875rem;
}

.identity-empty .material-symbols-outlined {
  font-size: 2rem;
  color: var(--theme-outline);
}

.identity-empty p {
  margin: 0;
}

.btn-link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.375rem 1rem;
  color: var(--theme-primary);
  background: none;
  border: 1px solid rgba(70, 101, 88, 0.3);
  border-radius: 9999px;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.btn-link:hover {
  background: var(--theme-primary-soft);
}

/* ===== 医疗史 ===== */
.history-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1rem;
}

.history-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.5rem;
  padding: 0.75rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.history-card-wide {
  grid-column: 1 / -1;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  min-height: 1.75rem;
}

.history-tag {
  border-radius: 9999px;
}

.tag-empty {
  font-size: 0.8125rem;
  font-style: italic;
  color: var(--theme-outline);
  align-self: center;
}

.tag-input {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.tag-input-field {
  flex: 1;
}

.tag-add-btn {
  flex-shrink: 0;
}

/* ===== 底部操作条 ===== */
.action-bar {
  position: fixed;
  bottom: 0;
  left: var(--layout-sidebar-width, 0px);
  right: 0;
  z-index: 15;
  background: var(--theme-surface-container-lowest);
  border-top: 1px solid var(--theme-outline-variant);
  padding: 0.75rem 1rem;
  box-shadow: 0 -4px 6px -1px rgba(0, 0, 0, 0.05);
}

.action-inner {
  max-width: 80rem;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.btn-danger-text {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 1rem;
  color: var(--theme-error);
  background: none;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.btn-danger-text:hover {
  background: var(--theme-error-container);
  border-color: rgba(186, 26, 26, 0.3);
}

/* 响应式：小屏操作条占满宽度 */
@media (max-width: 1024px) {
  .action-bar {
    left: 0;
  }
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
