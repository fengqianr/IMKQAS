<template>
  <div class="forgot-password-container">
    <div class="forgot-password-card glass">
      <div class="forgot-password-header">
        <h1 class="display-lg">重置密码</h1>
        <p class="subtitle">请输入您的手机号以重置密码</p>
      </div>

      <el-steps :active="activeStep" simple class="steps" finish-status="success">
        <el-step title="验证身份" />
        <el-step title="重置密码" />
        <el-step title="完成" />
      </el-steps>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="forgot-password-form"
        v-if="activeStep === 0"
        @submit.prevent="handleNextStep"
      >
        <el-form-item prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="手机号"
            size="large"
            :prefix-icon="Iphone"
          />
        </el-form-item>

        <el-form-item prop="code">
          <div class="code-input-wrapper">
            <el-input
              v-model="form.code"
              placeholder="验证码"
              size="large"
              :prefix-icon="Message"
              class="code-input"
            />
            <el-button
              type="text"
              class="send-code-btn"
              :disabled="sendCodeDisabled"
              @click="sendCode"
            >
              {{ sendCodeText }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="next-btn"
            :loading="loading"
            native-type="submit"
          >
            下一步
          </el-button>
        </el-form-item>
      </el-form>

      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        class="forgot-password-form"
        v-else-if="activeStep === 1"
        @submit.prevent="handleResetPassword"
      >
        <el-form-item prop="password">
          <el-input
            v-model="passwordForm.password"
            type="password"
            placeholder="新密码（至少8位）"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="确认新密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <div class="form-actions">
            <el-button size="large" @click="activeStep = 0">
              上一步
            </el-button>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              native-type="submit"
            >
              重置密码
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <div v-else class="success-message">
        <el-icon size="64" color="var(--color-primary)">
          <CircleCheck />
        </el-icon>
        <h2>密码重置成功</h2>
        <p>您的密码已成功重置，请使用新密码登录</p>
        <el-button
          type="primary"
          size="large"
          class="login-btn"
          @click="gotoLogin"
        >
          立即登录
        </el-button>
      </div>

      <div class="forgot-password-footer">
        <el-link type="primary" @click="gotoLogin">
          <el-icon><ArrowLeft /></el-icon>
          返回登录
        </el-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Iphone, Message, Lock, CircleCheck, ArrowLeft } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'

const router = useRouter()

// 步骤控制
const activeStep = ref(0)

// 第一步表单
const formRef = ref<FormInstance>()
const form = reactive({
  phone: '',
  code: ''
})

const validatePhone = (rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请输入手机号'))
  }
  if (!/^1[3-9]\d{9}$/.test(value)) {
    return callback(new Error('请输入正确的手机号'))
  }
  callback()
}

const rules: FormRules = {
  phone: [
    { validator: validatePhone, trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

// 第二步表单
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  password: '',
  confirmPassword: ''
})

const validatePassword = (rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请输入密码'))
  }
  if (value.length < 8) {
    return callback(new Error('密码长度至少8位'))
  }
  if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(value)) {
    return callback(new Error('密码需包含大小写字母和数字'))
  }
  callback()
}

const validateConfirmPassword = (rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请确认密码'))
  }
  if (value !== passwordForm.password) {
    return callback(new Error('两次输入的密码不一致'))
  }
  callback()
}

const passwordRules: FormRules = {
  password: [
    { validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 验证码相关
const sendCodeDisabled = ref(false)
const sendCodeText = ref('发送验证码')
const loading = ref(false)

// 发送验证码
const sendCode = async () => {
  if (!form.phone) {
    ElMessage.warning('请先输入手机号')
    return
  }

  // 验证手机号格式
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }

  sendCodeDisabled.value = true
  let count = 60
  sendCodeText.value = `${count}秒后重新发送`

  const timer = setInterval(() => {
    count--
    sendCodeText.value = `${count}秒后重新发送`

    if (count <= 0) {
      clearInterval(timer)
      sendCodeDisabled.value = false
      sendCodeText.value = '发送验证码'
    }
  }, 1000)

  try {
    // TODO: 调用发送验证码API
    await new Promise(resolve => setTimeout(resolve, 500))
    ElMessage.success('验证码已发送')
  } catch (error) {
    ElMessage.error('验证码发送失败')
    clearInterval(timer)
    sendCodeDisabled.value = false
    sendCodeText.value = '发送验证码'
  }
}

// 下一步
const handleNextStep = async () => {
  if (!formRef.value) return

  const isValid = await formRef.value.validate()
  if (!isValid) return

  loading.value = true
  try {
    // TODO: 验证验证码
    await new Promise(resolve => setTimeout(resolve, 500))
    activeStep.value = 1
  } catch (error) {
    ElMessage.error('验证失败，请检查验证码')
  } finally {
    loading.value = false
  }
}

// 重置密码
const handleResetPassword = async () => {
  if (!passwordFormRef.value) return

  const isValid = await passwordFormRef.value.validate()
  if (!isValid) return

  loading.value = true
  try {
    // TODO: 调用重置密码API
    await new Promise(resolve => setTimeout(resolve, 1000))
    activeStep.value = 2
  } catch (error) {
    ElMessage.error('重置密码失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 跳转到登录页
const gotoLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.forgot-password-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-surface), var(--color-surface-container-low));
  padding: var(--spacing-xl);
}

.forgot-password-card {
  width: 100%;
  max-width: 480px;
  padding: var(--spacing-xxl);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-ambient);
}

.forgot-password-header {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.forgot-password-header h1 {
  color: var(--color-primary);
  margin-bottom: var(--spacing-sm);
}

.subtitle {
  color: var(--color-on-surface-variant);
  font-size: 1.1rem;
}

.steps {
  margin: var(--spacing-xl) 0;
}

.forgot-password-form {
  margin-top: var(--spacing-xl);
}

.code-input-wrapper {
  display: flex;
  gap: var(--spacing-sm);
}

.code-input {
  flex: 1;
}

.send-code-btn {
  min-width: 120px;
}

.next-btn {
  width: 100%;
  margin-top: var(--spacing-lg);
}

.form-actions {
  display: flex;
  justify-content: space-between;
  width: 100%;
  margin-top: var(--spacing-lg);
}

.success-message {
  text-align: center;
  padding: var(--spacing-xl) 0;
}

.success-message h2 {
  margin: var(--spacing-lg) 0 var(--spacing-sm);
  color: var(--color-on-surface);
}

.success-message p {
  color: var(--color-on-surface-variant);
  margin-bottom: var(--spacing-xl);
}

.login-btn {
  margin-top: var(--spacing-lg);
}

.forgot-password-footer {
  text-align: center;
  margin-top: var(--spacing-lg);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-outline-variant);
}
</style>