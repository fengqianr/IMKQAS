<template>
  <div class="user-center-page">
    <!-- 返回首页 -->
    <button class="back-link" @click="goHome">
      <span class="material-symbols-outlined">arrow_back</span>
      返回首页
    </button>

    <!-- 加载态 -->
    <div v-if="loading" class="state-box">
      <span class="material-symbols-outlined text-3xl text-secondary animate-spin">refresh</span>
      <p>加载中...</p>
    </div>

    <template v-else>
      <!-- Hero 区 -->
      <section class="hero-card">
        <div class="hero-glow" />
        <div class="hero-inner">
          <div class="hero-avatar">{{ displayName.charAt(0) || '用' }}</div>
          <div>
            <div class="hero-head">
              <h1 class="hero-name">{{ displayName }}</h1>
              <span v-if="displayName" class="hero-badge">{{ genderText }}<template v-if="ageText !== null"> | {{ ageText }}岁</template></span>
            </div>
            <div class="hero-meta">
              <span class="material-symbols-outlined hero-meta-icon">badge</span>
              <span>患者档案: {{ fhirId }}</span>
            </div>
          </div>
        </div>
        <div class="hero-actions">
          <button v-if="!editing" class="btn-primary" @click="enterEdit">
            <span class="material-symbols-outlined">edit</span>
            编辑资料
          </button>
          <template v-else>
            <button class="btn-outline" :disabled="saving" @click="cancelEdit">
              <span class="material-symbols-outlined">close</span>
              取消
            </button>
            <button class="btn-primary" :disabled="saving" @click="handleSave">
              <span class="material-symbols-outlined">{{ saving ? 'sync' : 'save' }}</span>
              {{ saving ? '保存中...' : '保存身份信息' }}
            </button>
          </template>
        </div>
      </section>

      <!-- 同步提示 -->
      <el-alert
        v-if="!editing"
        type="info"
        :closable="false"
        class="sync-alert"
        show-icon
      >
        保存后，医生端可通过姓名、证件号或手机号检索到您的身份信息
      </el-alert>

      <!-- 身份与联系信息 -->
      <section class="info-card">
        <div class="card-head">
          <span class="material-symbols-outlined card-head-icon">account_box</span>
          <h2 class="card-title">身份与联系信息</h2>
        </div>

        <!-- 展示态 -->
        <template v-if="!editing">
          <h3 class="group-title">基本资料</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">姓名</span>
              <span class="info-value">{{ displayName || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">性别</span>
              <span class="info-value">{{ genderText || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">出生日期</span>
              <span class="info-value">{{ form.birthDate || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">证件号码</span>
              <span class="info-value id-card-value">{{ maskIdNumber(form.idCard) }}</span>
            </div>
          </div>

          <h3 class="group-title">联系方式</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">手机号码</span>
              <span class="info-value">{{ form.phone || '—' }}</span>
            </div>
          </div>

          <h3 class="group-title">地址信息</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">家庭住址</span>
              <span class="info-value">{{ form.address || '—' }}</span>
            </div>
          </div>
        </template>

        <!-- 编辑态 -->
        <el-form
          v-else
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="edit-form"
        >
          <h3 class="group-title">基本资料</h3>
          <div class="form-grid">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名" maxlength="50" />
            </el-form-item>
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="form.gender">
                <el-radio value="MALE">男</el-radio>
                <el-radio value="FEMALE">女</el-radio>
                <el-radio value="OTHER">其他</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="出生日期" prop="birthDate">
              <el-date-picker
                v-model="form.birthDate"
                type="date"
                placeholder="选择出生日期"
                value-format="YYYY-MM-DD"
                class="full-width"
              />
            </el-form-item>
            <el-form-item label="证件号码" prop="idCard">
              <el-input v-model="form.idCard" placeholder="请输入身份证号" maxlength="18" />
            </el-form-item>
          </div>

          <h3 class="group-title">联系方式</h3>
          <div class="form-grid">
            <el-form-item label="手机号码">
              <el-input :model-value="form.phone" disabled />
            </el-form-item>
          </div>

          <h3 class="group-title">地址信息</h3>
          <div class="form-grid">
            <el-form-item label="家庭住址" prop="address">
              <el-input v-model="form.address" placeholder="请输入家庭住址" maxlength="200" />
            </el-form-item>
          </div>

          <div class="form-actions">
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" :loading="saving" @click="handleSave">
              保存身份信息
            </el-button>
          </div>
        </el-form>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'
import { userService } from '@/api/services/user.service'

const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.userId)
/** FHIR 患者缓存 ID 约定：pat-{userId}（与后端一致） */
const fhirId = computed(() => (userId.value ? `pat-${userId.value}` : ''))

// 页面状态
const loading = ref(true)
const saving = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const form = reactive({
  name: '',
  gender: 'MALE',
  birthDate: '',
  idCard: '',
  address: '',
  phone: ''
})

// 校验规则
const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }]
}

/** 展示姓名（身份信息未填时回退用户名） */
const displayName = computed(() => form.name || authStore.user?.username || '')

/** 性别中文 */
const genderText = computed(() => {
  switch (form.gender) {
    case 'MALE': return '男'
    case 'FEMALE': return '女'
    case 'OTHER': return '其他'
    default: return ''
  }
})

/** 由出生日期计算年龄（无出生日期时返回 null） */
const ageText = computed(() => {
  if (!form.birthDate) return null
  const birth = new Date(form.birthDate)
  if (Number.isNaN(birth.getTime())) return null
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const m = today.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--
  return age >= 0 ? age : null
})

/** 证件号脱敏：保留前 6 后 4 */
const maskIdNumber = (id?: string) => {
  if (!id) return ''
  if (id.length < 11) return id
  return id.slice(0, 6) + '*'.repeat(Math.max(id.length - 10, 4)) + id.slice(-4)
}

// 加载身份信息
const loadIdentity = async () => {
  loading.value = true
  try {
    const res = await userService.getIdentity(userId.value)
    if (res.hasIdentity && res.identity) {
      Object.assign(form, {
        name: res.identity.name || '',
        gender: res.identity.gender || 'MALE',
        birthDate: res.identity.birthDate || '',
        idCard: res.identity.idCard || '',
        address: res.identity.address || '',
        phone: res.identity.phone || ''
      })
    } else {
      // 未设置身份信息：phone 仅从 authStore 兜底（刷新后可能丢失）
      form.phone = authStore.user?.phone || ''
    }
  } catch (e: any) {
    ElMessage.error('加载身份信息失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 进入编辑态
const enterEdit = () => {
  editing.value = true
}

// 取消编辑：回滚为已保存的数据
const cancelEdit = () => {
  editing.value = false
  loadIdentity()
}

// 保存身份信息
const handleSave = async () => {
  if (!formRef.value) return
  const isValid = await formRef.value.validate().catch(() => false)
  if (!isValid) return
  saving.value = true
  try {
    await userService.updateIdentity(userId.value, {
      name: form.name,
      gender: form.gender,
      birthDate: form.birthDate || undefined,
      idCard: form.idCard || undefined,
      address: form.address || undefined
    })
    ElMessage.success('身份信息保存成功，已同步至医生端')
    editing.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

// 返回首页
const goHome = () => {
  router.push('/qa')
}

onMounted(loadIdentity)
</script>

<style scoped>
/* ===== 页面容器 ===== */
.user-center-page {
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
  color: #00647c;
}

.back-link .material-symbols-outlined {
  font-size: 1rem;
}

/* ===== 加载态 ===== */
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

/* ===== Hero 区 ===== */
.hero-card {
  position: relative;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 1.5rem;
  margin-bottom: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

@media (min-width: 768px) {
  .hero-card {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

.hero-glow {
  position: absolute;
  top: -5rem;
  right: -5rem;
  width: 16rem;
  height: 16rem;
  border-radius: 9999px;
  background: rgba(0, 100, 124, 0.06);
  pointer-events: none;
}

.hero-inner {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  z-index: 1;
}

.hero-avatar {
  width: 6rem;
  height: 6rem;
  border-radius: 9999px;
  background: #d0e1fb;
  color: #00566a;
  border: 3px solid #ffffff;
  box-shadow: 0 0 0 1px #c2c6d4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  font-weight: 700;
  flex-shrink: 0;
}

.hero-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.25rem;
  flex-wrap: wrap;
}

.hero-name {
  font-size: 2rem;
  font-weight: 700;
  color: #191c1e;
  letter-spacing: -0.02em;
}

.hero-badge {
  padding: 0.125rem 0.5rem;
  background: #eceef0;
  border: 1px solid #bdc8ce;
  border-radius: 0.25rem;
  color: #191c1e;
  font-size: 0.75rem;
  font-weight: 600;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  color: #3e484d;
  font-size: 0.875rem;
}

.hero-meta-icon {
  font-size: 1.125rem;
  color: #00647c;
}

.hero-actions {
  display: flex;
  gap: 0.5rem;
  z-index: 1;
}

/* ===== 同步提示 ===== */
.sync-alert {
  margin-bottom: 1.25rem;
  border-radius: 0.5rem;
}

/* ===== 信息卡片 ===== */
.info-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e0e3e5;
  background: #f7f9fb;
}

.card-head-icon {
  font-size: 1.25rem;
  color: #00647c;
}

.card-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #191c1e;
}

.group-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #3e484d;
  padding: 1.25rem 1.5rem 0.75rem;
  border-bottom: 1px solid rgba(189, 200, 206, 0.5);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem 1.5rem;
  padding: 1.25rem 1.5rem 1.5rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label {
  font-size: 0.75rem;
  color: #3e484d;
}

.info-value {
  font-size: 1rem;
  font-weight: 500;
  color: #191c1e;
}

.id-card-value {
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.05em;
}

/* ===== 编辑态 ===== */
.edit-form {
  padding: 0 1.5rem 1.5rem;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 0 1.5rem;
  padding: 1.25rem 0 0.5rem;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e0e3e5;
  margin-top: 1rem;
}

.full-width {
  width: 100%;
}

/* ===== 按钮 ===== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.625rem 1.25rem;
  background: #00647c;
  color: #ffffff;
  font-size: 0.8125rem;
  font-weight: 600;
  border: none;
  border-radius: 0.5rem;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 100, 124, 0.25);
  transition: all 150ms;
}

.btn-primary:hover {
  background: #00566a;
}

.btn-primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.625rem 1.25rem;
  background: #ffffff;
  border: 1px solid #bdc8ce;
  color: #3e484d;
  font-size: 0.8125rem;
  font-weight: 600;
  border-radius: 0.5rem;
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
