/**
 * 前端通用错误处理工具
 * 统一从 Axios 错误对象中提取可展示的消息
 */

/**
 * 提取可展示的错误消息
 * 依次尝试：后端响应 message → Axios 自带 message → 兜底文案
 * @param error 捕获到的异常（多为 AxiosError）
 * @param fallback 无任何消息时的兜底文案
 */
export function apiErrorMessage(error: unknown, fallback = '网络错误'): string {
  const e = error as { response?: { data?: { message?: string } }; message?: string } | undefined
  return e?.response?.data?.message || e?.message || fallback
}
