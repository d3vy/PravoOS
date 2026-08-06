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

  downloadDocx: async (comparisonId: string, fileName: string): Promise<void> => {
    const response = await apiClient.get<Blob>(
      `/api/ai/document-comparisons/${comparisonId}/export.docx`,
      { responseType: 'blob' },
    )
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    window.URL.revokeObjectURL(url)
  },
}
