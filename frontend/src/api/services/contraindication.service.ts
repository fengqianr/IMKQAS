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
  // silent: 页面自动加载的读操作建议传 { silent: true }，由视图呈现错误态，避免与全局弹窗重复提示
  async list(
    params: {
      page?: number
      size?: number
      drugName?: string
      populationName?: string
      contraindicationType?: string
      isActive?: number
    } = {},
    options?: { silent?: boolean }
  ): Promise<PaginationResponse<ContraindicationRule[]>> {
    const query = new URLSearchParams()
    query.append('page', (params.page || 1).toString())
    query.append('size', (params.size || 20).toString())
    if (params.drugName) query.append('drugName', params.drugName)
    if (params.populationName) query.append('populationName', params.populationName)
    if (params.contraindicationType) query.append('contraindicationType', params.contraindicationType)
    if (params.isActive !== undefined) query.append('isActive', params.isActive.toString())

    // 失败由拦截器统一弹窗/reject，成功直接返回业务数据
    const response = await request.get<{ data: PaginationResponse<ContraindicationRule[]> }>(
      `/admin/contraindications?${query}`,
      { silent: options?.silent }
    )
    return response.data.data
  }

  /** 新增规则 */
  async create(rule: ContraindicationRule): Promise<ContraindicationRule> {
    const response = await request.post<{ data: ContraindicationRule }>('/admin/contraindications', rule)
    return response.data.data
  }

  /** 编辑规则 */
  async update(id: number, rule: ContraindicationRule): Promise<ContraindicationRule> {
    const response = await request.put<{ data: ContraindicationRule }>(`/admin/contraindications/${id}`, rule)
    return response.data.data
  }

  /** 启用/禁用 */
  async toggle(id: number): Promise<void> {
    await request.put(`/admin/contraindications/${id}/toggle`, {})
  }

  /** 删除 */
  async delete(id: number): Promise<void> {
    await request.delete(`/admin/contraindications/${id}`)
  }

  /** 批量导入 */
  async batchImport(rules: ContraindicationRule[]): Promise<BatchImportResult> {
    const response = await request.post<{ data: BatchImportResult }>('/admin/contraindications/batch-import', rules)
    return response.data.data
  }

  /** 刷新缓存 */
  async reloadCache(): Promise<void> {
    await request.post(`/admin/contraindications/reload-cache`, {})
  }
}

export const contraindicationService = new ContraindicationService()
