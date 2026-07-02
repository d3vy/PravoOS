import apiClient from './client'
import type { CitationCheckResult } from '../types'

export const citationsApi = {
  checkResponse: async (responseId: string): Promise<CitationCheckResult> => {
    const response = await apiClient.post<CitationCheckResult>(`/api/ai/citation-checks/responses/${responseId}`)
    return response.data
  },

  checkText: async (text: string): Promise<CitationCheckResult> => {
    const response = await apiClient.post<CitationCheckResult>('/api/ai/citation-checks', { text })
    return response.data
  },
}
