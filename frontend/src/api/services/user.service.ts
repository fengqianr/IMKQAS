import request from '../request'

/** 健康档案 */
export interface HealthProfile {
  /** 年龄（0-150） */
  age: number
  /** 性别: MALE | FEMALE | OTHER */
  gender: string
  /** 过敏史 */
  allergies: string[]
  /** 慢性病史 */
  chronicDiseases: string[]
  /** 用药史 */
  medicationHistory: string[]
  /** 手术史 */
  surgicalHistory: string[]
  /** 家族病史 */
  familyHistory: string[]
}

/** 健康档案查询结果（GET 返回体，无 success 包装） */
export interface HealthProfileResult {
  hasHealthProfile: boolean
  healthProfile?: HealthProfile
  message?: string
}

/** 写操作结果（PUT/DELETE 返回体） */
export interface HealthProfileActionResult {
  success: boolean
  message: string
}

class UserService {
  /**
   * 获取用户健康档案
   * GET /api/users/{userId}/health-profile
   */
  async getHealthProfile(userId: number): Promise<HealthProfileResult> {
    const response = await request.get<HealthProfileResult>(
      `/users/${userId}/health-profile`
    )
    return response.data
  }

  /**
   * 更新用户健康档案
   * PUT /api/users/{userId}/health-profile
   */
  async updateHealthProfile(userId: number, profile: HealthProfile): Promise<void> {
    const response = await request.put<HealthProfileActionResult>(
      `/users/${userId}/health-profile`,
      profile
    )
    if (!response.data.success) {
      throw new Error(response.data.message || '保存失败')
    }
  }

  /**
   * 删除用户健康档案
   * DELETE /api/users/{userId}/health-profile
   */
  async deleteHealthProfile(userId: number): Promise<void> {
    const response = await request.delete<HealthProfileActionResult>(
      `/users/${userId}/health-profile`
    )
    if (!response.data.success) {
      throw new Error(response.data.message || '删除失败')
    }
  }
}

export const userService = new UserService()
