import axios from 'axios'
import { API_CONFIG, AUTH_CONFIG } from '../config'
import type {
  Document,
  DocumentPageResponse,
  DocumentSearchParams,
  DocumentUploadParams,
  DocumentProcessResponse,
  DocumentApiResponse,
  DocumentListResponse,
  DocumentDetailResponse,
  DocumentProcessApiResponse
} from '../types/document.types'
import { DocumentStatus } from '../types/document.types'

class DocumentService {
  private baseURL = API_CONFIG.BASE_URL

  // 获取文档列表（分页）
  async getDocuments(current = 1, size = 10): Promise<DocumentListResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get<DocumentApiResponse<DocumentPageResponse>>(
        `${this.baseURL}/documents`,
        {
          params: { current, size },
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error('获取文档列表失败:', error)
      return {
        success: false,
        message: error.response?.data?.message || '获取文档列表失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 搜索文档
  async searchDocuments(params: DocumentSearchParams): Promise<DocumentListResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get<DocumentApiResponse<DocumentPageResponse>>(
        `${this.baseURL}/documents/search`,
        {
          params: {
            keyword: params.keyword,
            category: params.category,
            status: params.status,
            current: params.current || 1,
            size: params.size || 10
          },
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error('搜索文档失败:', error)
      return {
        success: false,
        message: error.response?.data?.message || '搜索文档失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 获取文档详情
  async getDocument(id: string): Promise<DocumentDetailResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get<DocumentApiResponse<Document>>(
        `${this.baseURL}/documents/${id}`,
        {
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error(`获取文档详情失败 (ID: ${id}):`, error)
      return {
        success: false,
        message: error.response?.data?.message || '获取文档详情失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 上传并处理文档
  async uploadDocument(params: DocumentUploadParams): Promise<DocumentProcessApiResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)

      // 创建FormData
      const formData = new FormData()
      formData.append('file', params.file)
      if (params.title) {
        formData.append('title', params.title)
      }
      if (params.category) {
        formData.append('category', params.category)
      }

      const response = await axios.post<DocumentApiResponse<DocumentProcessResponse>>(
        `${this.baseURL}/rag/process-document`,
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error('上传文档失败:', error)
      return {
        success: false,
        message: error.response?.data?.message || '上传文档失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 删除文档
  async deleteDocument(id: string): Promise<DocumentApiResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.delete<DocumentApiResponse>(
        `${this.baseURL}/documents/${id}`,
        {
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error(`删除文档失败 (ID: ${id}):`, error)
      return {
        success: false,
        message: error.response?.data?.message || '删除文档失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 更新文档
  async updateDocument(id: string, document: Partial<Document>): Promise<DocumentDetailResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.put<DocumentApiResponse<Document>>(
        `${this.baseURL}/documents/${id}`,
        document,
        {
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error(`更新文档失败 (ID: ${id}):`, error)
      return {
        success: false,
        message: error.response?.data?.message || '更新文档失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 获取文档分类列表（从现有文档中提取）
  async getCategories(): Promise<string[]> {
    try {
      const response = await this.getDocuments(1, 1000)
      if (response.success && response.data) {
        const categories = new Set<string>()
        response.data.data.forEach((doc: Document) => {
          if (doc.category) {
            categories.add(doc.category)
          }
        })
        return Array.from(categories)
      }
      return []
    } catch (error) {
      console.error('获取分类列表失败:', error)
      return []
    }
  }

  // 获取文档统计信息
  async getDocumentStats(): Promise<{ total: number, byStatus: Record<DocumentStatus, number>, byCategory: Record<string, number> }> {
    try {
      const response = await this.getDocuments(1, 1000)
      if (response.success && response.data) {
        const documents = response.data.data
        const total = documents.length
        const byStatus: Record<DocumentStatus, number> = {
          [DocumentStatus.UPLOADED]: 0,
          [DocumentStatus.PROCESSING]: 0,
          [DocumentStatus.COMPLETED]: 0,
          [DocumentStatus.FAILED]: 0
        }
        const byCategory: Record<string, number> = {}

        documents.forEach((doc: Document) => {
          byStatus[doc.status] = (byStatus[doc.status] || 0) + 1
          if (doc.category) {
            byCategory[doc.category] = (byCategory[doc.category] || 0) + 1
          }
        })

        return { total, byStatus, byCategory }
      }
      return { total: 0, byStatus: {} as Record<DocumentStatus, number>, byCategory: {} }
    } catch (error) {
      console.error('获取文档统计失败:', error)
      return { total: 0, byStatus: {} as Record<DocumentStatus, number>, byCategory: {} }
    }
  }

  // 获取文档预览URL
  getPreviewUrl(id: string): string {
    return `${this.baseURL}/documents/${id}/preview`
  }

  // 获取文档预览文件内容（用于PDF等二进制格式）
  async getPreviewBlob(id: string): Promise<Blob | null> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get(`${this.baseURL}/documents/${id}/preview`, {
        responseType: 'blob',
        headers: {
          Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
        }
      })
      return response.data
    } catch (error: any) {
      console.error(`获取文档预览失败 (ID: ${id}):`, error)
      return null
    }
  }

  // 获取文档文本预览内容（用于非PDF格式的文本展示）
  async getPreviewText(id: string): Promise<string | null> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get(`${this.baseURL}/documents/${id}/preview/text`, {
        responseType: 'text',
        headers: {
          Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
        }
      })
      return response.data
    } catch (error: any) {
      console.error(`获取文档文本预览失败 (ID: ${id}):`, error)
      return null
    }
  }

  // 重新处理文档（触发分块处理）
  async reprocessDocument(documentId: string): Promise<DocumentProcessApiResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.post<DocumentApiResponse<DocumentProcessResponse>>(
        `${this.baseURL}/rag/chunk-document/${documentId}`,
        {},
        {
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error(`重新处理文档失败 (ID: ${documentId}):`, error)
      return {
        success: false,
        message: error.response?.data?.message || '重新处理文档失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }
}

export const documentService = new DocumentService()