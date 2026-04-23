// 文档相关类型定义
import type { ApiResponse } from './auth.types'

// 文档状态枚举
export enum DocumentStatus {
  UPLOADED = 'UPLOADED',    // 已上传
  PROCESSING = 'PROCESSING', // 处理中
  COMPLETED = 'COMPLETED',   // 处理完成
  FAILED = 'FAILED'          // 处理失败
}

// 文档实体
export interface Document {
  id: string  // 使用字符串类型存储大整数ID，避免JavaScript精度损失
  title: string
  category: string  // 医学分类: 内科/外科/儿科/妇科/神经科/心血管科等
  filePath: string  // 文件存储路径
  chunkCount: number // 文档分块数量
  status: DocumentStatus
  uploadedBy: number // 上传者用户ID
  createdAt: string  // ISO 8601格式时间字符串
  updatedAt: string  // ISO 8601格式时间字符串
  deleted: number    // 逻辑删除标记
}

// 文档分页响应
export interface DocumentPageResponse {
  data: Document[]
  total: number
  page: number
  size: number
  totalPages: number
}

// 文档搜索请求参数
export interface DocumentSearchParams {
  keyword?: string
  category?: string
  status?: string
  current?: number
  size?: number
}

// 文档上传请求参数
export interface DocumentUploadParams {
  file: File
  title?: string
  category?: string
}

// 文档处理响应
export interface DocumentProcessResponse {
  success: boolean
  message: string
  documentId: string  // 使用字符串类型存储大整数ID，避免JavaScript精度损失
  document: Document
  status: DocumentStatus
}

// API响应类型别名
export type DocumentApiResponse<T = any> = ApiResponse<T>
export type DocumentListResponse = DocumentApiResponse<DocumentPageResponse>
export type DocumentDetailResponse = DocumentApiResponse<Document>
export type DocumentProcessApiResponse = DocumentApiResponse<DocumentProcessResponse>