import axios from 'axios'
import { API_CONFIG } from '../config'
import { authService } from './auth.service'

/** 未映射词条项 */
export interface UnmappedTermItem {
  id: number
  term: string
  contextQuery: string
  guessedEntityType: string
  llmGuess?: string
  llmConfidence?: number
  occurrenceCount: number
  status: string
  reviewer?: string
  reviewNote?: string
  firstSeenAt: string
  lastSeenAt: string
  entityTypeLabel?: string
  reviewerName?: string
}

/** 分页响应 */
export interface PaginationResponse<T> {
  data: T
  total: number
  page: number
  size: number
  totalPages: number
}

/** 审核请求 */
export interface ApproveRequest {
  id: number
  standardTerm: string
  entityType?: string
  snomedConceptId?: string
}

/** 统计数据 */
export interface AdminStats {
  pendingCount: number
  approvedTodayCount: number
  unmappedRate: number
  alertThreshold: number
  alertTriggered: boolean
  totalAlertCount: number
  topUnmappedTerms: string[]
}

class AdminService {
  private baseURL = API_CONFIG.BASE_URL

  private getAuthHeaders() {
    const token = authService.getToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  }

  /** 分页获取待审核词条 */
  async getUnmappedTerms(params: {
    page?: number
    size?: number
    status?: string
  } = {}): Promise<PaginationResponse<UnmappedTermItem[]>> {
    const { page = 1, size = 20, status } = params
    const query = new URLSearchParams()
    query.append('page', page.toString())
    query.append('size', size.toString())
    if (status) query.append('status', status)

    const response = await axios.get<{
      success: boolean
      message: string
      data: {
        data: UnmappedTermItem[]
        total: number
        page: number
        size: number
        totalPages: number
      }
    }>(`${this.baseURL}/admin/unmapped-terms?${query.toString()}`, {
      headers: this.getAuthHeaders()
    })

    if (response.data.success && response.data.data) {
      return response.data.data
    }
    throw new Error(response.data.message || '获取词条列表失败')
  }

  /** 获取统计数据 */
  async getStats(): Promise<AdminStats> {
    const response = await axios.get<{ success: boolean; data: AdminStats }>(
      `${this.baseURL}/admin/unmapped-terms/stats`,
      { headers: this.getAuthHeaders() }
    )
    if (response.data.success && response.data.data) {
      return response.data.data
    }
    throw new Error('获取统计数据失败')
  }

  /** 单个审核通过 */
  async approveTerm(id: number, standardTerm: string): Promise<void> {
    await axios.post(
      `${this.baseURL}/admin/unmapped-terms/${id}/approve`,
      { standardTerm },
      {
        headers: {
          'Content-Type': 'application/json',
          ...this.getAuthHeaders()
        }
      }
    )
  }

  /** 批量审核通过 */
  async batchApprove(requests: ApproveRequest[]): Promise<{ total: number; success: number; failed: number }> {
    const response = await axios.post<{
      success: boolean
      message: string
      data: { total: number; success: number; failed: number }
    }>(
      `${this.baseURL}/admin/unmapped-terms/batch-approve`,
      requests,
      {
        headers: {
          'Content-Type': 'application/json',
          ...this.getAuthHeaders()
        }
      }
    )
    if (response.data.success && response.data.data) {
      return response.data.data
    }
    throw new Error(response.data.message || '批量审核失败')
  }

  /** 拒绝词条 */
  async rejectTerm(id: number, reason: string = ''): Promise<void> {
    await axios.post(
      `${this.baseURL}/admin/unmapped-terms/${id}/reject`,
      { reason },
      {
        headers: {
          'Content-Type': 'application/json',
          ...this.getAuthHeaders()
        }
      }
    )
  }

  /** 获取未映射率 */
  async getUnmappedRate(): Promise<AdminStats> {
    const response = await axios.get<{ success: boolean; data: AdminStats }>(
      `${this.baseURL}/admin/stats/unmapped-rate`,
      { headers: this.getAuthHeaders() }
    )
    if (response.data.success && response.data.data) {
      return response.data.data
    }
    throw new Error('获取未映射率失败')
  }
}

export const adminService = new AdminService()
