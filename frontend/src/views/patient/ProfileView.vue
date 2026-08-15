<template>
  <div class="profile-page">
    <!-- 页头 -->
    <div class="page-header">
      <h1 class="page-title">我的健康档案</h1>
      <p class="page-subtitle">维护您的个人健康信息，用于更精准的问答与问卷评估</p>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="state-box">
      <span class="material-symbols-outlined text-3xl text-secondary animate-spin">refresh</span>
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

    <!-- 表单态 -->
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

    <!-- 底部操作条 -->
    <div v-if="hasProfile && !loading" class="action-bar">
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

const authStore = useAuthStore()
const router = useRouter()
const userId = computed(() => authStore.userId)

// 页面状态
const loading = ref(true)
const saving = ref(false)
const hasProfile = ref(false)

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
 * 由出生日期计算年龄
 * @param birthDate 出生日期 yyyy-MM-dd
 * @returns 年龄；无出生日期或解析失败返回 null
 */
const calcAge = (birthDate?: string): number | null => {
  if (!birthDate) return null
  const birth = new Date(birthDate)
  if (isNaN(birth.getTime())) return null
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const monthDiff = today.getMonth() - birth.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age--
  }
  return age
}

/**
 * 性别枚举转中文显示
 * @param gender MALE/FEMALE/OTHER
 * @returns 中文性别；未知返回空串
 */
const genderText = (gender?: string): string => {
  if (gender === 'MALE') return '男'
  if (gender === 'FEMALE') return '女'
  if (gender === 'OTHER') return '其他'
  return ''
}

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
    ElMessage.error('加载健康档案失败: ' + (e.message || '未知错误'))
    hasProfile.value = false
  } finally {
    loading.value = false
  }
}

// 从空态进入编辑态
const enterEdit = () => {
  hasProfile.value = true
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

// 保存档案（人口学字段由个人中心兜底，仅提交病史）
const handleSave = async () => {
  saving.value = true
  try {
    await userService.updateHealthProfile(userId.value, { ...form })
    ElMessage.success('健康档案保存成功')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
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
    ElMessage.error('删除失败: ' + (e.message || '未知错误'))
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

/* ===== 加载/空态容器 ===== */
.state-box {
  min-height: 24rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  color: #727783;
  font-size: 0.875rem;
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
}

.empty-box {
  min-height: 24rem;
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

/* ===== 主按钮 ===== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.5rem;
  background: #005eb8;
  color: #ffffff;
  font-size: 0.875rem;
  font-weight: 600;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 94, 184, 0.25);
  transition: all 150ms;
}

.btn-primary:hover {
  background: #00478d;
  box-shadow: 0 6px 16px rgba(0, 71, 141, 0.35);
}

.btn-primary:active {
  transform: scale(0.97);
}

.btn-primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* ===== 表单卡片 ===== */
.form-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.75rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.form-section {
  padding: 1.5rem;
  border-bottom: 1px solid #e2e8f0;
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
  color: #191c1d;
  margin-bottom: 1rem;
}

.section-title .material-symbols-outlined {
  color: #005eb8;
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
  color: #4a5f83;
}

/* ===== 身份信息只读回显 ===== */
.field-value {
  font-size: 0.9375rem;
  color: #191c1d;
  padding-top: 0.375rem;
  padding-bottom: 0.375rem;
}

.identity-hint {
  justify-content: flex-end;
}

.identity-hint-text {
  font-size: 0.75rem;
  color: #94a3b8;
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
  color: #727783;
  font-size: 0.875rem;
}

.identity-empty .material-symbols-outlined {
  font-size: 2rem;
  color: #94a3b8;
}

.identity-empty p {
  margin: 0;
}

.btn-link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.375rem 1rem;
  color: #005eb8;
  background: none;
  border: 1px solid rgba(0, 94, 184, 0.3);
  border-radius: 9999px;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.btn-link:hover {
  background: #eef4fb;
}

.age-input {
  width: 100%;
}

/* ===== 医疗史 ===== */
.history-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1rem;
}

.history-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
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
  color: #94a3b8;
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
  background: #ffffff;
  border-top: 1px solid #c2c6d4;
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
  color: #ba1a1a;
  background: none;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.btn-danger-text:hover {
  background: #ffdad6;
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
