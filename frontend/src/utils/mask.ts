/**
 * 敏感信息脱敏工具
 * 全站统一格式：手机号 138-****-5678、证件号 前4后4（与患者端 fhir.ts 既有规范一致）
 */

/**
 * 手机号脱敏：13812345678 -> 138-****-5678
 * @param phone 原始手机号
 * @param fb 空值兜底文案
 */
export function maskPhone(phone?: string, fb = '—'): string {
  const digits = (phone ?? '').replace(/\D/g, '')
  if (digits.length > 7) return `${digits.slice(0, 3)}-****-${digits.slice(-4)}`
  return phone || fb
}

/**
 * 证件号脱敏：前4位 + 星号 + 后4位
 * @param id 原始证件号
 * @param fb 空值兜底文案
 */
export function maskIdNumber(id?: string, fb = '—'): string {
  if (!id) return fb
  if (id.length <= 8) return `${id.slice(0, 2)}***${id.slice(-2)}`
  const maskLen = id.length - 8
  return `${id.slice(0, 4)}${'*'.repeat(maskLen)}${id.slice(-4)}`
}
