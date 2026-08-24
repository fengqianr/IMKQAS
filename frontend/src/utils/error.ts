/**
 * 前端通用错误处理工具
 * 统一从 Axios 错误对象中提取可展示的消息
 *
 * 核心原则：提示框（ElMessage）展示给用户的信息必须是可读的中文语句，
 * 不得直接暴露 HTTP 状态码、英文原始消息等对用户无意义的内容。
 */

/** HTTP 状态码 → 中文描述语句映射 */
const HTTP_STATUS_MESSAGES: Record<number, string> = {
  400: '请求参数错误，请检查输入后重试',
  401: '登录状态已失效，请重新登录',
  403: '您没有权限执行该操作',
  404: '请求的资源不存在',
  405: '请求方式不被支持',
  408: '请求超时，请稍后重试',
  409: '数据冲突，请刷新后重试',
  415: '请求的数据格式不支持',
  422: '参数校验未通过，请检查输入',
  429: '请求过于频繁，请稍后重试',
  500: '服务器内部错误，请稍后重试',
  501: '服务器暂不支持该功能',
  502: '网关错误，请稍后重试',
  503: '服务暂时不可用，请稍后重试',
  504: '网关超时，请稍后重试'
}

/** 匹配英文/含状态码的错误消息，如 "Request failed with status code 500"、"HTTP 500" */
const STATUS_CODE_PATTERN = /(?:status code|HTTP)\s+(\d{3})/i

/**
 * 根据 HTTP 状态码返回对应的中文描述语句
 *
 * @param status HTTP 状态码（数字或字符串，如 500 / '500'）
 * @returns 中文描述；未知状态码时返回空字符串
 */
export function httpStatusMessage(status?: number | string | null): string {
  if (status == null || status === '') return ''
  const code = typeof status === 'string' ? Number.parseInt(status, 10) : status
  if (!code || !(code in HTTP_STATUS_MESSAGES)) return ''
  return HTTP_STATUS_MESSAGES[code]
}

/** 判断字符串是否为纯数字（可能是状态码本身，如 "500"） */
function isPureNumber(value: string): boolean {
  return /^\d+$/.test(value.trim())
}

/**
 * 提取可展示的错误消息
 *
 * 依次尝试：后端响应 message → 文本错误信息 → HTTP 状态码中文映射 →
 * 从 Axios message 提取状态码映射 → 网络/超时中文提示 → 兜底文案。
 *
 * @param error 捕获到的异常（多为 AxiosError）
 * @param fallback 无任何消息时的兜底文案
 */
export function apiErrorMessage(error: unknown, fallback = '网络错误'): string {
  const e = error as
    | {
        response?: { status?: number; data?: { message?: string } | string | null }
        message?: string
      }
    | undefined

  const status = e?.response?.status

  // 1. 优先取后端响应体 message（业务错误提示，通常为中文）
  const responseData = e?.response?.data
  if (responseData && typeof responseData === 'object') {
    const backendMessage = responseData.message
    if (typeof backendMessage === 'string' && backendMessage.trim()) {
      // 若后端 message 本身就是纯状态码（如 "500"），映射为中文语句
      if (isPureNumber(backendMessage)) {
        const mapped = httpStatusMessage(backendMessage) || httpStatusMessage(status)
        if (mapped) return mapped
      }
      return backendMessage
    }
  }

  // 2. 后端直接返回文本形式的错误信息
  if (typeof responseData === 'string' && responseData.trim()) {
    return responseData.trim()
  }

  // 3. 根据 HTTP 状态码映射中文语句
  if (status) {
    const mapped = httpStatusMessage(status)
    if (mapped) return mapped
  }

  // 4. 从 error.message 提取状态码并映射（覆盖 Axios 的英文错误消息）
  const rawMessage = e?.message
  if (rawMessage) {
    // 消息本身是纯状态码（如 "500"）时映射为中文语句
    if (isPureNumber(rawMessage)) {
      const mapped = httpStatusMessage(rawMessage)
      if (mapped) return mapped
    }
    const matched = rawMessage.match(STATUS_CODE_PATTERN)
    if (matched) {
      const mapped = httpStatusMessage(matched[1])
      if (mapped) return mapped
    }
    // 网络层错误
    if (/network\s*error/i.test(rawMessage)) {
      return '网络连接异常，请检查网络后重试'
    }
    // 请求超时
    if (/timeout|timed\s*out/i.test(rawMessage)) {
      return '请求超时，请稍后重试'
    }
    // 其他原始消息（如 service 层抛出的中文业务错误）
    return rawMessage
  }

  return fallback
}
