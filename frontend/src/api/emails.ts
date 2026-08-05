import apiClient from './client'
import { DEFAULT_PAGE_SIZE, readTotal, type Page } from './pagination'
import type { EmailMessageResponse, LinkEmailRequest } from '../types'

export const emailsApi = {
  unlinked: async (page = 0, size = DEFAULT_PAGE_SIZE): Promise<Page<EmailMessageResponse>> => {
    const response = await apiClient.get<EmailMessageResponse[]>('/api/ai/emails/unlinked', {
      params: { page, size },
    })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  link: async (emailId: string, data: LinkEmailRequest): Promise<EmailMessageResponse> => {
    const response = await apiClient.post<EmailMessageResponse>(`/api/ai/emails/${emailId}/link`, data)
    return response.data
  },

  unlink: async (emailId: string): Promise<EmailMessageResponse> => {
    const response = await apiClient.delete<EmailMessageResponse>(`/api/ai/emails/${emailId}/link`)
    return response.data
  },

  byCase: async (caseId: string): Promise<EmailMessageResponse[]> => {
    const response = await apiClient.get<EmailMessageResponse[]>(`/api/ai/cases/${caseId}/emails`)
    return response.data
  },

  byClient: async (clientId: string): Promise<EmailMessageResponse[]> => {
    const response = await apiClient.get<EmailMessageResponse[]>(`/api/ai/clients/${clientId}/emails`)
    return response.data
  },
}
