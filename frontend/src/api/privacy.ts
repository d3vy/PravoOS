import apiClient from './client'
import type {
  ConsentPurpose,
  ConsentResponse,
  PersonalDataExportResponse,
  PrivacyPolicyResponse,
  SubjectRequestResponse,
} from '../types'

export const privacyApi = {
  policy: async (): Promise<PrivacyPolicyResponse> => {
    const response = await apiClient.get<PrivacyPolicyResponse>('/api/user/privacy/policy')
    return response.data
  },

  consents: async (): Promise<ConsentResponse[]> => {
    const response = await apiClient.get<ConsentResponse[]>('/api/user/privacy/consents')
    return response.data
  },

  grantConsent: async (purpose: ConsentPurpose): Promise<ConsentResponse> => {
    const response = await apiClient.post<ConsentResponse>(`/api/user/privacy/consents/${purpose}`)
    return response.data
  },

  revokeConsent: async (purpose: ConsentPurpose): Promise<void> => {
    await apiClient.delete(`/api/user/privacy/consents/${purpose}`)
  },

  exportData: async (): Promise<PersonalDataExportResponse> => {
    const response = await apiClient.get<PersonalDataExportResponse>('/api/user/privacy/export')
    return response.data
  },

  requests: async (): Promise<SubjectRequestResponse[]> => {
    const response = await apiClient.get<SubjectRequestResponse[]>('/api/user/privacy/requests')
    return response.data
  },

  erase: async (password: string): Promise<SubjectRequestResponse> => {
    const response = await apiClient.post<SubjectRequestResponse>('/api/user/privacy/erase', {
      password,
    })
    return response.data
  },
}
