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
  name?: string
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
  async getHealthProfile(userId: string | number): Promise<HealthProfileResult> {
    const response = await request.get<HealthProfileResult>(`/users/${userId}/health-profile`)
    return response.data
  }

  /**
   * 更新用户健康档案
   * PUT /api/users/{userId}/health-profile
   */
  async updateHealthProfile(userId: string | number, profile: HealthProfile): Promise<void> {
    // 失败由拦截器统一弹窗并 reject，无需重复检查 success
    await request.put(`/users/${userId}/health-profile`, profile)
  }

  /**
   * 删除用户健康档案
   * DELETE /api/users/{userId}/health-profile
   */
  async deleteHealthProfile(userId: string | number): Promise<void> {
    await request.delete(`/users/${userId}/health-profile`)
  }

  /**
   * 获取用户个人身份信息
   * GET /api/users/{userId}/identity
   *
   * silent：页面自动加载的读操作，失败交由调用方呈现错误态/降级，
   * 避免与视图错误态（UserCenterView 的 identityError、ProfileView 的人口学回显降级）重复弹全局提示。
   */
  async getIdentity(userId: string | number): Promise<IdentityResult> {
    const response = await request.get<IdentityResult>(`/users/${userId}/identity`, { silent: true })
    return response.data
  }

  /**
   * 保存用户个人身份信息（同步 fhir_patient_cache，医生端立即可检索）
   * PUT /api/users/{userId}/identity
   *
   * silent：写操作失败由调用方统一弹一次提示（UserCenterView.handleSave 已处理），避免双弹窗。
   */
  async updateIdentity(userId: string | number, identity: IdentityInfo): Promise<void> {
    await request.put(`/users/${userId}/identity`, identity, { silent: true })
  }
}

export const userService = new UserService()
