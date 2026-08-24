import request from '../request'
import type {
  DocumentChunk,
  DocumentChunkPageResponse,
  DocumentChunkSearchParams,
  DocumentChunkCreateParams,
  DocumentChunkUpdateParams
} from '../types/document-chunk'

class DocumentChunkService {
  // 全部方法 silent：失败由拦截器 reject，交由调用方 try/catch 处理（弹窗或降级），避免全局弹窗刷屏
  // 获取文档分块列表（分页）
  async getDocumentChunks(current = 1, size = 10): Promise<DocumentChunkPageResponse> {
    const response = await request.get<{ data: DocumentChunkPageResponse }>('/document-chunks', {
      params: { current, size },
      silent: true
    })
    return response.data.data
  }

  // 根据文档ID获取分块列表
  async getChunksByDocument(documentId: string, current = 1, size = 100): Promise<DocumentChunkPageResponse> {
    const response = await request.get<{ data: DocumentChunkPageResponse }>(
      `/document-chunks/by-document/${documentId}`,
      {
        params: { current, size },
        silent: true
      }
    )
    return response.data.data
  }

  // 搜索文档分块
  async searchDocumentChunks(params: DocumentChunkSearchParams): Promise<DocumentChunkPageResponse> {
    const response = await request.get<{ data: DocumentChunkPageResponse }>('/document-chunks/search', {
      params: {
        keyword: params.keyword,
        documentId: params.documentId,
        current: params.current || 1,
        size: params.size || 10
      },
      silent: true
    })
    return response.data.data
  }

  // 获取文档分块详情
  async getDocumentChunk(id: string): Promise<DocumentChunk> {
    const response = await request.get<{ data: DocumentChunk }>(`/document-chunks/${id}`, { silent: true })
    return response.data.data
  }

  // 创建文档分块
  async createDocumentChunk(params: DocumentChunkCreateParams): Promise<DocumentChunk> {
    const response = await request.post<{ data: DocumentChunk }>('/document-chunks', params, { silent: true })
    return response.data.data
  }

  // 更新文档分块
  async updateDocumentChunk(id: string, params: DocumentChunkUpdateParams): Promise<DocumentChunk> {
    const response = await request.put<{ data: DocumentChunk }>(`/document-chunks/${id}`, params, { silent: true })
    return response.data.data
  }

  // 删除文档分块
  async deleteDocumentChunk(id: string): Promise<void> {
    await request.delete(`/document-chunks/${id}`, { silent: true })
  }
}

export const documentChunkService = new DocumentChunkService()
