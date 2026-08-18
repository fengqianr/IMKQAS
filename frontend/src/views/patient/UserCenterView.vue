<template>
  <div class="user-center-page">
    <!-- 页面标题区 -->
    <div class="page-head">
      <h2 class="page-title">个人中心</h2>
      <p class="page-sub">管理您的个人信息、身份资料与紧急联系人</p>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="state-box">
      <span class="material-symbols-outlined state-spin">refresh</span>
      <p>加载中...</p>
    </div>

    <div v-else class="uc-grid">
      <!-- 左栏：个人资料卡 + 下次预约 -->
      <div class="uc-left">
        <!-- 个人资料卡 -->
        <section class="profile-card">
          <div class="avatar-wrap">
            <div class="profile-avatar">{{ displayName.charAt(0) || '用' }}</div>
            <div class="avatar-dot">
              <span class="material-symbols-outlined">check</span>
            </div>
          </div>
          <h3 class="profile-name">{{ displayName }}</h3>
          <p class="profile-id">患者编号: {{ fhirId }}</p>
          <div class="profile-tags">
            <span class="tag-active">
              <span class="material-symbols-outlined">verified</span>
              在册患者
            </span>
            <span class="tag-id">
              <span class="material-symbols-outlined">shield</span>
              {{ genderText }}<template v-if="ageText !== null"> · {{ ageText }}岁</template>
            </span>
          </div>
          <button class="btn-photo" @click="enterEdit">
            <span class="material-symbols-outlined">edit</span>
            编辑资料
          </button>
        </section>

        <!-- 下次预约（后端暂无预约数据 → 空态） -->
        <section class="appt-card">
          <h4 class="appt-title">
            <span class="material-symbols-outlined">event</span>
            下次预约
          </h4>
          <div class="appt-empty">
            <span class="material-symbols-outlined appt-empty-icon">event_available</span>
            <div>
              <p class="appt-empty-title">暂无预约安排</p>
              <p class="appt-empty-desc">预约功能即将开放，敬请期待</p>
            </div>
          </div>
        </section>
      </div>

      <!-- 右栏：身份信息 + 紧急联系人 -->
      <div class="uc-right">
        <!-- 身份与联系信息 -->
        <section class="form-card">
          <div class="form-head">
            <h3 class="form-title">身份与联系信息</h3>
            <p class="form-sub">保存后，医生端可通过姓名、证件号或手机号检索到您的身份信息</p>
          </div>

          <!-- 展示态 -->
          <template v-if="!editing">
            <div class="info-block">
              <h4 class="info-group">基本资料</h4>
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
            </div>

            <div class="info-block">
              <h4 class="info-group">联系方式</h4>
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">手机号码</span>
                  <span class="info-value">{{ form.phone || '—' }}</span>
                </div>
              </div>
            </div>

            <div class="info-block">
              <h4 class="info-group">地址信息</h4>
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">家庭住址</span>
                  <span class="info-value">{{ form.address || '—' }}</span>
                </div>
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
            <h4 class="info-group">基本资料</h4>
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

            <h4 class="info-group">联系方式</h4>
            <div class="form-grid">
              <el-form-item label="手机号码">
                <el-input :model-value="form.phone" disabled />
              </el-form-item>
            </div>

            <h4 class="info-group">地址信息</h4>
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

        <!-- 紧急联系人（后端暂无接口 → 空态） -->
        <section class="contact-card">
          <div class="contact-head">
            <div>
              <h3 class="contact-title">紧急联系人</h3>
              <p class="contact-sub">可在紧急情况下联系到的亲友</p>
            </div>
            <button class="contact-add" @click="ElMessage.info('紧急联系人功能即将开放')">
              <span class="material-symbols-outlined">add</span>
            </button>
          </div>
          <div class="contact-empty">
            <span class="material-symbols-outlined contact-empty-icon">contact_phone</span>
            <div>
              <p class="contact-empty-title">暂未添加紧急联系人</p>
              <p class="contact-empty-desc">添加后可设置关系与联系方式</p>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'
import { userService } from '@/api/services/user.service'

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

/**
 * 展示姓名（注册/登录回显的真实姓名优先）：
 * 身份信息 form.name > 登录/刷新后缓存的 authStore.user.name > 用户名
 */
const displayName = computed(
  () => form.name || authStore.user?.name || authStore.user?.username || ''
)

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

onMounted(loadIdentity)
</script>

<style scoped>
/* ===== 页面容器 ===== */
.user-center-page {
  max-width: 80rem;
  margin: 0 auto;
  padding-bottom: 3rem;
}

/* ===== 页面标题区 ===== */
.page-head {
  margin-bottom: 1.75rem;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--theme-on-surface);
  letter-spacing: -0.02em;
  margin: 0 0 0.25rem;
}

.page-sub {
  font-size: 0.9375rem;
  color: var(--theme-soft-stone);
  margin: 0;
}

/* ===== 加载态 ===== */
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

/* ===== 主体栅格（左 4 / 右 8） ===== */
.uc-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem 2rem;
}

@media (min-width: 1024px) {
  .uc-grid {
    grid-template-columns: repeat(12, minmax(0, 1fr));
  }
}

.uc-left {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

@media (min-width: 1024px) {
  .uc-left {
    grid-column: span 4;
  }
}

.uc-right {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
}

@media (min-width: 1024px) {
  .uc-right {
    grid-column: span 8;
  }
}

/* ===== 通用卡片 ===== */
.profile-card,
.appt-card,
.form-card,
.contact-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid rgba(193, 200, 195, 0.5);
  border-radius: 0.75rem;
  box-shadow: 0 10px 30px rgba(70, 101, 88, 0.06);
}

/* ===== 个人资料卡 ===== */
.profile-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 1.5rem;
}

.avatar-wrap {
  position: relative;
  margin-bottom: 1rem;
}

.profile-avatar {
  width: 6rem;
  height: 6rem;
  border-radius: 9999px;
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
  border: 4px solid var(--theme-surface-container-lowest);
  box-shadow: 0 0 0 1px var(--theme-outline-variant);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  font-weight: 700;
}

.avatar-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 1.5rem;
  height: 1.5rem;
  background: var(--theme-healing-sage);
  border-radius: 9999px;
  border: 2px solid var(--theme-surface-container-lowest);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-dot .material-symbols-outlined {
  font-size: 0.875rem;
  color: var(--theme-on-primary);
}

.profile-name {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0 0 0.25rem;
}

.profile-id {
  font-size: 0.8125rem;
  color: var(--theme-soft-stone);
  margin: 0 0 1rem;
  font-family: 'JetBrains Mono', monospace;
}

.profile-tags {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 1.5rem;
}

.tag-active,
.tag-id {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.tag-active {
  background: var(--theme-primary-soft);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-primary);
}

.tag-id {
  background: var(--theme-secondary-container);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-on-secondary-container);
}

.tag-active .material-symbols-outlined,
.tag-id .material-symbols-outlined {
  font-size: 0.9375rem;
}

.btn-photo {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  padding: 0.625rem 1rem;
  background: var(--theme-primary);
  color: var(--theme-on-primary);
  border: 1px solid var(--theme-primary);
  border-radius: 0.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}

.btn-photo:hover {
  background: var(--theme-primary-strong);
  border-color: var(--theme-primary-strong);
}

/* ===== 下次预约卡（暖沙上边线） ===== */
.appt-card {
  border-top: 2px solid var(--theme-warm-sand);
  padding: 1.25rem 1.5rem;
}

.appt-title {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--theme-on-surface-variant);
  margin: 0 0 1rem;
}

.appt-title .material-symbols-outlined {
  font-size: 1.125rem;
}

.appt-empty {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: var(--theme-surface-container-low);
  border: 1px dashed var(--theme-outline-variant);
  border-radius: 0.5rem;
}

.appt-empty-icon {
  font-size: 1.5rem;
  color: var(--theme-healing-sage);
  flex-shrink: 0;
}

.appt-empty-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0;
}

.appt-empty-desc {
  font-size: 0.75rem;
  color: var(--theme-outline);
  margin: 0.125rem 0 0;
}

/* ===== 身份信息卡（暖沙上边线） ===== */
.form-card {
  overflow: hidden;
  border-top: 2px solid var(--theme-warm-sand);
}

.form-head {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--theme-warm-sand);
  background: var(--theme-surface-bright);
}

.form-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0 0 0.25rem;
}

.form-sub {
  font-size: 0.8125rem;
  color: var(--theme-soft-stone);
  margin: 0;
}

/* 展示态信息分组 */
.info-block {
  padding: 0 1.5rem;
}

.info-block:first-child {
  padding-top: 1.25rem;
}

.info-group {
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--theme-on-surface-variant);
  padding: 1.25rem 0 0.5rem;
  border-bottom: 1px solid rgba(193, 200, 195, 0.4);
  margin: 0 0 0.875rem;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 0.875rem 1.5rem;
  padding-bottom: 1.25rem;
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
  font-size: 1rem;
  font-weight: 500;
  color: var(--theme-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.id-card-value {
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.05em;
}

/* ===== 编辑态 ===== */
.edit-form {
  padding: 0 1.5rem 1.5rem;
}

.edit-form .info-group {
  padding-top: 1.25rem;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0 1.5rem;
}

@media (min-width: 768px) {
  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--theme-outline-variant);
  margin-top: 0.75rem;
}

.full-width {
  width: 100%;
}

/* ===== 紧急联系人卡（暖沙上边线） ===== */
.contact-card {
  overflow: hidden;
  border-top: 2px solid var(--theme-warm-sand);
}

.contact-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--theme-warm-sand);
  background: var(--theme-surface-bright);
}

.contact-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0 0 0.25rem;
}

.contact-sub {
  font-size: 0.8125rem;
  color: var(--theme-soft-stone);
  margin: 0;
}

.contact-add {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 9999px;
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 150ms;
}

.contact-add:hover {
  background: rgba(70, 101, 88, 0.2);
}

.contact-empty {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1.5rem;
}

.contact-empty-icon {
  font-size: 1.5rem;
  color: var(--theme-healing-sage);
  flex-shrink: 0;
}

.contact-empty-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0;
}

.contact-empty-desc {
  font-size: 0.75rem;
  color: var(--theme-outline);
  margin: 0.125rem 0 0;
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
