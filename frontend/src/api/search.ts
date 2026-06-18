import apiClient from './client'
import type { GlobalSearchResponse } from '../types'

export const searchApi = {
  global: async (q: string, searchContent: boolean): Promise<GlobalSearchResponse> => {
    const response = await apiClient.get<GlobalSearchResponse>('/api/ai/search', {
      params: { q, content: searchContent },
    })
    return response.data
  },
}
