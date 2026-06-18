import apiClient from './client'
import type { GlobalSearchResponse } from '../types'

export const searchApi = {
  global: async (q: string): Promise<GlobalSearchResponse> => {
    const response = await apiClient.get<GlobalSearchResponse>('/api/ai/search', {
      params: { q },
    })
    return response.data
  },
}
