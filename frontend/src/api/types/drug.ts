// 药物类型定义与解析工具
// 注意：DrugController 返回裸 JSON（非 ApiResponse 包装），
// 枚举字段序列化为英文名（如 interactionType: "SEVERE"），需在此处映射为中文

// ==================== Drug 药品 ====================

export interface Drug {
  id: number
  /** 通用名 */
  genericName?: string
  /** 商品名 */
  brandName?: string
  /** 英文名 */
  englishName?: string
  /** 药品分类 */
  drugClass?: string
  /** 剂型 */
  dosageForm?: string
  /** 规格 */
  specification?: string
  /** 生产厂商 */
  manufacturer?: string
  /** 适应症（JSON 数组字符串） */
  indications?: string
  /** 禁忌症（JSON 数组字符串） */
  contraindications?: string
  /** 不良反应（JSON 数组字符串） */
  adverseReactions?: string
  /** 用法用量 */
  dosage?: string
  /** 注意事项 */
  precautions?: string
  /** 储存条件 */
  storage?: string
  /** 批准文号 */
  approvalNumber?: string
  /** 是否有相互作用标记（1 有 / 0 无） */
  hasInteractions?: number
}

/** 解析 JSON 数组字符串字段（适应症/禁忌症/不良反应），容错返回空数组 */
export function parseArray(jsonStr?: string | null): string[] {
  if (!jsonStr) return []
  try {
    const parsed = JSON.parse(jsonStr)
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : []
  } catch {
    return []
  }
}

/** 药品标题：通用名 */
export function drugTitle(d?: Drug | null): string {
  return d?.genericName || '未命名药品'
}

/** 药品副标题：商品名 · 英文名（无则退化为剂型） */
export function drugSubtitle(d?: Drug | null): string {
  const parts = [d?.brandName, d?.englishName].filter(Boolean)
  return parts.length ? parts.join(' · ') : d?.dosageForm || ''
}

// ==================== DrugInteraction 药物相互作用 ====================

export interface DrugInteraction {
  id?: number
  /** 药品 A ID */
  drugAId: number
  /** 药品 B ID */
  drugBId: number
  /** 相互作用类型（英文枚举名） */
  interactionType?: string
  /** 严重程度（英文枚举名） */
  severity?: string
  /** 相互作用描述 */
  description?: string
  /** 作用机制 */
  mechanism?: string
  /** 用药建议 */
  recommendation?: string
}

/** 徽标配色档位，由组件映射到具体 CSS 类 */
export type BadgeTone = 'danger' | 'warning' | 'info' | 'muted'

/** 相互作用类型中文描述 */
export function interactionTypeText(type?: string): string {
  const map: Record<string, string> = {
    CONTRAINDICATED: '禁忌合用',
    SEVERE: '严重相互作用',
    MODERATE: '中等相互作用',
    MILD: '轻度相互作用',
    MONITOR: '需要监测',
    UNKNOWN: '未知相互作用'
  }
  return (type && map[type]) || type || '未知'
}

/** 严重程度中文描述 */
export function severityText(severity?: string): string {
  const map: Record<string, string> = { HIGH: '高', MODERATE: '中', LOW: '低' }
  return (severity && map[severity]) || severity || ''
}

/** 严重程度配色档位 */
export function severityTone(severity?: string): BadgeTone {
  const map: Record<string, BadgeTone> = { HIGH: 'danger', MODERATE: 'warning', LOW: 'info' }
  return (severity && map[severity]) || 'muted'
}

/** 相互作用类型配色档位 */
export function interactionTone(type?: string): BadgeTone {
  const map: Record<string, BadgeTone> = {
    CONTRAINDICATED: 'danger',
    SEVERE: 'danger',
    MODERATE: 'warning',
    MILD: 'info',
    MONITOR: 'muted',
    UNKNOWN: 'muted'
  }
  return (type && map[type]) || 'muted'
}
