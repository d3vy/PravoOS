import apiClient from './client'
import type { DashboardResponse } from '../types'

export const dashboardApi = {
  get: async (): Promise<DashboardResponse> => {
    const response = await apiClient.get<DashboardResponse>('/api/ai/dashboard')
    return response.data
  },
}
