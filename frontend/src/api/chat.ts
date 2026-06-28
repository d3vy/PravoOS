import apiClient from './client'
import { MAX_PAGE_SIZE } from './pagination'
import type { ChatRequest, ChatResponse, ConversationResponse, MessageResponse, RateRequest } from '../types'

export const chatApi = {
  sendMessage: async (data: ChatRequest): Promise<ChatResponse> => {
    const response = await apiClient.post<ChatResponse>('/api/ai/chat', data)
    return response.data
  },

  getConversations: async (q?: string): Promise<ConversationResponse[]> => {
    const response = await apiClient.get<ConversationResponse[]>('/api/ai/conversations', {
      params: { ...(q ? { q } : {}), page: 0, size: MAX_PAGE_SIZE },
    })
    return response.data
  },

  getMessages: async (conversationId: string): Promise<MessageResponse[]> => {
    const response = await apiClient.get<MessageResponse[]>(
      `/api/ai/conversations/${conversationId}/messages`,
      { params: { page: 0, size: MAX_PAGE_SIZE } }
    )
    return response.data
  },

  rateMessage: async (messageId: string, data: RateRequest): Promise<MessageResponse> => {
    const response = await apiClient.post<MessageResponse>(`/api/ai/messages/${messageId}/rate`, data)
    return response.data
  },
}
