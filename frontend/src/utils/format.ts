/**
 * 前端通用格式化/工具函数
 */

/**
 * 耗时格式化：1234 -> "1234ms"，空值返回 "—"
 */
export const formatDuration = (ms?: number | null): string => (ms ? `${Math.round(ms)}ms` : '—')

/**
 * 取名称首字（头像占位用）
 * @param name 名称
 * @param fb 空值兜底字符
 */
export const initialOf = (name?: string, fb = '?'): string => (name ? name.charAt(0) : fb)

/**
 * 触发浏览器下载 Blob 文件
 * @param blob 文件内容
 * @param filename 下载文件名
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
