import apiClient from './client'
import type {
  ClientDetailResponse,
  ClientResponse,
  CreateClientRequest,
  UpdateClientRequest,
} from '../types'

export const clientsApi = {
  getAll: async (): Promise<ClientResponse[]> => {
    const response = await apiClient.get<ClientResponse[]>('/api/ai/clients')
    return response.data
  },

  get: async (clientId: string): Promise<ClientDetailResponse> => {
    const response = await apiClient.get<ClientDetailResponse>(`/api/ai/clients/${clientId}`)
    return response.data
  },

  create: async (data: CreateClientRequest): Promise<ClientResponse> => {
    const response = await apiClient.post<ClientResponse>('/api/ai/clients', data)
    return response.data
  },

  update: async (clientId: string, data: UpdateClientRequest): Promise<ClientResponse> => {
    const response = await apiClient.put<ClientResponse>(`/api/ai/clients/${clientId}`, data)
    return response.data
  },

  delete: async (clientId: string, cascade: boolean): Promise<void> => {
    await apiClient.delete(`/api/ai/clients/${clientId}`, { params: { cascade } })
  },
}
