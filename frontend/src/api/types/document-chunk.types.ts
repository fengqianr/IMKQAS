// 文档分块相关类型定义
import type { ApiResponse } from './auth.types'

// 文档分块实体
export interface DocumentChunk {
  id: string              // 使用字符串类型存储大整数ID，避免JavaScript精度损失
  documentId: string      // 文档ID，使用字符串类型存储大整数ID
  chunkIndex: number        // 分块序号
  content: string          // 分块文本内容
  metadata: string         // 元数据: {page: 页码, section: 章节, start_char: 起始字符, end_char: 结束字符}
  vectorId: string         // Milvus向量ID
  createdAt: string        // ISO 8601格式时间字符串
  updatedAt: string        // ISO 8601格式时间字符串
  deleted: number          // 逻辑删除标记
}

// 文档分块分页响应
export interface DocumentChunkPageResponse {
  data: DocumentChunk[]
  total: number
  page: number
  size: number
  totalPages: number
}

// 文档分块搜索请求参数
export interface DocumentChunkSearchParams {
  keyword?: string
  documentId?: string      // 文档ID，使用字符串类型存储大整数ID
  current?: number
  size?: number
}

// 创建文档分块请求参数
export interface DocumentChunkCreateParams {
  documentId: string      // 文档ID，使用字符串类型存储大整数ID
  chunkIndex: number
  content: string
  metadata?: string
  vectorId?: string
}

// 更新文档分块请求参数
export interface DocumentChunkUpdateParams {
  content?: string
  metadata?: string
  vectorId?: string
}

// API响应类型别名
export type DocumentChunkApiResponse<T = any> = ApiResponse<T>
export type DocumentChunkListResponse = DocumentChunkApiResponse<DocumentChunkPageResponse>
export type DocumentChunkDetailResponse = DocumentChunkApiResponse<DocumentChunk>