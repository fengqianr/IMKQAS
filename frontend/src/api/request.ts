import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { apiErrorMessage } from '@/utils/error'
import { AUTH_CONFIG, API_CONFIG } from './config'

/**
 * 扩展 Axios 请求配置，支持请求级错误弹窗控制：
 * - silent：为 true 时抑制全局错误弹窗，由业务代码自行处理（如局部错误态、静默降级）
 * - errorMessage：覆盖拦截器默认的错误提示文案
 */
declare module 'axios' {
  interface AxiosRequestConfig {
    silent?: boolean
    errorMessage?: string
  }
}

/**
 * 全局网络请求实例
 *
 * 职责：
 * - 统一 baseURL 与超时时间
 * - 请求拦截器：自动注入 Bearer 令牌
 * - 响应拦截器：401 自动刷新令牌 + 并发请求排队重试
 *
 * 注意：认证类接口（login / register / send-code / refresh）不应使用本实例，
 * 否则登录失败返回的 401 会被误判为"令牌过期"而触发刷新逻辑。认证接口请使用裸 axios。
 */
const request: AxiosInstance = axios.create({
  baseURL: API_CONFIG.BASE_URL,
  timeout: API_CONFIG.TIMEOUT
})

/** 可携带重试标记的请求配置 */
interface RetryableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

// ===== 请求拦截器：注入认证令牌 =====
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `${AUTH_CONFIG.TOKEN_PREFIX}${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ===== 响应拦截器：401 刷新令牌 =====
let isRefreshing = false
let failedQueue: Array<{
  resolve: (value?: unknown) => void
  reject: (reason?: unknown) => void
}> = []

/** 处理排队中的请求：刷新成功则携带新令牌重试，失败则全部拒绝 */
const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

/** 清除本地令牌并跳转登录页 */
const clearAuthAndRedirect = () => {
  localStorage.removeItem(AUTH_CONFIG.TOKEN_KEY)
  localStorage.removeItem(AUTH_CONFIG.REFRESH_TOKEN_KEY)
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

/** 令牌失效统一处理：提示用户登录状态失效，然后清除令牌并跳转登录页 */
const handleAuthExpired = () => {
  ElMessage.error('登录状态已失效，请重新登录')
  clearAuthAndRedirect()
}

request.interceptors.response.use(
  (response: AxiosResponse) => {
    // 拦截 HTTP 200 下的业务失败（后端直接返回 success=false），统一在此弹窗
    const data = response.data as { success?: boolean; message?: string } | null | undefined
    if (data && typeof data === 'object' && data.success === false) {
      if (!response.config?.silent) {
        ElMessage.error(response.config?.errorMessage || apiErrorMessage(data) || '操作失败')
      }
      return Promise.reject(new Error(data.message || '操作失败'))
    }
    return response
  },
  async (error) => {
    const originalRequest = error.config as RetryableConfig | undefined

    // 401 且未重试过 → 尝试刷新令牌
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      if (isRefreshing) {
        // 已有刷新在途，当前请求排队等待新令牌
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then((token) => {
          originalRequest.headers.Authorization = `${AUTH_CONFIG.TOKEN_PREFIX}${token}`
          return request(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = localStorage.getItem(AUTH_CONFIG.REFRESH_TOKEN_KEY)
      if (!refreshToken) {
        handleAuthExpired()
        return Promise.reject(error)
      }

      try {
        // 使用纯净 axios 刷新（不经过本实例拦截器，避免递归调用）
        const response = await axios.post<{ success: boolean; data?: string; message?: string }>(
          `${API_CONFIG.BASE_URL}/auth/refresh`,
          null,
          {
            headers: { Authorization: `${AUTH_CONFIG.TOKEN_PREFIX}${refreshToken}` }
          }
        )

        const newToken = response.data?.success ? response.data.data : null
        if (newToken) {
          localStorage.setItem(AUTH_CONFIG.TOKEN_KEY, newToken)
          originalRequest.headers.Authorization = `${AUTH_CONFIG.TOKEN_PREFIX}${newToken}`
          // 唤醒排队中的请求
          processQueue(null, newToken)
          // 重试原请求
          return request(originalRequest)
        }
        handleAuthExpired()
        return Promise.reject(error)
      } catch (refreshError) {
        handleAuthExpired()
        processQueue(refreshError, null)
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    // 403 权限不足
    if (error.response?.status === 403) {
      console.error('权限不足:', error)
    }

    // 请求被取消不弹窗
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }

    // 非 silent 请求统一弹窗，避免各业务代码重复编写
    if (!error.config?.silent) {
      ElMessage.error(error.config?.errorMessage || apiErrorMessage(error))
    }

    return Promise.reject(error)
  }
)

export default request
