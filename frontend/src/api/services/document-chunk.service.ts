import axios from 'axios'
import { API_CONFIG, AUTH_CONFIG } from '../config'
import type {
  DocumentChunk,
  DocumentChunkPageResponse,
  DocumentChunkSearchParams,
  DocumentChunkCreateParams,
  DocumentChunkUpdateParams,
  DocumentChunkApiResponse,
  DocumentChunkListResponse,
  DocumentChunkDetailResponse
} from '../types/document-chunk.types'

class DocumentChunkService {
  private baseURL = API_CONFIG.BASE_URL

  // 获取文档分块列表（分页）
  async getDocumentChunks(current = 1, size = 10): Promise<DocumentChunkListResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get<DocumentChunkApiResponse<DocumentChunkPageResponse>>(
        `${this.baseURL}/document-chunks`,
        {
          params: { current, size },
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error('获取文档分块列表失败:', error)
      return {
        success: false,
        message: error.response?.data?.message || '获取文档分块列表失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 根据文档ID获取分块列表
  async getChunksByDocument(documentId: string, current = 1, size = 100): Promise<DocumentChunkListResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get<DocumentChunkApiResponse<DocumentChunkPageResponse>>(
        `${this.baseURL}/document-chunks/by-document/${documentId}`,
        {
          params: { current, size },
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error(`获取文档分块失败 (文档ID: ${documentId}):`, error)
      return {
        success: false,
        message: error.response?.data?.message || '获取文档分块失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 搜索文档分块
  async searchDocumentChunks(params: DocumentChunkSearchParams): Promise<DocumentChunkListResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get<DocumentChunkApiResponse<DocumentChunkPageResponse>>(
        `${this.baseURL}/document-chunks/search`,
        {
          params: {
            keyword: params.keyword,
            documentId: params.documentId,
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
      console.error('搜索文档分块失败:', error)
      return {
        success: false,
        message: error.response?.data?.message || '搜索文档分块失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 获取文档分块详情
  async getDocumentChunk(id: string): Promise<DocumentChunkDetailResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.get<DocumentChunkApiResponse<DocumentChunk>>(
        `${this.baseURL}/document-chunks/${id}`,
        {
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error(`获取文档分块详情失败 (ID: ${id}):`, error)
      return {
        success: false,
        message: error.response?.data?.message || '获取文档分块详情失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 创建文档分块
  async createDocumentChunk(params: DocumentChunkCreateParams): Promise<DocumentChunkDetailResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.post<DocumentChunkApiResponse<DocumentChunk>>(
        `${this.baseURL}/document-chunks`,
        params,
        {
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error('创建文档分块失败:', error)
      return {
        success: false,
        message: error.response?.data?.message || '创建文档分块失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 更新文档分块
  async updateDocumentChunk(id: string, params: DocumentChunkUpdateParams): Promise<DocumentChunkDetailResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.put<DocumentChunkApiResponse<DocumentChunk>>(
        `${this.baseURL}/document-chunks/${id}`,
        params,
        {
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error(`更新文档分块失败 (ID: ${id}):`, error)
      return {
        success: false,
        message: error.response?.data?.message || '更新文档分块失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }

  // 删除文档分块
  async deleteDocumentChunk(id: string): Promise<DocumentChunkApiResponse> {
    try {
      const token = localStorage.getItem(AUTH_CONFIG.TOKEN_KEY)
      const response = await axios.delete<DocumentChunkApiResponse>(
        `${this.baseURL}/document-chunks/${id}`,
        {
          headers: {
            Authorization: token ? `${AUTH_CONFIG.TOKEN_PREFIX}${token}` : undefined
          }
        }
      )
      return response.data
    } catch (error: any) {
      console.error(`删除文档分块失败 (ID: ${id}):`, error)
      return {
        success: false,
        message: error.response?.data?.message || '删除文档分块失败',
        data: undefined,
        code: error.response?.status?.toString() || '500',
        timestamp: Date.now()
      }
    }
  }
}

export const documentChunkService = new DocumentChunkService()