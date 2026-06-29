// 问卷采集相关 TypeScript 类型

// ==================== 问卷模板 ====================

export interface AnswerOption {
  code: string
  display: string
  score: number
}

export interface QuestionItem {
  index: number
  linkId: string
  text: string
  options: AnswerOption[]
}

export interface ScoreRange {
  minScore: number
  maxScore: number
  severity: string
  interpretation: string
}

export interface ScoringRule {
  minScore: number
  maxScore: number
  ranges: ScoreRange[]
}

export interface QuestionnaireTemplate {
  id: string
  title: string
  description: string
  category: string
  codeSystem?: string
  triggerKeywords: string[]
  items: QuestionItem[]
  scoringRule: ScoringRule
}

// ==================== 面试会话 ====================

export interface InterviewSession {
  sessionId: string
  questionnaireId: string
  questionnaireTitle: string
  userId: number | null
  conversationId: number | null
  currentQuestionIndex: number
  totalQuestions: number
  answers: Record<string, string>
  currentScore: number
  completed: boolean
  collectionMode: 'llm_driven' | 'manual_form'
  degradationLevel: 'llm' | 'rule_parser' | 'manual_form'
  progressPercent: number
}

// ==================== 问卷建议 ====================

export interface InterviewSuggestion {
  matched: boolean
  questionnaire?: QuestionnaireTemplate
  suggestionText: string
  confidence: number
}

// ==================== SSE 事件类型 ====================

export interface QuestionEvent {
  type: 'question'
  linkId: string
  text: string
  currentIndex: number
  totalQuestions: number
  progress: string
  sessionId?: string
  options?: AnswerOption[]
}

export interface CompletionEvent {
  type: 'completion'
  message: string
  totalScore: number
  maxScore: number
  severity: string
  interpretation: string
  analysisSummary?: string
  analysisId?: string
}

export interface SafetyAlertEvent {
  type: 'safety_alert'
  reason: string
  message: string
}

export interface ProgressEvent {
  type: 'progress'
  current: number
  total: number
  percent: number
}

export interface ClarifyEvent {
  type: 'clarify'
  linkId: string
  text: string
  sessionId?: string
  options?: AnswerOption[]
}

export interface DegradationEvent {
  type: 'degradation'
  level: string
  reason: string
}

export interface DoneEvent {
  type: 'done'
}

export interface ErrorEvent {
  type: 'error'
  message: string
}

export type InterviewSSEEvent =
  | QuestionEvent
  | CompletionEvent
  | SafetyAlertEvent
  | ProgressEvent
  | ClarifyEvent
  | DegradationEvent
  | DoneEvent
  | ErrorEvent

// ==================== 问卷消息（聊天中渲染） ====================

export interface QuestionnaireMessage {
  id: string
  type: 'questionnaire_question' | 'questionnaire_completion' | 'questionnaire_safety'
  sessionId: string
  // 问题消息
  question?: {
    linkId: string
    text: string
    currentIndex: number
    totalQuestions: number
    options: AnswerOption[]
  }
  // 完成消息
  completion?: {
    totalScore: number
    maxScore: number
    severity: string
    interpretation: string
    analysisSummary?: string
    message: string
  }
  // 安全警报消息
  safetyAlert?: {
    reason: string
    message: string
  }
}

// ==================== API 请求 ====================

export interface StartInterviewRequest {
  questionnaireId: string
  userId?: number
  conversationId?: string
}

export interface SubmitAnswerRequest {
  sessionId: string
  userInput: string
  selectedCode?: string
}

// ==================== 分析报告 ====================

export interface AnalysisReport {
  analysisId: string
  sessionId: string
  summary: string
  riskAssessment: {
    level: string
    description: string
    requiresUrgentAttention: boolean
  }
  detailAnalysis: {
    overview: string
    patterns: string[]
    conclusion: string
  }
  recommendations: {
    immediate: { title: string; description: string }[]
    shortTerm: { title: string; description: string }[]
    professional: { title: string; description: string; resource: string }[]
  }
  followUp: {
    suggestedDate: string
    rationale: string
  }
  disclaimer: string
  latencyMs: number
}

// ==================== 访谈消息（从MySQL加载的历史记录） ====================

export interface InterviewMessageItem {
  id: number
  conversationId: number
  sessionId: string
  messageType: 'suggestion' | 'question' | 'completion' | 'safety_alert' | 'progress' | 'clarify' | 'user_message'
  questionnaireId: string
  questionnaireTitle: string
  messageData: Record<string, any>
  createdAt: string
}

// ==================== 访谈历史记录摘要 ====================

export interface InterviewHistoryItem {
  sessionId: string
  questionnaireId: string
  questionnaireTitle: string
  collectionMode: string
  totalScore: number
  completed: boolean
  severity?: string
  hasAnalysis: boolean
  createdAt: string
  lastActiveAt: string
}
