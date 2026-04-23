import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authService } from '@/api/services/auth.service'
import type { LoginRequest, UserInfo } from '@/api/types/auth.types'

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const token = ref<string | null>(authService.getToken())
  const refreshToken = ref<string | null>(authService.getRefreshToken())
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)

  // 计算属性
  const isAuthenticated = computed(() => !!token.value)
  const userRole = computed(() => user.value?.role || '')
  const userId = computed(() => user.value?.id || 0)

  // Actions
  const setToken = (newToken: string) => {
    token.value = newToken
    authService.setToken(newToken)
  }

  const setRefreshToken = (newRefreshToken: string) => {
    refreshToken.value = newRefreshToken
    authService.setRefreshToken(newRefreshToken)
  }

  const setUser = (userInfo: UserInfo) => {
    user.value = userInfo
  }

  const clearAuth = () => {
    token.value = null
    refreshToken.value = null
    user.value = null
    authService.clearTokens()
  }

  // 发送验证码
  const sendCode = async (phone: string): Promise<boolean> => {
    try {
      return await authService.sendLoginCode(phone)
    } catch (error) {
      console.error('发送验证码失败:', error)
      return false
    }
  }

  // 登录
  const login = async (request: LoginRequest) => {
    loading.value = true
    try {
      const response = await authService.loginWithCode(request)

      if (response.token) {
        setToken(response.token)
        setRefreshToken(response.refreshToken)

        // 设置用户信息
        setUser({
          id: response.userId,
          username: response.username,
          phone: request.username, // 假设用户名是手机号
          role: response.role,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        })

        return { success: true, data: response }
      } else {
        return { success: false, message: response.message || '登录失败' }
      }
    } catch (error: any) {
      console.error('登录失败:', error)
      return { success: false, message: error.message || '登录失败' }
    } finally {
      loading.value = false
    }
  }

  // 登出
  const logout = async () => {
    loading.value = true
    try {
      const currentToken = token.value
      if (currentToken) {
        await authService.logout(currentToken)
      }
      clearAuth()
      return { success: true }
    } catch (error: any) {
      console.error('登出失败:', error)
      // 即使API失败也清除本地状态
      clearAuth()
      return { success: false, message: error.message || '登出失败' }
    } finally {
      loading.value = false
    }
  }

  // 检查认证状态
  const checkAuth = async (): Promise<boolean> => {
    const currentToken = token.value
    if (!currentToken) {
      return false
    }

    try {
      const isValid = await authService.validateToken(currentToken)
      if (!isValid) {
        clearAuth()
        return false
      }

      // 获取用户信息
      const userInfo = await authService.getCurrentUser(currentToken)
      if (userInfo) {
        setUser(userInfo)
      }

      return true
    } catch (error) {
      console.error('检查认证状态失败:', error)
      clearAuth()
      return false
    }
  }

  // 刷新令牌
  const refreshAuthToken = async (): Promise<boolean> => {
    const currentRefreshToken = refreshToken.value
    if (!currentRefreshToken) {
      return false
    }

    try {
      const newToken = await authService.refreshToken(currentRefreshToken)
      if (newToken) {
        setToken(newToken)
        return true
      }
      return false
    } catch (error) {
      console.error('刷新令牌失败:', error)
      return false
    }
  }

  // 初始化时检查认证状态
  const initialize = async () => {
    if (token.value) {
      await checkAuth()
    }
  }

  return {
    // 状态
    token,
    refreshToken,
    user,
    loading,

    // 计算属性
    isAuthenticated,
    userRole,
    userId,

    // Actions
    setToken,
    setRefreshToken,
    setUser,
    clearAuth,
    sendCode,
    login,
    logout,
    checkAuth,
    refreshAuthToken,
    initialize
  }
})