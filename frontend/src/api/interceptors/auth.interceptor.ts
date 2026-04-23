import axios from 'axios'
import { AUTH_CONFIG } from '../config'
import { authService } from '../services/auth.service'

// 配置axios默认值
axios.defaults.timeout = 30000
axios.defaults.headers.common['Content-Type'] = 'application/json'

// 请求拦截器 - 添加认证令牌
axios.interceptors.request.use(
  (config) => {
    const token = authService.getToken()
    if (token) {
      config.headers.Authorization = `${AUTH_CONFIG.TOKEN_PREFIX}${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 处理令牌刷新
let isRefreshing = false
let failedQueue: Array<{
  resolve: (value?: any) => void
  reject: (reason?: any) => void
}> = []

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

axios.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // 处理401错误 - 令牌过期
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // 如果已经在刷新，将请求加入队列
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then((token) => {
          originalRequest.headers.Authorization = `${AUTH_CONFIG.TOKEN_PREFIX}${token}`
          return axios(originalRequest)
        }).catch((err) => {
          return Promise.reject(err)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = authService.getRefreshToken()
      if (!refreshToken) {
        authService.clearTokens()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      try {
        // 尝试刷新令牌
        const newToken = await authService.refreshToken(refreshToken)
        if (newToken) {
          // 更新原请求的Authorization头
          originalRequest.headers.Authorization = `${AUTH_CONFIG.TOKEN_PREFIX}${newToken}`

          // 处理等待队列中的请求
          processQueue(null, newToken)

          // 重试原请求
          return axios(originalRequest)
        } else {
          // 刷新失败，清除令牌并重定向到登录页
          authService.clearTokens()
          window.location.href = '/login'
          return Promise.reject(error)
        }
      } catch (refreshError) {
        // 刷新失败，清除令牌并重定向到登录页
        authService.clearTokens()
        window.location.href = '/login'
        processQueue(refreshError, null)
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    // 处理其他错误
    if (error.response?.status === 403) {
      console.error('权限不足:', error)
      // TODO: 可以显示权限不足提示
    }

    return Promise.reject(error)
  }
)

// 全局错误处理
window.addEventListener('unhandledrejection', (event) => {
  if (event.reason?.response?.status === 401) {
    console.warn('未处理的401错误，可能是令牌失效')
  }
})