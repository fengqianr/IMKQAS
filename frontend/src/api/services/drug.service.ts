import request from '../request'
import type { Drug, DrugInteraction } from '../types/drug'

/**
 * 药物查询服务
 *
 * 重要：DrugController 返回裸 JSON（ResponseEntity<List<Drug>> 等），
 * 非 ApiResponse 包装，因此所有响应均取 response.data（数组或对象），
 * 与 fhir 服务的 response.data.data 用法不同。
 */
class DrugService {
  /** 按名称搜索药品（通用名/商品名/别名模糊匹配，返回完整 Drug 对象列表） */
  async searchByName(name: string): Promise<Drug[]> {
    try {
      const response = await request.get('/drugs/search', { params: { name } })
      return (response.data || []) as Drug[]
    } catch (error: any) {
      console.error('药品搜索失败:', error)
      return []
    }
  }

  /** 获取药品分类列表 */
  async getClasses(): Promise<string[]> {
    try {
      const response = await request.get('/drugs/classes')
      return (response.data || []) as string[]
    } catch (error: any) {
      console.error('药品分类获取失败:', error)
      return []
    }
  }

  /** 按分类查询药品 */
  async getDrugsByClass(drugClass: string): Promise<Drug[]> {
    try {
      const response = await request.get(`/drugs/classes/${encodeURIComponent(drugClass)}`)
      return (response.data || []) as Drug[]
    } catch (error: any) {
      console.error('分类查询药品失败:', error)
      return []
    }
  }

  /** 药品详情（按 ID） */
  async getDrugById(id: number): Promise<Drug | null> {
    try {
      const response = await request.get(`/drugs/${id}`)
      return (response.data as Drug) || null
    } catch (error: any) {
      console.error('药品详情获取失败:', error)
      return null
    }
  }

  /** 批量检查药物相互作用（后端对 ID 列表做两两组合检查，ID 为字符串形式） */
  async checkBatch(drugIds: string[]): Promise<DrugInteraction[]> {
    try {
      const response = await request.post('/drugs/interactions/batch', drugIds)
      return (response.data || []) as DrugInteraction[]
    } catch (error: any) {
      console.error('药物相互作用检查失败:', error)
      return []
    }
  }
}

export const drugService = new DrugService()
