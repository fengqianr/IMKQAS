import axios from 'axios'
import { API_CONFIG } from '../config'
import { authService } from './auth.service'
import type {
  InterviewSuggestion,
  InterviewSSEEvent,
  QuestionnaireTemplate,
  StartInterviewRequest,
  SubmitAnswerRequest,
  AnalysisReport,
  InterviewMessageItem,
  InterviewHistoryItem
} from '../types/interview.types'

class InterviewService {
  private baseURL = API_CONFIG.BASE_URL

  private getAuthHeaders() {
    const token = authService.getToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  }

  // 获取问卷建议
  async suggest(userInput: string): Promise<InterviewSuggestion> {
    try {
      const response = await axios.post(
        `${this.baseURL}/his/interview/suggest`,
        { userInput },
        { headers: { 'Content-Type': 'application/json', ...this.getAuthHeaders() } }
      )
      return response.data.data as InterviewSuggestion
    } catch (error: any) {
      console.error('获取问卷建议失败:', error)
      throw new Error(error.response?.data?.message || '网络错误')
    }
  }

  // 启动LLM驱动填表（SSE流式）
  async startLlmInterview(
    request: StartInterviewRequest,
    onEvent: (event: InterviewSSEEvent) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ): Promise<void> {
    console.log('[InterviewService] startLlmInterview 请求:', {
      url: `${this.baseURL}/his/interview/start-llm`,
      questionnaireId: request.questionnaireId,
      userId: request.userId,
      conversationId: request.conversationId
    })
    try {
      const token = authService.getToken()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      }
      if (token) headers.Authorization = `Bearer ${token}`

      const response = await fetch(`${this.baseURL}/his/interview/start-llm`, {
        method: 'POST',
        headers,
        body: JSON.stringify(request)
      })

      console.log('[InterviewService] startLlmInterview 响应:', {
        status: response.status,
        ok: response.ok,
        statusText: response.statusText
      })

      if (!response.ok) {
        const errorBody = await response.text().catch(() => '')
        console.error('[InterviewService] startLlmInterview HTTP错误:', response.status, errorBody)
        throw new Error(`HTTP ${response.status}: ${errorBody || response.statusText}`)
      }

      await this.readSSEStream(response, onEvent, onError, onComplete)
    } catch (err: any) {
      console.error('[InterviewService] startLlmInterview 异常:', err.name, err.message)
      if (err.name !== 'AbortError') {
        onError?.(err instanceof Error ? err : new Error('启动填表失败'))
      }
    }
  }

  // 提交LLM回答（SSE流式）
  async submitLlmAnswer(
    request: SubmitAnswerRequest,
    onEvent: (event: InterviewSSEEvent) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ): Promise<void> {
    try {
      const token = authService.getToken()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      }
      if (token) headers.Authorization = `Bearer ${token}`

      const response = await fetch(`${this.baseURL}/his/interview/llm-answer`, {
        method: 'POST',
        headers,
        body: JSON.stringify(request)
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      await this.readSSEStream(response, onEvent, onError, onComplete)
    } catch (err: any) {
      if (err.name !== 'AbortError') {
        onError?.(err instanceof Error ? err : new Error('提交回答失败'))
      }
    }
  }

  // 获取问卷列表
  async getQuestionnaires(): Promise<QuestionnaireTemplate[]> {
    try {
      const response = await axios.get(
        `${this.baseURL}/his/interview/questionnaires`,
        { headers: this.getAuthHeaders() }
      )
      return response.data.data as QuestionnaireTemplate[]
    } catch (error: any) {
      console.error('获取问卷列表失败:', error)
      return []
    }
  }

  // 获取问卷详情
  async getQuestionnaire(id: string): Promise<QuestionnaireTemplate | null> {
    try {
      const response = await axios.get(
        `${this.baseURL}/his/interview/questionnaires/${id}`,
        { headers: this.getAuthHeaders() }
      )
      return response.data.data as QuestionnaireTemplate
    } catch (error: any) {
      console.error('获取问卷详情失败:', error)
      return null
    }
  }

  // 获取会话的访谈消息列表（用于重建历史问卷卡片）
  async getSessionMessages(sessionId: string): Promise<InterviewMessageItem[]> {
    try {
      const response = await axios.get(
        `${this.baseURL}/his/interview/${sessionId}/messages`,
        { headers: this.getAuthHeaders() }
      )
      return (response.data.data || []) as InterviewMessageItem[]
    } catch (error: any) {
      console.error('获取访谈消息失败:', error)
      return []
    }
  }

  // 获取完整AI分析报告
  async getAnalysisReport(sessionId: string): Promise<AnalysisReport | null> {
    try {
      const response = await axios.get(
        `${this.baseURL}/his/interview/${sessionId}/analysis`,
        { headers: this.getAuthHeaders() }
      )
      return response.data.data as AnalysisReport
    } catch (error: any) {
      console.error('获取分析报告失败:', error)
      return null
    }
  }

  // 获取对话下的所有访谈记录
  async getInterviewsByConversation(conversationId: string): Promise<InterviewHistoryItem[]> {
    try {
      const response = await axios.get(
        `${this.baseURL}/his/interview/by-conversation/${conversationId}`,
        { headers: this.getAuthHeaders() }
      )
      return (response.data.data || []) as InterviewHistoryItem[]
    } catch (error: any) {
      console.error('获取访谈记录失败:', error)
      return []
    }
  }

  // 获取用户历史填写记录（评分趋势等）
  async getHistory(userId: number, questionnaireId?: string): Promise<any[]> {
    try {
      const params: any = { userId }
      if (questionnaireId) params.questionnaireId = questionnaireId
      const response = await axios.get(
        `${this.baseURL}/his/interview/history`,
        { params, headers: this.getAuthHeaders() }
      )
      return (response.data.data || []) as any[]
    } catch (error: any) {
      console.error('获取历史记录失败:', error)
      return []
    }
  }

  // 通用的 SSE 流读取
  private async readSSEStream(
    response: Response,
    onEvent: (event: InterviewSSEEvent) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ): Promise<void> {
    const reader = response.body?.getReader()
    if (!reader) {
      onError?.(new Error('浏览器不支持流式响应'))
      return
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let completed = false

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const jsonStr = line.substring(5).trim()
            if (!jsonStr) continue
            try {
              const parsed = JSON.parse(jsonStr) as InterviewSSEEvent
              onEvent(parsed)
              if (parsed.type === 'done' || parsed.type === 'error') {
                completed = true
              }
            } catch {
              // 忽略非 JSON 数据
            }
          }
        }
      }

      if (buffer.trim() && buffer.startsWith('data:')) {
        const jsonStr = buffer.substring(5).trim()
        if (jsonStr) {
          try {
            const parsed = JSON.parse(jsonStr) as InterviewSSEEvent
            onEvent(parsed)
          } catch { /* ignore */ }
        }
      }

      if (completed) {
        onComplete?.()
      }
    } catch (err: any) {
      if (err.name !== 'AbortError') {
        onError?.(err instanceof Error ? err : new Error('流式连接中断'))
      }
    }
  }
}

export const interviewService = new InterviewService()
