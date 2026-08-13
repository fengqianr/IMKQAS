// 登录请求
export interface LoginRequest {
  username: string
  password: string
  code?: string
}

// 登录响应
export interface LoginResponse {
  token: string
  refreshToken: string
  userId: number
  username: string
  role: string
  expiresIn: number
  message?: string
}

// 注册请求
export interface RegisterRequest {
  username: string
  password: string
  phone: string
  confirmPassword: string
}

// API响应格式
export interface ApiResponse<T = any> {
  success: boolean
  message: string
  data?: T
  code?: number
  timestamp: number
}

// 用户信息
export interface UserInfo {
  id: number
  username: string
  phone: string
  email?: string
  role: string
  avatar?: string
  createdAt: string
  updatedAt: string
}