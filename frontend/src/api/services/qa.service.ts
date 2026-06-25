import axios from 'axios'
import { API_CONFIG } from '../config'
import type {
  QaAskRequest,
  QaResponse,
  QaStreamChunk,
  TriageRequest,
  TriageResponse,
  DrugQueryRequest,
  DrugResponse,
  RagStats,
  QaAskResponse,
  TriageResponseWrapper,
  DrugQueryResponse,
  RagStatsResponse
} from '../types/qa.types'
import { authService } from './auth.service'

class QaService {
  private baseURL = API_CONFIG.BASE_URL
  private sseController: AbortController | null = null

  // 获取认证头
  private getAuthHeaders() {
    const token = authService.getToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  }

  // 同步问答
  async ask(request: QaAskRequest): Promise<QaResponse> {
    try {
      const response = await axios.post<QaAskResponse>(
        `${this.baseURL}/qa/ask`,
        request,
        {
          headers: {
            'Content-Type': 'application/json',
            ...this.getAuthHeaders()
          }
        }
      )

      console.log('QA API响应:', response.data)

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '问答失败')
      }
    } catch (error: any) {
      console.error('问答请求失败:', error)
      console.error('错误详情:', error.response?.data)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 流式问答（使用 fetch + ReadableStream 支持 POST 和 JWT 认证）
  async streamAsk(
    request: QaAskRequest,
    onChunk: (chunk: QaStreamChunk) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ): Promise<() => void> {
    // 取消之前的连接
    this.stopStreaming()

    this.sseController = new AbortController()
    const token = authService.getToken()
    const headers: Record<string, string> = {
      'Content-Type': 'application/x-www-form-urlencoded',
      Accept: 'text/event-stream'
    }
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }

    // 构建表单参数
    const params = new URLSearchParams()
    params.append('query', request.question)
    if (request.userId) params.append('userId', request.userId.toString())
    if (request.conversationId) params.append('conversationId', request.conversationId)

    try {
      const response = await fetch(`${this.baseURL}/qa/stream`, {
        method: 'POST',
        headers,
        body: params.toString(),
        signal: this.sseController.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('浏览器不支持流式响应')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      const processLine = (line: string) => {
        if (line.startsWith('data:')) {
          const jsonStr = line.substring(5).trim()
          if (!jsonStr) return
          try {
            const parsed = JSON.parse(jsonStr)

            // 处理 retrievalPath 事件
            if (parsed.type === 'retrievalPath' && parsed.data) {
              onChunk({ type: 'retrievalPath', retrievalPath: parsed.data })
              return
            }

            const chunk: QaStreamChunk = parsed
            onChunk(chunk)

            if (chunk.type === 'done') {
              this.sseController = null
              onComplete?.()
            } else if (chunk.type === 'error') {
              this.sseController = null
              onError?.(new Error(chunk.error || '流式问答错误'))
            }
          } catch {
            // 非 JSON 数据，忽略
          }
        }
      }

      // 循环读取 SSE 数据流
      const readLoop = async () => {
        try {
          while (true) {
            const { done, value } = await reader.read()
            if (done) break

            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            // 保留最后一个可能不完整的行
            buffer = lines.pop() || ''

            for (const line of lines) {
              processLine(line)
            }
          }
          // 处理剩余的 buffer
          if (buffer.trim()) {
            processLine(buffer)
          }
        } catch (err: any) {
          if (err.name !== 'AbortError') {
            console.error('读取流式数据失败:', err)
            onError?.(new Error('流式连接中断'))
          }
        }
      }

      readLoop()
    } catch (err: any) {
      if (err.name !== 'AbortError') {
        console.error('流式请求失败:', err)
        onError?.(err instanceof Error ? err : new Error('流式连接失败'))
      }
    }

    return () => {
      this.stopStreaming()
    }
  }

  // 停止流式问答
  stopStreaming(): void {
    if (this.sseController) {
      this.sseController.abort()
      this.sseController = null
    }
  }

  // 科室导诊
  async triage(request: TriageRequest): Promise<TriageResponse> {
    try {
      const response = await axios.post<TriageResponseWrapper>(
        `${this.baseURL}/qa/triage`,
        request,
        {
          headers: {
            'Content-Type': 'application/json',
            ...this.getAuthHeaders()
          }
        }
      )

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '科室导诊失败')
      }
    } catch (error: any) {
      console.error('科室导诊失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 药物查询
  async queryDrug(request: DrugQueryRequest): Promise<DrugResponse> {
    try {
      const params = new URLSearchParams()
      params.append('name', request.name)
      if (request.brandName) params.append('brandName', request.brandName)
      if (request.genericName) params.append('genericName', request.genericName)

      const response = await axios.get<DrugQueryResponse>(
        `${this.baseURL}/qa/drug?${params.toString()}`,
        {
          headers: this.getAuthHeaders()
        }
      )

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '药物查询失败')
      }
    } catch (error: any) {
      console.error('药物查询失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 获取RAG统计信息
  async getRagStats(): Promise<RagStats> {
    try {
      const response = await axios.get<RagStatsResponse>(
        `${this.baseURL}/rag/stats`,
        {
          headers: this.getAuthHeaders()
        }
      )

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '获取统计信息失败')
      }
    } catch (error: any) {
      console.error('获取RAG统计失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }
}

export const qaService = new QaService()