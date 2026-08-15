import request from '../request'

/**
 * 健康档案
 * 人口学字段（姓名/年龄/性别）可选：由个人中心（identity）统一维护，
 * 健康档案页仅编辑病史；保存缺省人口学字段时后端以 identity 兜底填充。
 */
export interface HealthProfile {
  /** 姓名（可选，缺省以个人中心身份信息兜底） */
  name?: string
  /** 年龄 0-150（可选，缺省以个人中心出生日期计算兜底） */
  age?: number
  /** 性别: MALE | FEMALE | OTHER（可选，缺省以个人中心身份信息兜底） */
  gender?: string
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

/** 个人身份信息 */
export interface IdentityInfo {
  /** 姓名 */
  name: string
  /** 性别: MALE | FEMALE | OTHER */
  gender: string
  /** 出生日期 yyyy-MM-dd */
  birthDate?: string
  /** 证件号（身份证） */
  idCard?: string
  /** 家庭住址 */
  address?: string
  /** 联系电话（只读，取自 users.phone） */
  phone?: string
}

/** 身份信息查询结果（GET 返回体，无 success 包装） */
export interface IdentityResult {
  hasIdentity: boolean
  identity?: IdentityInfo
  message?: string
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

  /**
   * 获取用户个人身份信息
   * GET /api/users/{userId}/identity
   */
  async getIdentity(userId: number): Promise<IdentityResult> {
    const response = await request.get<IdentityResult>(
      `/users/${userId}/identity`
    )
    return response.data
  }

  /**
   * 保存用户个人身份信息（同步 fhir_patient_cache，医生端立即可检索）
   * PUT /api/users/{userId}/identity
   */
  async updateIdentity(userId: number, identity: IdentityInfo): Promise<void> {
    const response = await request.put<HealthProfileActionResult>(
      `/users/${userId}/identity`,
      identity
    )
    if (!response.data.success) {
      throw new Error(response.data.message || '保存失败')
    }
  }
}

export const userService = new UserService()
