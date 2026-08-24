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
  userId: string | number
  username: string
  name: string
  phone: string
  role: string
  expiresAt?: string
  message?: string
}

// 注册请求
export interface RegisterRequest {
  username: string
  /** 真实姓名（必填，注册即同步至医生端患者档案） */
  name: string
  password: string
  phone: string
  confirmPassword: string
  /** 角色（仅患者/学生/护士/健康管理师可自助注册；医生/管理员由管理员创建） */
  role?: string
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
  id: string | number
  username: string
  name: string
  phone: string
  email?: string
  role: string
  avatar?: string
  createdAt: string
  updatedAt: string
}
