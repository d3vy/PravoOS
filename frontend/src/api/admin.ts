import apiClient from './client'
import type {
  AdminConversationResponse,
  ApplicationResponse,
  ClientStatsResponse,
  LawyerProfileResponse,
  MessageResponse,
} from '../types'
import { DEFAULT_PAGE_SIZE, readTotal, type Page } from './pagination'

export { DEFAULT_PAGE_SIZE, type Page } from './pagination'

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

  getConversations: async (
    filters: { orgId?: string; lawyerId?: string; q?: string } = {},
    page = 0,
    size = DEFAULT_PAGE_SIZE
  ): Promise<Page<AdminConversationResponse>> => {
    const response = await apiClient.get<AdminConversationResponse[]>(
      '/api/ai/admin/conversations',
      {
        params: {
          ...(filters.orgId ? { orgId: filters.orgId } : {}),
          ...(filters.lawyerId ? { lawyerId: filters.lawyerId } : {}),
          ...(filters.q ? { q: filters.q } : {}),
          page,
          size,
        },
      }
    )
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  getConversationMessages: async (
    conversationId: string,
    page = 0,
    size = 100
  ): Promise<Page<MessageResponse>> => {
    const response = await apiClient.get<MessageResponse[]>(
      `/api/ai/admin/conversations/${conversationId}/messages`,
      { params: { page, size } }
    )
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },
}
