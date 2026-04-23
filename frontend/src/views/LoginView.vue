<template>
  <div class="login-container">
    <div class="login-card glass">
      <div class="login-header">
        <h1 class="display-lg">IMKQAS</h1>
        <p class="subtitle">医疗知识智能问答系统</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名/手机号"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>


        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loginLoading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>

        <div class="login-footer">
          <el-link type="primary" @click="gotoRegister">注册账号</el-link>
          <el-link type="info" @click="gotoForgotPassword">忘记密码？</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'

const router = useRouter()
const authStore = useAuthStore()
const loginFormRef = ref<FormInstance>()

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名或手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const loginLoading = ref(false)

const handleLogin = async () => {
  if (!loginFormRef.value) return

  const isValid = await loginFormRef.value.validate()
  if (!isValid) return

  loginLoading.value = true

  try {
    const result = await authStore.login({
      username: loginForm.username,
      password: loginForm.password
    })

    if (result.success) {
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } else {
      ElMessage.error(result.message || '登录失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败，请检查用户名和密码')
  } finally {
    loginLoading.value = false
  }
}


const gotoRegister = () => {
  router.push('/register')
}

const gotoForgotPassword = () => {
  router.push('/forgot-password')
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-surface), var(--color-surface-container-low));
  padding: var(--spacing-xl);
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: var(--spacing-xxl);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-ambient);
}

.login-header {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.login-header h1 {
  color: var(--color-primary);
  margin-bottom: var(--spacing-sm);
}

.subtitle {
  color: var(--color-on-surface-variant);
  font-size: 1.1rem;
}

.login-form {
  margin-top: var(--spacing-xl);
}


.login-btn {
  width: 100%;
  margin-top: var(--spacing-lg);
}

.login-footer {
  display: flex;
  justify-content: space-between;
  margin-top: var(--spacing-lg);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-outline-variant);
}
</style>