import apiClient from './client'
import type {
  LoginRequest,
  LoginResponse,
  AuthResponse,
  MfaLoginRequest,
  ApplyRequest,
  ApplicationResponse,
  ApplicationSubmissionResponse,
  PortalInvitePreviewResponse,
  UpdateApplicationRequest,
} from '../types'

export const authApi = {
  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post<LoginResponse>('/api/auth/login', data)
    return response.data
  },

  loginMfa: async (data: MfaLoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/api/auth/login/mfa', data)
    return response.data
  },

  apply: async (data: ApplyRequest): Promise<ApplicationSubmissionResponse> => {
    const response = await apiClient.post<ApplicationSubmissionResponse>('/api/auth/apply', data)
    return response.data
  },

  getApplicationStatus: async (token: string): Promise<ApplicationResponse> => {
    const response = await apiClient.get<ApplicationResponse>('/api/auth/application', {
      headers: { 'X-Application-Token': token },
    })
    return response.data
  },

  updateApplication: async (
    token: string,
    data: UpdateApplicationRequest,
  ): Promise<ApplicationResponse> => {
    const response = await apiClient.put<ApplicationResponse>('/api/auth/application', data, {
      headers: { 'X-Application-Token': token },
    })
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

  portalInvitePreview: async (token: string): Promise<PortalInvitePreviewResponse> => {
    const response = await apiClient.get<PortalInvitePreviewResponse>('/api/auth/portal/invite', {
      params: { token },
    })
    return response.data
  },

  portalAccept: async (token: string, password: string): Promise<LoginResponse> => {
    const response = await apiClient.post<LoginResponse>('/api/auth/portal/accept', { token, password })
    return response.data
  },
}
