import apiClient from './client'
import type { LawyerProfileResponse, TelegramLinkResponse, UpdateProfileRequest } from '../types'

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
}
