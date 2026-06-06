import apiClient from './client'
import type { LoginRequest, AuthResponse, ApplyRequest, ApplicationResponse } from '../types'

export const authApi = {
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/auth/login', data)
    return response.data
  },

  apply: async (data: ApplyRequest): Promise<ApplicationResponse> => {
    const response = await apiClient.post<ApplicationResponse>('/api/auth/apply', data)
    return response.data
  },

  logout: async (): Promise<void> => {
    await apiClient.post('/api/auth/logout')
  },

  forgotPassword: async (email: string): Promise<void> => {
    await apiClient.post('/api/auth/forgot-password', { email })
  },

  resetPassword: async (token: string, password: string): Promise<void> => {
    await apiClient.post('/api/auth/reset-password', { token, password })
  },

  verifyEmail: async (token: string): Promise<void> => {
    await apiClient.post('/api/auth/verify-email', { token })
  },

  resendVerification: async (email: string): Promise<void> => {
    await apiClient.post('/api/auth/resend-verification', { email })
  },
}
