<template>
  <div class="register-container">
    <div class="register-card glass">
      <div class="register-header">
        <h1 class="display-lg">创建账户</h1>
        <p class="subtitle">加入IMKQAS医疗知识问答系统</p>
      </div>

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        class="register-form"
        @submit.prevent="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="phone">
          <el-input
            v-model="registerForm.phone"
            placeholder="手机号"
            size="large"
            :prefix-icon="Iphone"
          />
        </el-form-item>


        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="密码（至少8位）"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item prop="realName">
          <el-input
            v-model="registerForm.realName"
            placeholder="真实姓名（选填）"
            size="large"
            :prefix-icon="UserFilled"
          />
        </el-form-item>

        <el-form-item prop="agree">
          <el-checkbox v-model="registerForm.agree">
            我已阅读并同意
            <el-link type="primary" :underline="false">《服务协议》</el-link>
            和
            <el-link type="primary" :underline="false">《隐私政策》</el-link>
          </el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="register-btn"
            :loading="registerLoading"
            native-type="submit"
          >
            注册
          </el-button>
        </el-form-item>

        <div class="register-footer">
          <span class="login-link">
            已有账号？
            <el-link type="primary" @click="gotoLogin">立即登录</el-link>
          </span>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Iphone, Lock, UserFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'

const router = useRouter()
const registerFormRef = ref<FormInstance>()

// 表单数据
const registerForm = reactive({
  username: '',
  phone: '',
  password: '',
  confirmPassword: '',
  realName: '',
  agree: false
})

// 验证规则
const validateUsername = (_rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请输入用户名'))
  }
  if (value.length < 3 || value.length > 20) {
    return callback(new Error('用户名长度应为3-20个字符'))
  }
  if (!/^[a-zA-Z0-9_]+$/.test(value)) {
    return callback(new Error('用户名只能包含字母、数字和下划线'))
  }
  callback()
}

const validatePhone = (_rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请输入手机号'))
  }
  if (!/^1[3-9]\d{9}$/.test(value)) {
    return callback(new Error('请输入正确的手机号'))
  }
  callback()
}

const validatePassword = (_rule: any, value: string, callback: any) => {
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

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请确认密码'))
  }
  if (value !== registerForm.password) {
    return callback(new Error('两次输入的密码不一致'))
  }
  callback()
}

const validateAgree = (_rule: any, value: boolean, callback: any) => {
  if (!value) {
    return callback(new Error('请同意服务协议和隐私政策'))
  }
  callback()
}

const registerRules: FormRules = {
  username: [
    { validator: validateUsername, trigger: 'blur' }
  ],
  phone: [
    { validator: validatePhone, trigger: 'blur' }
  ],
  password: [
    { validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  agree: [
    { validator: validateAgree, trigger: 'change' }
  ]
}

// 注册状态
const registerLoading = ref(false)


// 处理注册
const handleRegister = async () => {
  if (!registerFormRef.value) return

  const isValid = await registerFormRef.value.validate()
  if (!isValid) return

  registerLoading.value = true

  try {
    // TODO: 调用注册API
    // 模拟注册成功
    await new Promise(resolve => setTimeout(resolve, 1000))

    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error: any) {
    ElMessage.error(error.message || '注册失败，请稍后重试')
  } finally {
    registerLoading.value = false
  }
}

// 跳转到登录页
const gotoLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-surface), var(--color-surface-container-low));
  padding: var(--spacing-xl);
}

.register-card {
  width: 100%;
  max-width: 480px;
  padding: var(--spacing-xxl);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-ambient);
}

.register-header {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.register-header h1 {
  color: var(--color-primary);
  margin-bottom: var(--spacing-sm);
}

.subtitle {
  color: var(--color-on-surface-variant);
  font-size: 1.1rem;
}

.register-form {
  margin-top: var(--spacing-xl);
}


.register-btn {
  width: 100%;
  margin-top: var(--spacing-lg);
}

.register-footer {
  text-align: center;
  margin-top: var(--spacing-lg);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-outline-variant);
}

.login-link {
  color: var(--color-on-surface-variant);
}
</style>