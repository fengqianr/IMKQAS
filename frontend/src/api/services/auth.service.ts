import axios from 'axios'
import { API_CONFIG, AUTH_CONFIG } from '../config'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  ApiResponse
} from '../types/auth.types'

class AuthService {
  private baseURL = API_CONFIG.BASE_URL

  // 发送登录验证码
  async sendLoginCode(phone: string): Promise<boolean> {
    try {
      const response = await axios.post<ApiResponse>(
        `${this.baseURL}/auth/send-code`,
        null,
        { params: { phone } }
      )
      return response.data.success
    } catch (error) {
      console.error('发送验证码失败:', error)
      return false
    }
  }

  // 验证码登录
  async loginWithCode(request: LoginRequest): Promise<LoginResponse> {
    try {
      const response = await axios.post<ApiResponse<LoginResponse>>(
        `${this.baseURL}/auth/login`,
        request
      )

      if (response.data.success && response.data.data) {
        const { token, refreshToken } = response.data.data
        this.setToken(token)
        this.setRefreshToken(refreshToken)
        return response.data.data
      } else {
        throw new Error(response.data.message || '登录失败')
      }
    } catch (error: any) {
      console.error('登录失败:', error)
      return {
        token: '',
        refreshToken: '',
        userId: 0,
        username: '',
        role: '',
        expiresIn: 0,
        message: error.message || '登录失败'
      }
    }
  }

  // 刷新令牌
  async refreshToken(oldToken: string): Promise<string | null> {
    try {
      const response = await axios.post<ApiResponse<string>>(
        `${this.baseURL}/auth/refresh`,
        null,
        {
          headers: {
            Authorization: `${AUTH_CONFIG.TOKEN_PREFIX}${oldToken}`
          }
        }
      )

      if (response.data.success && response.data.data) {
        this.setToken(response.data.data)
        return response.data.data
      }
      return null
    } catch (error) {
      console.error('刷新令牌失败:', error)
      return null
    }
  }

  // 用户登出
  async logout(token: string): Promise<boolean> {
    try {
      const response = await axios.post<ApiResponse>(
        `${this.baseURL}/auth/logout`,
        null,
        {
          headers: {
            Authorization: `${AUTH_CONFIG.TOKEN_PREFIX}${token}`
          }
        }
      )

      if (response.data.success) {
        this.clearTokens()
      }
      return response.data.success
    } catch (error) {
      console.error('登出失败:', error)
      this.clearTokens() // 即使API失败也清除本地令牌
      return false
    }
  }

  // 验证令牌
  async validateToken(token: string): Promise<boolean> {
    try {
      const response = await axios.get<ApiResponse<boolean>>(
        `${this.baseURL}/auth/validate`,
        {
          headers: {
            Authorization: `${AUTH_CONFIG.TOKEN_PREFIX}${token}`
          }
        }
      )
      return response.data.success && response.data.data === true
    } catch (error) {
      console.error('验证令牌失败:', error)
      return false
    }
  }

  // 获取当前用户信息
  async getCurrentUser(token: string): Promise<any> {
    try {
      const response = await axios.get<ApiResponse>(
        `${this.baseURL}/auth/me`,
        {
          headers: {
            Authorization: `${AUTH_CONFIG.TOKEN_PREFIX}${token}`
          }
        }
      )
      return response.data.data
    } catch (error) {
      console.error('获取用户信息失败:', error)
      return null
    }
  }

  // 用户注册
  async register(request: RegisterRequest): Promise<boolean> {
    try {
      const response = await axios.post<ApiResponse>(
        `${this.baseURL}/auth/register`,
        request
      )
      return response.data.success
    } catch (error) {
      console.error('注册失败:', error)
      return false
    }
  }

  // 本地存储操作
  setToken(token: string): void {
    localStorage.setItem(AUTH_CONFIG.TOKEN_KEY, token)
  }

  getToken(): string | null {
    return localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
  }

  setRefreshToken(refreshToken: string): void {
    localStorage.setItem(AUTH_CONFIG.REFRESH_TOKEN_KEY, refreshToken)
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(AUTH_CONFIG.REFRESH_TOKEN_KEY)
  }

  clearTokens(): void {
    localStorage.removeItem(AUTH_CONFIG.TOKEN_KEY)
    localStorage.removeItem(AUTH_CONFIG.REFRESH_TOKEN_KEY)
  }

  // 检查是否已认证
  isAuthenticated(): boolean {
    return !!this.getToken()
  }
}

export const authService = new AuthService()