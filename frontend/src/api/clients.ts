import apiClient from './client'
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, readTotal, type Page } from './pagination'
import type {
  ClientDetailResponse,
  ClientResponse,
  ContactResponse,
  CreateClientRequest,
  CreateContactRequest,
  PortalInviteStatusResponse,
  UpdateClientRequest,
  UpdateContactRequest,
} from '../types'

export const clientsApi = {
  list: async (page = 0, size = DEFAULT_PAGE_SIZE): Promise<Page<ClientResponse>> => {
    const response = await apiClient.get<ClientResponse[]>('/api/ai/clients', { params: { page, size } })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  getAll: async (): Promise<ClientResponse[]> => {
    const response = await apiClient.get<ClientResponse[]>('/api/ai/clients', {
      params: { page: 0, size: MAX_PAGE_SIZE },
    })
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

  getContacts: async (clientId: string): Promise<ContactResponse[]> => {
    const response = await apiClient.get<ContactResponse[]>(`/api/ai/clients/${clientId}/contacts`, {
      params: { page: 0, size: MAX_PAGE_SIZE },
    })
    return response.data
  },

  createContact: async (clientId: string, data: CreateContactRequest): Promise<ContactResponse> => {
    const response = await apiClient.post<ContactResponse>(`/api/ai/clients/${clientId}/contacts`, data)
    return response.data
  },

  updateContact: async (
    clientId: string,
    contactId: string,
    data: UpdateContactRequest,
  ): Promise<ContactResponse> => {
    const response = await apiClient.put<ContactResponse>(
      `/api/ai/clients/${clientId}/contacts/${contactId}`,
      data,
    )
    return response.data
  },

  deleteContact: async (clientId: string, contactId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/clients/${clientId}/contacts/${contactId}`)
  },

  getPortalStatus: async (clientId: string): Promise<PortalInviteStatusResponse> => {
    const response = await apiClient.get<PortalInviteStatusResponse>(
      `/api/ai/clients/${clientId}/portal/invite`,
    )
    return response.data
  },

  invitePortal: async (clientId: string): Promise<void> => {
    await apiClient.post(`/api/ai/clients/${clientId}/portal/invite`)
  },

  revokePortal: async (clientId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/clients/${clientId}/portal/invite`)
  },
}
