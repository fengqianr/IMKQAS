import request from '../request'
import { DocumentStatus } from '../types/document'
import type {
  Document,
  DocumentPageResponse,
  DocumentSearchParams,
  DocumentUploadParams,
  DocumentProcessResponse
} from '../types/document'

class DocumentService {
  // 获取文档列表（分页）
  // 失败由拦截器统一弹窗/reject；页面自动加载读操作建议传 { silent: true }，由视图呈现错误态
  async getDocuments(current = 1, size = 10, options?: { silent?: boolean }): Promise<DocumentPageResponse> {
    const response = await request.get<{ data: DocumentPageResponse }>('/documents', {
      params: { current, size },
      silent: options?.silent
    })
    return response.data.data
  }

  // 搜索文档
  async searchDocuments(params: DocumentSearchParams): Promise<DocumentPageResponse> {
    const response = await request.get<{ data: DocumentPageResponse }>('/documents/search', {
      params: {
        keyword: params.keyword,
        category: params.category,
        status: params.status,
        current: params.current || 1,
        size: params.size || 10
      }
    })
    return response.data.data
  }

  // 获取文档详情
  async getDocument(id: string): Promise<Document> {
    const response = await request.get<{ data: Document }>(`/documents/${id}`)
    return response.data.data
  }

  // 上传并处理文档
  async uploadDocument(params: DocumentUploadParams): Promise<DocumentProcessResponse> {
    // 创建FormData
    const formData = new FormData()
    formData.append('file', params.file)
    if (params.title) {
      formData.append('title', params.title)
    }
    if (params.category) {
      formData.append('category', params.category)
    }

    const response = await request.post<{ data: DocumentProcessResponse }>('/rag/process-document', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    return response.data.data
  }

  // 删除文档（成功静默返回；失败 reject 由调用方提示）
  async deleteDocument(id: string): Promise<void> {
    await request.delete(`/documents/${id}`)
  }

  // 更新文档
  async updateDocument(id: string, document: Partial<Document>): Promise<Document> {
    const response = await request.put<{ data: Document }>(`/documents/${id}`, document)
    return response.data.data
  }

  // 获取文档分类列表（从现有文档中提取）
  async getCategories(): Promise<string[]> {
    const page = await this.getDocuments(1, 1000, { silent: true })
    const categories = new Set<string>()
    page.data.forEach((doc: Document) => {
      if (doc.category) {
        categories.add(doc.category)
      }
    })
    return Array.from(categories)
  }

  // 获取文档统计信息
  async getDocumentStats(): Promise<{
    total: number
    byStatus: Record<DocumentStatus, number>
    byCategory: Record<string, number>
  }> {
    try {
      const page = await this.getDocuments(1, 1000, { silent: true })
      const documents = page.data
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
    } catch (error) {
      // 仪表板统计为后台展示，加载失败静默降级为 0
      console.error('获取文档统计失败:', error)
      return { total: 0, byStatus: {} as Record<DocumentStatus, number>, byCategory: {} }
    }
  }

  // 获取文档预览URL
  getPreviewUrl(id: string): string {
    return `${request.defaults.baseURL || ''}/documents/${id}/preview`
  }

  // 获取文档预览文件内容（用于PDF等二进制格式）
  async getPreviewBlob(id: string): Promise<Blob | null> {
    try {
      const response = await request.get(`/documents/${id}/preview`, {
        responseType: 'blob',
        silent: true
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
      const response = await request.get(`/documents/${id}/preview/text`, {
        responseType: 'text',
        silent: true
      })
      return response.data
    } catch (error: any) {
      console.error(`获取文档文本预览失败 (ID: ${id}):`, error)
      return null
    }
  }

  // 重新处理文档（触发分块处理）
  async reprocessDocument(documentId: string): Promise<DocumentProcessResponse> {
    const response = await request.post<{ data: DocumentProcessResponse }>(`/rag/chunk-document/${documentId}`, {})
    return response.data.data
  }
}

export const documentService = new DocumentService()
