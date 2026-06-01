import apiClient from './client'
import type { LoginRequest, LoginResponse, ApplyRequest, ApplicationResponse } from '../types'

export const authApi = {
  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post<LoginResponse>('/api/auth/login', data)
    return response.data
  },

  apply: async (data: ApplyRequest): Promise<ApplicationResponse> => {
    const response = await apiClient.post<ApplicationResponse>('/api/auth/apply', data)
    return response.data
  },

  logout: async (refreshToken: string): Promise<void> => {
    await apiClient.post('/api/auth/logout', { refreshToken })
  },
}
