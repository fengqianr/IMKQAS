import request from '../request'
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
} from '../types/qa'
import { authService } from './auth.service'
import { apiErrorMessage } from '@/utils/error'

class QaService {
  private sseController: AbortController | null = null

  // 同步问答
  async ask(req: QaAskRequest): Promise<QaResponse> {
    try {
      const response = await request.post<QaAskResponse>('/qa/ask', req)

      console.log('QA API响应:', response.data)

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '问答失败')
      }
    } catch (error: any) {
      console.error('问答请求失败:', error)
      console.error('错误详情:', error.response?.data)
      throw new Error(apiErrorMessage(error))
    }
  }

  // 流式问答（使用 fetch + ReadableStream 支持 POST 和 JWT 认证）
  async streamAsk(
    req: QaAskRequest,
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
    params.append('query', req.question)
    if (req.userId) params.append('userId', req.userId.toString())
    if (req.conversationId) params.append('conversationId', req.conversationId)

    try {
      const response = await fetch(`${request.defaults.baseURL || ''}/qa/stream`, {
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
          for (;;) {
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
  async triage(req: TriageRequest): Promise<TriageResponse> {
    try {
      const response = await request.post<TriageResponseWrapper>('/qa/triage', req)

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '科室导诊失败')
      }
    } catch (error: any) {
      console.error('科室导诊失败:', error)
      throw new Error(apiErrorMessage(error))
    }
  }

  // 药物查询
  async queryDrug(req: DrugQueryRequest): Promise<DrugResponse> {
    try {
      const params = new URLSearchParams()
      params.append('name', req.name)
      if (req.brandName) params.append('brandName', req.brandName)
      if (req.genericName) params.append('genericName', req.genericName)

      const response = await request.get<DrugQueryResponse>(`/qa/drug?${params.toString()}`)

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '药物查询失败')
      }
    } catch (error: any) {
      console.error('药物查询失败:', error)
      throw new Error(apiErrorMessage(error))
    }
  }

  // 获取RAG统计信息
  async getRagStats(): Promise<RagStats> {
    try {
      const response = await request.get<RagStatsResponse>('/rag/stats')

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '获取统计信息失败')
      }
    } catch (error: any) {
      console.error('获取RAG统计失败:', error)
      throw new Error(apiErrorMessage(error))
    }
  }
}

export const qaService = new QaService()
