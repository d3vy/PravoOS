import apiClient from './client'
import type { AiResponseDto, RateRequest, WorkflowInfo } from '../types'

export const workflowsApi = {
  getAll: async (): Promise<WorkflowInfo[]> => {
    const response = await apiClient.get<WorkflowInfo[]>('/api/ai/workflows')
    return response.data
  },

  rateResponse: async (responseId: string, data: RateRequest): Promise<AiResponseDto> => {
    const response = await apiClient.post<AiResponseDto>(`/api/ai/responses/${responseId}/rate`, data)
    return response.data
  },
}
