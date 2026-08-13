import request from '../request'
import { authService } from './auth.service'
import type {
  InterviewSuggestion,
  InterviewSSEEvent,
  QuestionnaireTemplate,
  StartInterviewRequest,
  SubmitAnswerRequest,
  AnalysisReport,
  InterviewMessageItem,
  InterviewHistoryItem,
  InterviewSession,
  BatchSubmitResponse
} from '../types/interview'

/** 评分趋势点（/his/interview/trend 返回） */
export interface TrendPoint {
  date: string
  score: number
  severity?: string
}

class InterviewService {
  // 获取问卷建议
  async suggest(userInput: string): Promise<InterviewSuggestion> {
    try {
      const response = await request.post(
        '/his/interview/suggest',
        { userInput }
      )
      return response.data.data as InterviewSuggestion
    } catch (error: any) {
      console.error('获取问卷建议失败:', error)
      throw new Error(error.response?.data?.message || '网络错误')
    }
  }

  // 启动LLM驱动填表（SSE流式）
  async startLlmInterview(
    req: StartInterviewRequest,
    onEvent: (event: InterviewSSEEvent) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ): Promise<void> {
    const baseURL = request.defaults.baseURL || ''
    console.log('[InterviewService] startLlmInterview 请求:', {
      url: `${baseURL}/his/interview/start-llm`,
      questionnaireId: req.questionnaireId,
      userId: req.userId,
      conversationId: req.conversationId
    })
    try {
      const token = authService.getToken()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      }
      if (token) headers.Authorization = `Bearer ${token}`

      const response = await fetch(`${baseURL}/his/interview/start-llm`, {
        method: 'POST',
        headers,
        body: JSON.stringify(req)
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
    req: SubmitAnswerRequest,
    onEvent: (event: InterviewSSEEvent) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ): Promise<void> {
    const baseURL = request.defaults.baseURL || ''
    try {
      const token = authService.getToken()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      }
      if (token) headers.Authorization = `Bearer ${token}`

      const response = await fetch(`${baseURL}/his/interview/llm-answer`, {
        method: 'POST',
        headers,
        body: JSON.stringify(req)
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

  // 暂停访谈（sendBeacon，用于页面关闭/刷新时保留数据）
  pauseInterview(sessionId: string): void {
    const baseURL = request.defaults.baseURL || ''
    navigator.sendBeacon(
      `${baseURL}/his/interview/${sessionId}/pause`,
      new Blob(['{}'], { type: 'application/json' })
    )
  }

  // 主动放弃访谈（彻底清理）
  async abandonInterview(sessionId: string): Promise<void> {
    try {
      await request.post(
        `/his/interview/${sessionId}/abandon`,
        {}
      )
    } catch (error: any) {
      console.error('放弃访谈失败:', error)
    }
  }

  // 取消访谈（委托到 abandon）
  async cancelInterview(sessionId: string): Promise<void> {
    return this.abandonInterview(sessionId)
  }

  // 恢复中断的访谈
  async resumeInterview(sessionId: string): Promise<InterviewSession | null> {
    try {
      const response = await request.post(
        `/his/interview/${sessionId}/resume`,
        {}
      )
      return response.data.data as InterviewSession
    } catch (error: any) {
      console.error('恢复访谈失败:', error)
      return null
    }
  }

  // 心跳保活
  async heartbeat(sessionId: string): Promise<void> {
    try {
      await request.post(
        `/his/interview/${sessionId}/heartbeat`,
        {}
      )
    } catch {
      // 心跳失败不报错
    }
  }

  // 获取问卷列表
  async getQuestionnaires(): Promise<QuestionnaireTemplate[]> {
    try {
      const response = await request.get(
        '/his/interview/questionnaires'
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
      const response = await request.get(
        `/his/interview/questionnaires/${id}`
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
      const response = await request.get(
        `/his/interview/${sessionId}/messages`
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
      const response = await request.get(
        `/his/interview/${sessionId}/analysis`
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
      const response = await request.get(
        `/his/interview/by-conversation/${conversationId}`
      )
      return (response.data.data || []) as InterviewHistoryItem[]
    } catch (error: any) {
      console.error('获取访谈记录失败:', error)
      return []
    }
  }

  // 纯表单模式批量提交
  async batchSubmit(sessionId: string, answers: Record<string, string>): Promise<BatchSubmitResponse> {
    try {
      const response = await request.post(
        `/his/interview/${sessionId}/batch-submit`,
        { answers }
      )
      return response.data.data as BatchSubmitResponse
    } catch (error: any) {
      console.error('批量提交失败:', error)
      throw new Error(error.response?.data?.message || '批量提交失败')
    }
  }

  // 获取用户历史填写记录（评分趋势等）
  async getHistory(userId: number, questionnaireId?: string): Promise<any[]> {
    try {
      const params: any = { userId }
      if (questionnaireId) params.questionnaireId = questionnaireId
      const response = await request.get(
        '/his/interview/history',
        { params }
      )
      return (response.data.data || []) as any[]
    } catch (error: any) {
      console.error('获取历史记录失败:', error)
      return []
    }
  }

  // 获取评分趋势数据（用于图表展示）
  async getTrend(userId: number, questionnaireId: string): Promise<TrendPoint[]> {
    try {
      const response = await request.get(
        '/his/interview/trend',
        { params: { userId, questionnaireId } }
      )
      return (response.data.data || []) as TrendPoint[]
    } catch (error: any) {
      console.error('获取评分趋势失败:', error)
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
      for (;;) {
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
