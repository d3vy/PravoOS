import apiClient from './client'
import type { ApplicationResponse, LawyerResponse } from '../types'

export const adminApi = {
  getAllApplications: async (): Promise<ApplicationResponse[]> => {
    const response = await apiClient.get<ApplicationResponse[]>('/api/admin/applications')
    return response.data
  },

  getPendingApplications: async (): Promise<ApplicationResponse[]> => {
    const response = await apiClient.get<ApplicationResponse[]>('/api/admin/applications/pending')
    return response.data
  },

  approveApplication: async (id: string): Promise<void> => {
    await apiClient.post(`/api/admin/applications/${id}/approve`)
  },

  rejectApplication: async (id: string): Promise<void> => {
    await apiClient.post(`/api/admin/applications/${id}/reject`)
  },

  getLawyers: async (): Promise<LawyerResponse[]> => {
    const response = await apiClient.get<LawyerResponse[]>('/api/admin/users/lawyers')
    return response.data
  },
}
