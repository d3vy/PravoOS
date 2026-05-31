import apiClient from './client'
import type { ChatRequest, ChatResponse, ConversationResponse, MessageResponse, RateRequest } from '../types'

export const chatApi = {
  sendMessage: async (data: ChatRequest): Promise<ChatResponse> => {
    const response = await apiClient.post<ChatResponse>('/api/ai/chat', data)
    return response.data
  },

  getConversations: async (): Promise<ConversationResponse[]> => {
    const response = await apiClient.get<ConversationResponse[]>('/api/ai/conversations')
    return response.data
  },

  getMessages: async (conversationId: string): Promise<MessageResponse[]> => {
    const response = await apiClient.get<MessageResponse[]>(
      `/api/ai/conversations/${conversationId}/messages`
    )
    return response.data
  },

  rateMessage: async (messageId: string, data: RateRequest): Promise<MessageResponse> => {
    const response = await apiClient.post<MessageResponse>(`/api/ai/messages/${messageId}/rate`, data)
    return response.data
  },
}
