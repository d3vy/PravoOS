import apiClient from './client'
import type { AiResponseDto, AiStatsResponse } from '../types'

export const aiStatsApi = {
  getStats: async (): Promise<AiStatsResponse> => {
    const response = await apiClient.get<AiStatsResponse>('/api/ai/admin/ai-stats')
    return response.data
  },

  getRecentResponses: async (): Promise<AiResponseDto[]> => {
    const response = await apiClient.get<AiResponseDto[]>('/api/ai/admin/ai-responses')
    return response.data
  },
}
