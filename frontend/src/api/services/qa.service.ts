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

  // 流式问答
  streamAsk(
    request: QaAskRequest,
    onChunk: (chunk: QaStreamChunk) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ): () => void {
    // 取消之前的SSE连接
    this.stopStreaming()

    const params = new URLSearchParams()
    params.append('query', request.question)
    if (request.userId) params.append('userId', request.userId.toString())
    if (request.conversationId) params.append('conversationId', request.conversationId)

    const token = authService.getToken()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    }

    if (token) {
      headers.Authorization = `Bearer ${token}`
    }

    this.sseController = new AbortController()
    // signal is available but EventSource doesn't support it directly
    const _signal = this.sseController.signal

    const eventSource = new EventSource(
      `${this.baseURL}/qa/stream?${params.toString()}`,
      {
        withCredentials: true,
        headers
      } as any
    )

    eventSource.onopen = () => {
      console.log('SSE连接已建立')
    }

    eventSource.onmessage = (event) => {
      try {
        const chunk: QaStreamChunk = JSON.parse(event.data)
        onChunk(chunk)

        if (chunk.type === 'done' || chunk.type === 'error') {
          eventSource.close()
          if (chunk.type === 'error') {
            onError?.(new Error(chunk.error || '流式问答错误'))
          } else {
            onComplete?.()
          }
        }
      } catch (error) {
        console.error('解析SSE数据失败:', error)
        onError?.(new Error('解析响应数据失败'))
      }
    }

    eventSource.onerror = (error) => {
      console.error('SSE连接错误:', error)
      eventSource.close()
      onError?.(new Error('流式连接中断'))
    }

    // 返回取消函数
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