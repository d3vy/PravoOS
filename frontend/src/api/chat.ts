import apiClient from './client'
import type { ChatRequest, ChatResponse, ConversationResponse, MessageResponse } from '../types'

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
}
