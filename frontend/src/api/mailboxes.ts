import apiClient from './client'
import type {
  CreateMailboxRequest,
  MailHostPresetResponse,
  MailSyncResult,
  MailboxResponse,
  MailboxTestResult,
  UpdateMailboxRequest,
} from '../types'

export const mailboxesApi = {
  presets: async (): Promise<MailHostPresetResponse[]> => {
    const response = await apiClient.get<MailHostPresetResponse[]>('/api/ai/mailboxes/presets')
    return response.data
  },

  list: async (): Promise<MailboxResponse[]> => {
    const response = await apiClient.get<MailboxResponse[]>('/api/ai/mailboxes')
    return response.data
  },

  create: async (data: CreateMailboxRequest): Promise<MailboxResponse> => {
    const response = await apiClient.post<MailboxResponse>('/api/ai/mailboxes', data)
    return response.data
  },

  update: async (mailboxId: string, data: UpdateMailboxRequest): Promise<MailboxResponse> => {
    const response = await apiClient.put<MailboxResponse>(`/api/ai/mailboxes/${mailboxId}`, data)
    return response.data
  },

  test: async (mailboxId: string): Promise<MailboxTestResult> => {
    const response = await apiClient.post<MailboxTestResult>(`/api/ai/mailboxes/${mailboxId}/test`)
    return response.data
  },

  sync: async (mailboxId: string): Promise<MailSyncResult> => {
    const response = await apiClient.post<MailSyncResult>(`/api/ai/mailboxes/${mailboxId}/sync`)
    return response.data
  },

  remove: async (mailboxId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/mailboxes/${mailboxId}`)
  },
}
