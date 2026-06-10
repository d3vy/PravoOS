import apiClient from './client'
import type { ApplicationResponse, ClientStatsResponse, LawyerProfileResponse } from '../types'

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

  approveApplicationForce: async (id: string): Promise<void> => {
    await apiClient.post(`/api/admin/applications/${id}/approve-force`)
  },

  rejectApplication: async (id: string): Promise<void> => {
    await apiClient.post(`/api/admin/applications/${id}/reject`)
  },

  getLawyers: async (): Promise<LawyerProfileResponse[]> => {
    const response = await apiClient.get<LawyerProfileResponse[]>('/api/admin/users/lawyers')
    return response.data
  },

  deleteLawyer: async (userId: string): Promise<void> => {
    await apiClient.delete(`/api/admin/users/lawyers/${userId}`)
  },

  getClientStats: async (): Promise<ClientStatsResponse> => {
    const response = await apiClient.get<ClientStatsResponse>('/api/admin/stats/clients')
    return response.data
  },
}
