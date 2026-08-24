import request from '../request'
import { adminUserService } from './admin-user.service'

/** 导诊统计（对齐后端 TriageStats，裸 JSON 返回） */
export interface TriageStats {
  totalRequests: number
  successfulRequests: number
  failedRequests: number
  /** 成功率（0~1） */
  successRate: number
  avgProcessingTime: number
  ruleEngineRequests: number
  llmRequests: number
  hybridRequests: number
  fallbackRequests: number
  /** 规则引擎平均耗时（ms） */
  ruleEngineAvgTime: number
  /** 大语言模型平均耗时（ms） */
  llmAvgTime: number
  emergencyAvgTime: number
  ruleEngineSuccessRate: number
  llmSuccessRate: number
  timeoutCount: number
  /** 科室 → 请求数 */
  topDepartments?: Record<string, number>
  /** 急诊分级（CRITICAL/HIGH/MEDIUM/LOW）→ 数量 */
  emergencyDistribution?: Record<string, number>
  /** 引擎来源 → 数量 */
  sourceDistribution?: Record<string, number>
  /** 置信度区间 → 数量 */
  confidenceDistribution?: Record<number, number>
  statsStartTime?: string
  lastResetTime?: string
}

/** 分诊服务健康检查响应 */
export interface TriageHealthResponse {
  serviceAvailable: boolean
  totalRequests: number
  successRate: number
  averageResponseTime: number
  timestamp: string
}

/** 用户维度统计（前端全量聚合） */
export interface UserStats {
  total: number
  /** [角色枚举, 数量] 元组数组 */
  byRole: Array<[string, number]>
}

/**
 * 系统统计聚合服务
 * 复用现有接口（/users、/triage/stats、/qa/health、/triage/health），不新增后端接口
 */
class DashboardService {
  /** 分页拉全量用户并按角色聚合（后端无统计接口，前端全量计算） */
  async getUsersStats(): Promise<UserStats> {
    const size = 1000
    const first = await adminUserService.listUsers(1, size, { silent: true })
    const users = [...first.data]
    for (let p = 2; p <= first.totalPages; p++) {
      const page = await adminUserService.listUsers(p, size, { silent: true })
      users.push(...page.data)
    }
    const byRole = new Map<string, number>()
    users.forEach((u) => byRole.set(u.role, (byRole.get(u.role) || 0) + 1))
    return { total: first.total, byRole: Array.from(byRole.entries()) }
  }

  /** 导诊统计（ResponseEntity<TriageStats>，裸 JSON） */
  async getTriageStats(): Promise<TriageStats> {
    const response = await request.get<TriageStats>('/triage/stats', { silent: true })
    return response.data
  }

  /** 问答服务健康检查（200 即在线） */
  async checkQaHealth(): Promise<boolean> {
    const response = await request.get<string>('/qa/health', { silent: true })
    return response.status === 200
  }

  /** 分诊服务健康检查（非 2xx 视为离线） */
  async checkTriageHealth(): Promise<boolean> {
    try {
      const response = await request.get<TriageHealthResponse>('/triage/health', { silent: true })
      return response.data.serviceAvailable
    } catch {
      return false
    }
  }
}

export const dashboardService = new DashboardService()
