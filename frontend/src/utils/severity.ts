/**
 * 风险/严重度等级归一化工具
 * 把后端返回的多种等级字符串（LOW/MEDIUM/HIGH/CRITICAL、1-5）统一为语义 tone。
 * 注意：本模块只负责「字符串 → 语义等级」，颜色映射由各组件/页面自行处理。
 */

export type SeverityTone = 'low' | 'medium' | 'high' | 'critical'

/**
 * 归一化严重度字符串为语义 tone
 * @param s 后端原始等级（如 CRITICAL / HIGH / 3 等），可为空
 */
export function severityTone(s?: string | null): SeverityTone {
  const k = String(s ?? '').toUpperCase()
  if (k.includes('CRIT') || k === '5') return 'critical'
  if (k === 'HIGH' || k === '3') return 'high'
  if (k === 'MEDIUM' || k === '2') return 'medium'
  return 'low'
}
