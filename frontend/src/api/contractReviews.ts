import apiClient from './client'
import type { ContractReviewDto } from '../types'

export const contractReviewsApi = {
  listByCase: async (caseId: string): Promise<ContractReviewDto[]> => {
    const response = await apiClient.get<ContractReviewDto[]>('/api/ai/contract-reviews', {
      params: { caseId },
    })
    return response.data
  },

  create: async (documentId: string): Promise<ContractReviewDto> => {
    const response = await apiClient.post<ContractReviewDto>('/api/ai/contract-reviews', { documentId })
    return response.data
  },
}
