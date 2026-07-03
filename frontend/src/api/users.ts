import apiClient from './client'
import type {
  LawyerProfileResponse,
  TelegramLinkResponse,
  UpdateProfileRequest,
  MfaStatusResponse,
  MfaSetupResponse,
  SessionResponse,
  NotificationSettingsResponse,
  UpdateNotificationSettingsRequest,
} from '../types'

export const usersApi = {
  getProfile: async (): Promise<LawyerProfileResponse> => {
    const response = await apiClient.get<LawyerProfileResponse>('/api/user/profile')
    return response.data
  },

  updateProfile: async (data: UpdateProfileRequest): Promise<LawyerProfileResponse> => {
    const response = await apiClient.patch<LawyerProfileResponse>('/api/user/profile', data)
    return response.data
  },

  createTelegramLinkCode: async (): Promise<TelegramLinkResponse> => {
    const response = await apiClient.post<TelegramLinkResponse>('/api/user/profile/telegram/link-code')
    return response.data
  },

  unlinkTelegram: async (): Promise<void> => {
    await apiClient.delete('/api/user/profile/telegram')
  },

  getMfaStatus: async (): Promise<MfaStatusResponse> => {
    const response = await apiClient.get<MfaStatusResponse>('/api/user/mfa')
    return response.data
  },

  setupMfa: async (): Promise<MfaSetupResponse> => {
    const response = await apiClient.post<MfaSetupResponse>('/api/user/mfa/setup')
    return response.data
  },

  enableMfa: async (code: string): Promise<void> => {
    await apiClient.post('/api/user/mfa/enable', { code })
  },

  disableMfa: async (code: string): Promise<void> => {
    await apiClient.post('/api/user/mfa/disable', { code })
  },

  listSessions: async (): Promise<SessionResponse[]> => {
    const response = await apiClient.get<SessionResponse[]>('/api/user/sessions')
    return response.data
  },

  revokeSession: async (sessionId: string): Promise<void> => {
    await apiClient.delete(`/api/user/sessions/${sessionId}`)
  },

  getNotificationSettings: async (): Promise<NotificationSettingsResponse> => {
    const response = await apiClient.get<NotificationSettingsResponse>('/api/user/settings/notifications')
    return response.data
  },

  updateNotificationSettings: async (
    data: UpdateNotificationSettingsRequest,
  ): Promise<NotificationSettingsResponse> => {
    const response = await apiClient.put<NotificationSettingsResponse>('/api/user/settings/notifications', data)
    return response.data
  },
}
