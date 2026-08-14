import request from '../request'

/** 用户列表项（对齐后端 User 实体序列化字段，忽略 password/健康档案） */
export interface UserItem {
  /** 用户 ID（后端 Long 序列化为字符串） */
  id: string
  username: string
  phone?: string
  /** 后端 Role 枚举名：PATIENT/STUDENT/NURSE/DOCTOR/HEALTH_MANAGER/ADMIN */
  role: string
  createdAt?: string
  updatedAt?: string
}

/** 分页响应（对齐 ApiResponse.Pagination） */
export interface UserPageResult {
  data: UserItem[]
  total: number
  page: number
  size: number
  totalPages: number
}

/** 创建/更新用户请求体（编辑时 password 留空表示不修改） */
export interface UserUpsertRequest {
  username: string
  phone?: string
  role: string
  password?: string
}

/**
 * 用户管理接口
 * 复用现有 UserController（GET /users、GET /users/search、POST /users、PUT /users/{id}、DELETE /users/{id}），不新增后端接口
 */
class AdminUserService {
  /** 分页获取用户列表 */
  async listUsers(current = 1, size = 100): Promise<UserPageResult> {
    const response = await request.get<{ success: boolean; message: string; data: UserPageResult }>(
      '/users',
      { params: { current, size } }
    )
    if (response.data.success && response.data.data) {
      return response.data.data
    }
    throw new Error(response.data.message || '获取用户列表失败')
  }

  /** 关键词搜索用户（用户名/手机号模糊匹配） */
  async searchUsers(keyword: string, current = 1, size = 100): Promise<UserPageResult> {
    const response = await request.get<{ success: boolean; message: string; data: UserPageResult }>(
      '/users/search',
      { params: { keyword, current, size } }
    )
    if (response.data.success && response.data.data) {
      return response.data.data
    }
    throw new Error(response.data.message || '搜索用户失败')
  }

  /** 创建用户（返回 User 实体，成功即 2xx） */
  async createUser(data: UserUpsertRequest): Promise<void> {
    await request.post('/users', data)
  }

  /** 更新用户（密码留空不修改） */
  async updateUser(id: string, data: UserUpsertRequest): Promise<void> {
    await request.put(`/users/${id}`, data)
  }

  /** 删除用户（逻辑删除） */
  async deleteUser(id: string): Promise<void> {
    await request.delete(`/users/${id}`)
  }
}

export const adminUserService = new AdminUserService()
