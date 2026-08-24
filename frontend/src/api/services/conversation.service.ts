import request from '../request'
import type {
  Conversation,
  Message,
  CreateConversationRequest,
  CreateMessageRequest,
  ConversationListResponse,
  MessageListResponse
} from '../types/qa'

class ConversationService {
  // 获取对话列表（传入 userId 时按用户过滤，避免不同用户的会话混在一起）
  // 全部方法 silent：失败由拦截器 reject，交由调用方 try/catch 处理（弹窗或降级），避免全局弹窗刷屏
  async getConversations(userId?: string | number): Promise<Conversation[]> {
    // 按用户查询时复用后端已存在的 /conversations/by-user/{userId}，分页拉全量
    const url = userId ? `/conversations/by-user/${userId}` : '/conversations'
    const params = userId ? { current: 1, size: 1000 } : undefined
    const response = await request.get<ConversationListResponse>(url, { params, silent: true })

    // 后端返回分页数据，提取records数组
    const pageData = response.data.data as any
    if (pageData.records && Array.isArray(pageData.records)) {
      return pageData.records as Conversation[]
    }
    return response.data.data as Conversation[]
  }

  // 创建对话
  async createConversation(payload: CreateConversationRequest): Promise<Conversation> {
    const response = await request.post<{ data: Conversation }>('/conversations', payload, { silent: true })
    return response.data.data
  }

  // 获取单个对话
  async getConversation(conversationId: string): Promise<Conversation> {
    const response = await request.get<{ data: Conversation }>(`/conversations/${conversationId}`, { silent: true })
    return response.data.data
  }

  // 更新对话标题
  async updateConversationTitle(conversationId: string, title: string): Promise<Conversation> {
    const response = await request.put<{ data: Conversation }>(`/conversations/${conversationId}`, { title }, { silent: true })
    return response.data.data
  }

  // 删除对话（成功静默返回；失败 reject 由调用方提示）
  async deleteConversation(conversationId: string): Promise<void> {
    await request.delete(`/conversations/${conversationId}`, { silent: true })
  }

  // 导出对话（PDF）
  async exportConversation(conversationId: string): Promise<Blob> {
    const response = await request.get(`/conversations/${conversationId}/export`, {
      responseType: 'blob',
      silent: true
    })
    return response.data
  }

  // 获取对话消息列表
  async getMessages(conversationId: string): Promise<Message[]> {
    const response = await request.get<MessageListResponse>(`/messages/by-conversation/${conversationId}`, { silent: true })

    // 后端返回分页数据，提取records数组
    const pageData = response.data.data as any
    if (pageData.records && Array.isArray(pageData.records)) {
      return pageData.records as Message[]
    }
    return response.data.data as Message[]
  }

  // 创建消息
  async createMessage(payload: CreateMessageRequest): Promise<Message> {
    const response = await request.post<{ data: Message }>('/messages', payload, { silent: true })
    return response.data.data
  }

  // 删除消息（成功静默返回；失败 reject 由调用方提示）
  async deleteMessage(messageId: string): Promise<void> {
    await request.delete(`/messages/${messageId}`, { silent: true })
  }

  // 获取回收站中的已删除对话
  async getDeletedConversations(userId?: string | number): Promise<Conversation[]> {
    const response = await request.get<{ data: Conversation[] }>('/conversations/trash', {
      params: userId ? { userId } : {},
      silent: true
    })
    return response.data.data
  }

  // 从回收站恢复对话（成功静默返回；失败 reject 由调用方提示）
  async restoreConversation(conversationId: string): Promise<void> {
    await request.put(`/conversations/${conversationId}/restore`, {}, { silent: true })
  }

  // 从回收站彻底删除对话（物理删除，同时级联删除其消息）
  async deleteConversationPermanently(conversationId: string): Promise<void> {
    await request.delete(`/conversations/${conversationId}/permanent`, { silent: true })
  }
}

export const conversationService = new ConversationService()
