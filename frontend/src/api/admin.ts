import apiClient from './client'
import type { ApplicationResponse, ClientStatsResponse, LawyerProfileResponse } from '../types'

export interface Page<T> {
  items: T[]
  total: number
}

export const DEFAULT_PAGE_SIZE = 20

function readTotal(headers: unknown, fallback: number): number {
  const raw = (headers as Record<string, string> | undefined)?.['x-total-count']
  const parsed = raw ? Number.parseInt(raw, 10) : NaN
  return Number.isFinite(parsed) ? parsed : fallback
}

export const adminApi = {
  getAllApplications: async (page = 0, size = DEFAULT_PAGE_SIZE): Promise<Page<ApplicationResponse>> => {
    const response = await apiClient.get<ApplicationResponse[]>('/api/admin/applications', {
      params: { page, size },
    })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  getPendingApplications: async (page = 0, size = DEFAULT_PAGE_SIZE): Promise<Page<ApplicationResponse>> => {
    const response = await apiClient.get<ApplicationResponse[]>('/api/admin/applications/pending', {
      params: { page, size },
    })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
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

  getLawyers: async (page = 0, size = DEFAULT_PAGE_SIZE): Promise<Page<LawyerProfileResponse>> => {
    const response = await apiClient.get<LawyerProfileResponse[]>('/api/admin/users/lawyers', {
      params: { page, size },
    })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  deleteLawyer: async (userId: string): Promise<void> => {
    await apiClient.delete(`/api/admin/users/lawyers/${userId}`)
  },

  getClientStats: async (): Promise<ClientStatsResponse> => {
    const response = await apiClient.get<ClientStatsResponse>('/api/admin/stats/clients')
    return response.data
  },
}
