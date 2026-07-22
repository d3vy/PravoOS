import apiClient from './client'
import type {
  CreateTabularReviewRequest,
  TabularReviewDto,
  TabularReviewSummaryDto,
} from '../types'

export type ReviewExportFormat = 'xlsx' | 'docx'

export const reviewApi = {
  listByCase: async (caseId: string): Promise<TabularReviewSummaryDto[]> => {
    const response = await apiClient.get<TabularReviewSummaryDto[]>('/api/ai/tabular-reviews', {
      params: { caseId },
    })
    return response.data
  },

  get: async (reviewId: string): Promise<TabularReviewDto> => {
    const response = await apiClient.get<TabularReviewDto>(`/api/ai/tabular-reviews/${reviewId}`)
    return response.data
  },

  create: async (request: CreateTabularReviewRequest): Promise<TabularReviewDto> => {
    const response = await apiClient.post<TabularReviewDto>('/api/ai/tabular-reviews', request)
    return response.data
  },

  remove: async (reviewId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/tabular-reviews/${reviewId}`)
  },

  download: async (reviewId: string, title: string, format: ReviewExportFormat): Promise<void> => {
    const response = await apiClient.get<Blob>(`/api/ai/tabular-reviews/${reviewId}/export`, {
      params: { format },
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `${title}.${format}`
    link.click()
    window.URL.revokeObjectURL(url)
  },
}
