import request from '../request'
import type { BatchTriageRequest, DepartmentTriageResult } from '../types/triage'

/**
 * 批量导诊服务
 *
 * 重要：DepartmentTriageController 返回裸 JSON（ResponseEntity<List<DepartmentTriageResult>>），
 * 非 ApiResponse 包装，因此响应取 response.data（数组）。
 * 与 drug.service 一致，遵循裸 JSON 模式。
 *
 * 注意：批量导诊不在此方法内吞错，交由组件层 try/catch
 * 呈现「分诊服务不可用」错误态（与 drug.service 的静默降级不同）。
 */
class TriageService {
  /** 批量症状分流：一次处理最多 20 条症状描述，返回对应科室推荐结果列表 */
  async batchTriage(req: BatchTriageRequest): Promise<DepartmentTriageResult[]> {
    const response = await request.post('/triage/batch', req)
    return (response.data || []) as DepartmentTriageResult[]
  }
}

export const triageService = new TriageService()
