import request from '../request'
import type { Drug, DrugInteraction } from '../types/drug'

/**
 * 药物查询服务
 *
 * 重要：DrugController 返回裸 JSON（ResponseEntity<List<Drug>> 等），
 * 非 ApiResponse 包装，因此所有响应均取 response.data（数组或对象）。
 *
 * 错误策略：所有方法使用 silent:true 抑制全局错误弹窗，请求失败时**抛出异常**
 * 交由调用方呈现错误态，从而区分"无结果"与"加载失败"——
 * 尤其药物相互作用检查失败不得被误判为"无相互作用"（医疗安全）。
 */
class DrugService {
  /** 按名称搜索药品（通用名/商品名/别名模糊匹配，返回完整 Drug 对象列表） */
  async searchByName(name: string): Promise<Drug[]> {
    const response = await request.get('/drugs/search', { params: { name }, silent: true })
    return (response.data || []) as Drug[]
  }

  /** 获取药品分类列表 */
  async getClasses(): Promise<string[]> {
    const response = await request.get('/drugs/classes', { silent: true })
    return (response.data || []) as string[]
  }

  /** 按分类查询药品 */
  async getDrugsByClass(drugClass: string): Promise<Drug[]> {
    const response = await request.get(`/drugs/classes/${encodeURIComponent(drugClass)}`, { silent: true })
    return (response.data || []) as Drug[]
  }

  /** 药品详情（按 ID） */
  async getDrugById(id: number): Promise<Drug | null> {
    const response = await request.get(`/drugs/${id}`, { silent: true })
    return (response.data as Drug) || null
  }

  /** 批量检查药物相互作用（后端对 ID 列表做两两组合检查，ID 为字符串形式） */
  async checkBatch(drugIds: string[]): Promise<DrugInteraction[]> {
    const response = await request.post('/drugs/interactions/batch', drugIds, { silent: true })
    return (response.data || []) as DrugInteraction[]
  }
}

export const drugService = new DrugService()
