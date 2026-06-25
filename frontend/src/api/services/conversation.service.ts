import axios from 'axios'
import { API_CONFIG } from '../config'
import type {
  Conversation,
  Message,
  CreateConversationRequest,
  CreateMessageRequest,
  ConversationListResponse,
  ConversationResponse,
  MessageListResponse,
  MessageResponse,
  TrashListResponse,
  RestoreResponse
} from '../types/qa.types'
import { authService } from './auth.service'

class ConversationService {
  private baseURL = API_CONFIG.BASE_URL

  // 获取认证头
  private getAuthHeaders() {
    const token = authService.getToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  }

  // 获取对话列表
  async getConversations(): Promise<Conversation[]> {
    try {
      const response = await axios.get<ConversationListResponse>(
        `${this.baseURL}/conversations`,
        {
          headers: this.getAuthHeaders()
        }
      )

      if (response.data.success && response.data.data) {
        // 后端返回分页数据，提取records数组
        const pageData = response.data.data as any
        if (pageData.records && Array.isArray(pageData.records)) {
          return pageData.records as Conversation[]
        }
        return response.data.data as Conversation[]
      } else {
        throw new Error(response.data.message || '获取对话列表失败')
      }
    } catch (error: any) {
      console.error('获取对话列表失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 创建对话
  async createConversation(request: CreateConversationRequest): Promise<Conversation> {
    try {
      const response = await axios.post<ConversationResponse>(
        `${this.baseURL}/conversations`,
        request,
        {
          headers: {
            'Content-Type': 'application/json',
            ...this.getAuthHeaders()
          }
        }
      )

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '创建对话失败')
      }
    } catch (error: any) {
      console.error('创建对话失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 获取单个对话
  async getConversation(conversationId: string): Promise<Conversation> {
    try {
      const response = await axios.get<ConversationResponse>(
        `${this.baseURL}/conversations/${conversationId}`,
        {
          headers: this.getAuthHeaders()
        }
      )

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '获取对话失败')
      }
    } catch (error: any) {
      console.error('获取对话失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 更新对话标题
  async updateConversationTitle(conversationId: string, title: string): Promise<Conversation> {
    try {
      const response = await axios.put<ConversationResponse>(
        `${this.baseURL}/conversations/${conversationId}`,
        { title },
        {
          headers: {
            'Content-Type': 'application/json',
            ...this.getAuthHeaders()
          }
        }
      )

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '更新对话标题失败')
      }
    } catch (error: any) {
      console.error('更新对话标题失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 删除对话
  async deleteConversation(conversationId: string): Promise<boolean> {
    try {
      const response = await axios.delete<ConversationResponse>(
        `${this.baseURL}/conversations/${conversationId}`,
        {
          headers: this.getAuthHeaders()
        }
      )

      return response.data.success
    } catch (error: any) {
      console.error('删除对话失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 导出对话（PDF）
  async exportConversation(conversationId: string): Promise<Blob> {
    try {
      const response = await axios.get(
        `${this.baseURL}/conversations/${conversationId}/export`,
        {
          headers: this.getAuthHeaders(),
          responseType: 'blob'
        }
      )

      return response.data
    } catch (error: any) {
      console.error('导出对话失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 获取对话消息列表
  async getMessages(conversationId: string): Promise<Message[]> {
    try {
      const response = await axios.get<MessageListResponse>(
        `${this.baseURL}/messages/by-conversation/${conversationId}`,
        {
          headers: this.getAuthHeaders()
        }
      )

      if (response.data.success && response.data.data) {
        // 后端返回分页数据，提取records数组
        const pageData = response.data.data as any
        if (pageData.records && Array.isArray(pageData.records)) {
          return pageData.records as Message[]
        }
        return response.data.data as Message[]
      } else {
        throw new Error(response.data.message || '获取消息列表失败')
      }
    } catch (error: any) {
      console.error('获取消息列表失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 创建消息
  async createMessage(request: CreateMessageRequest): Promise<Message> {
    try {
      const response = await axios.post<MessageResponse>(
        `${this.baseURL}/messages`,
        request,
        {
          headers: {
            'Content-Type': 'application/json',
            ...this.getAuthHeaders()
          }
        }
      )

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '创建消息失败')
      }
    } catch (error: any) {
      console.error('创建消息失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 删除消息
  async deleteMessage(messageId: string): Promise<boolean> {
    try {
      const response = await axios.delete<MessageResponse>(
        `${this.baseURL}/messages/${messageId}`,
        {
          headers: this.getAuthHeaders()
        }
      )

      return response.data.success
    } catch (error: any) {
      console.error('删除消息失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 获取回收站中的已删除对话
  async getDeletedConversations(userId?: number): Promise<Conversation[]> {
    try {
      const response = await axios.get<TrashListResponse>(
        `${this.baseURL}/conversations/trash`,
        {
          params: userId ? { userId } : {},
          headers: this.getAuthHeaders()
        }
      )

      if (response.data.success && response.data.data) {
        return response.data.data
      } else {
        throw new Error(response.data.message || '获取回收站列表失败')
      }
    } catch (error: any) {
      console.error('获取回收站列表失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }

  // 从回收站恢复对话
  async restoreConversation(conversationId: string): Promise<boolean> {
    try {
      const response = await axios.put<RestoreResponse>(
        `${this.baseURL}/conversations/${conversationId}/restore`,
        {},
        {
          headers: this.getAuthHeaders()
        }
      )

      return response.data.success
    } catch (error: any) {
      console.error('恢复对话失败:', error)
      throw new Error(error.response?.data?.message || error.message || '网络错误')
    }
  }
}

export const conversationService = new ConversationService()