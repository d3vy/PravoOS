import apiClient from './client'
import type {
  CreateSavedViewRequest,
  SavedViewResponse,
  SavedViewScope,
  UpdateSavedViewRequest,
} from '../types'

export const savedViewsApi = {
  list: async (scope: SavedViewScope): Promise<SavedViewResponse[]> => {
    const response = await apiClient.get<SavedViewResponse[]>('/api/ai/saved-views', { params: { scope } })
    return response.data
  },

  create: async (data: CreateSavedViewRequest): Promise<SavedViewResponse> => {
    const response = await apiClient.post<SavedViewResponse>('/api/ai/saved-views', data)
    return response.data
  },

  update: async (viewId: string, data: UpdateSavedViewRequest): Promise<SavedViewResponse> => {
    const response = await apiClient.put<SavedViewResponse>(`/api/ai/saved-views/${viewId}`, data)
    return response.data
  },

  delete: async (viewId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/saved-views/${viewId}`)
  },
}
