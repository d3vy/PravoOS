import apiClient from './client'
import type { DocumentComparisonDto } from '../types'

export const documentComparisonsApi = {
  listByCase: async (caseId: string): Promise<DocumentComparisonDto[]> => {
    const response = await apiClient.get<DocumentComparisonDto[]>('/api/ai/document-comparisons', {
      params: { caseId },
    })
    return response.data
  },

  create: async (baseDocumentId: string, revisedDocumentId: string): Promise<DocumentComparisonDto> => {
    const response = await apiClient.post<DocumentComparisonDto>('/api/ai/document-comparisons', {
      baseDocumentId,
      revisedDocumentId,
    })
    return response.data
  },
}
