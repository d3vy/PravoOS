import apiClient from './client'
import type { LawyerProfileResponse, UpdateProfileRequest } from '../types'

export const usersApi = {
  getProfile: async (): Promise<LawyerProfileResponse> => {
    const response = await apiClient.get<LawyerProfileResponse>('/api/user/profile')
    return response.data
  },

  updateProfile: async (data: UpdateProfileRequest): Promise<LawyerProfileResponse> => {
    const response = await apiClient.patch<LawyerProfileResponse>('/api/user/profile', data)
    return response.data
  },
}
