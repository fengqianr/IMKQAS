import type { ApiResponse } from './auth.types'
import type { AnswerOption } from './interview.types'

// QA问答请求类型
export interface QaAskRequest {
  question: string
  userId?: number
  conversationId?: string
  options?: QaOptions
}

export interface QaOptions {
  temperature?: number
  topP?: number
  maxTokens?: number
  includeSources?: boolean
  stream?: boolean
}

// 检索步骤（匹配后端 RetrievalStepDto）
export interface RetrievalStep {
  stepName: string
  stepOrder: number
  durationMs: number
  inputCount: number
  outputCount: number
  intermediateData?: Record<string, any>
  status: string
  timestamp?: number
}

// 检索路径（匹配后端 RetrievalPathDto）
export interface RetrievalPath {
  steps: RetrievalStep[]
  totalDurationMs: number
  cacheHit: boolean
  intentType: string
}

// QA问答响应类型
export interface QaResponse {
  query: string
  answer: string
  retrievedContext: string[]
  confidence: number
  processingTime: number
  modelUsed: string
  citations?: SourceCitation[]
  intentType?: string
  questionnaireSuggestion?: any
  retrievalPath?: RetrievalPath
}

// 参考文献引用（匹配后端 SourceCitation）
export interface SourceCitation {
  documentId: string
  chunkId: string
  title: string
  snippet: string
  relevanceScore: number
  positionInAnswer: number
}

// 保留向后兼容的 SourceReference 别名
export type SourceReference = SourceCitation

// 流式问答响应块
export interface QaStreamChunk {
  type: 'text' | 'sources' | 'retrievalPath' | 'done' | 'error'
        | 'question' | 'completion' | 'safety_alert' | 'progress' | 'clarify' | 'degradation'
  content?: string
  sources?: SourceReference[]
  conversationId?: string
  error?: string
  retrievalPath?: RetrievalPath
  // done 事件字段
  answer?: string
  intentType?: string
  questionnaireSuggestion?: {
    matched: boolean
    confidence: number
    suggestionText: string
    questionnaireId?: string
    questionnaireTitle?: string
  }
  // 问卷相关字段
  linkId?: string
  text?: string
  currentIndex?: number
  totalQuestions?: number
  progress?: string
  options?: AnswerOption[]
  totalScore?: number
  maxScore?: number
  severity?: string
  interpretation?: string
  message?: string
  reason?: string
  percent?: number
  level?: string
}

// 问卷问题数据块
export interface QuestionChunk {
  type: 'question'
  linkId: string
  text: string
  currentIndex: number
  totalQuestions: number
  progress: string
  options?: AnswerOption[]
}

// 问卷完成数据块
export interface CompletionChunk {
  type: 'completion'
  message: string
  totalScore: number
  maxScore: number
  severity: string
  interpretation: string
}

// 安全警报数据块
export interface SafetyAlertChunk {
  type: 'safety_alert'
  reason: string
  message: string
}

// 科室导诊请求
export interface TriageRequest {
  symptoms: string
  patientAge?: number
  patientGender?: string
  duration?: string
  severity?: string
}

// 科室导诊响应
export interface TriageResponse {
  recommendedDepartment: string
  confidence: number
  reasons: string[]
  alternativeDepartments?: string[]
  suggestedTests?: string[]
  emergencyLevel?: 'normal' | 'urgent' | 'critical'
}

// 药物查询请求
export interface DrugQueryRequest {
  name: string
  brandName?: string
  genericName?: string
}

// 药物查询响应
export interface DrugResponse {
  drugName: string
  genericName?: string
  brandNames?: string[]
  indications: string[]
  dosage: string
  contraindications: string[]
  sideEffects: string[]
  interactions: string[]
  warnings: string[]
  references: string[]
}

// RAG统计信息
export interface RagStats {
  totalDocuments: number
  totalChunks: number
  totalEmbeddings: number
  processingQueue: number
  recentActivity: RagActivity[]
  storageUsage: {
    documents: number
    vectors: number
    total: number
  }
}

export interface RagActivity {
  timestamp: string
  action: 'upload' | 'process' | 'query' | 'delete'
  documentTitle?: string
  success: boolean
}

// 文档处理请求
export interface DocumentProcessRequest {
  file: File
  title: string
  category: string
  tags?: string[]
}

// 文档处理响应
export interface DocumentProcessResponse {
  documentId: string
  title: string
  category: string
  status: 'processing' | 'completed' | 'failed'
  chunksProcessed: number
  embeddingGenerated: number
  message?: string
}

// 对话相关类型
export interface Conversation {
  id: string
  userId: number
  title: string
  type: 'general' | 'diagnosis' | 'consultation'
  createdAt: string
  updatedAt: string
  messageCount: number
}

export interface Message {
  id: string
  conversationId: string
  role: 'user' | 'assistant'
  content: string
  sourceReferences?: string
  createdAt: string
  updatedAt: string
}

export interface CreateConversationRequest {
  title?: string
  type?: 'general' | 'diagnosis' | 'consultation'
}

export interface CreateMessageRequest {
  conversationId: string
  content: string
  role: 'user' | 'assistant'
  sourceReferences?: string
}

// API响应包装器
export type QaAskResponse = ApiResponse<QaResponse>
export type TriageResponseWrapper = ApiResponse<TriageResponse>
export type DrugQueryResponse = ApiResponse<DrugResponse>
export type RagStatsResponse = ApiResponse<RagStats>
export type ConversationListResponse = ApiResponse<Conversation[]>
export type ConversationResponse = ApiResponse<Conversation>
export type MessageListResponse = ApiResponse<Message[]>
export type MessageResponse = ApiResponse<Message>
export type DocumentProcessResponseWrapper = ApiResponse<DocumentProcessResponse>
export type TrashListResponse = ApiResponse<Conversation[]>
export type RestoreResponse = ApiResponse<void>