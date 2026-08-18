import request from '../request'

/** 禁忌规则 */
export interface ContraindicationRule {
  id?: number
  drugName: string
  atcCode?: string
  populationName: string
  contraindicationType: string
  evidenceLevel?: string
  description?: string
  source?: string
  isActive?: number
  /** 审核状态: published | draft | reviewing */
  status?: string
  createdAt?: string
  updatedAt?: string
}

/** 分页响应 */
export interface PaginationResponse<T> {
  data: T
  total: number
  page: number
  size: number
  totalPages: number
}

/** 批量导入结果 */
export interface BatchImportResult {
  total: number
  success: number
  skipped: number
}

class ContraindicationService {
  /** 分页查询 */
  async list(
    params: {
      page?: number
      size?: number
      drugName?: string
      populationName?: string
      contraindicationType?: string
      isActive?: number
    } = {}
  ): Promise<PaginationResponse<ContraindicationRule[]>> {
    const query = new URLSearchParams()
    query.append('page', (params.page || 1).toString())
    query.append('size', (params.size || 20).toString())
    if (params.drugName) query.append('drugName', params.drugName)
    if (params.populationName) query.append('populationName', params.populationName)
    if (params.contraindicationType) query.append('contraindicationType', params.contraindicationType)
    if (params.isActive !== undefined) query.append('isActive', params.isActive.toString())

    const response = await request.get<{
      success: boolean
      message: string
      data: PaginationResponse<ContraindicationRule[]>
    }>(`/admin/contraindications?${query}`)
    if (!response.data.success) {
      throw new Error(response.data.message || '查询失败')
    }
    return response.data.data
  }

  /** 新增规则 */
  async create(rule: ContraindicationRule): Promise<ContraindicationRule> {
    const response = await request.post<{
      success: boolean
      message: string
      data: ContraindicationRule
    }>('/admin/contraindications', rule)
    if (!response.data.success) {
      throw new Error(response.data.message || '新增失败')
    }
    return response.data.data
  }

  /** 编辑规则 */
  async update(id: number, rule: ContraindicationRule): Promise<ContraindicationRule> {
    const response = await request.put<{
      success: boolean
      message: string
      data: ContraindicationRule
    }>(`/admin/contraindications/${id}`, rule)
    if (!response.data.success) {
      throw new Error(response.data.message || '更新失败')
    }
    return response.data.data
  }

  /** 启用/禁用 */
  async toggle(id: number): Promise<void> {
    const response = await request.put<{ success: boolean; message: string }>(
      `/admin/contraindications/${id}/toggle`,
      {}
    )
    if (!response.data.success) {
      throw new Error(response.data.message || '操作失败')
    }
  }

  /** 删除 */
  async delete(id: number): Promise<void> {
    const response = await request.delete<{ success: boolean; message: string }>(`/admin/contraindications/${id}`)
    if (!response.data.success) {
      throw new Error(response.data.message || '删除失败')
    }
  }

  /** 批量导入 */
  async batchImport(rules: ContraindicationRule[]): Promise<BatchImportResult> {
    const response = await request.post<{
      success: boolean
      message: string
      data: BatchImportResult
    }>('/admin/contraindications/batch-import', rules)
    if (!response.data.success) {
      throw new Error(response.data.message || '导入失败')
    }
    return response.data.data
  }

  /** 刷新缓存 */
  async reloadCache(): Promise<void> {
    const response = await request.post<{ success: boolean; message: string }>(
      '/admin/contraindications/reload-cache',
      {}
    )
    if (!response.data.success) {
      throw new Error(response.data.message || '刷新缓存失败')
    }
  }
}

export const contraindicationService = new ContraindicationService()
