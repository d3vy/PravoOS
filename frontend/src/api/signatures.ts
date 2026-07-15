import apiClient from './client'
import type { SignatureProviderType, SignatureRequestResponse } from '../types'

export interface CreateSignatureRequest {
  documentId: string
  provider?: SignatureProviderType
  message?: string
  expiresInDays?: number
}

export const signaturesApi = {
  listByCase: async (caseId: string): Promise<SignatureRequestResponse[]> => {
    const response = await apiClient.get<SignatureRequestResponse[]>(`/api/ai/cases/${caseId}/signatures`)
    return response.data
  },

  create: async (caseId: string, payload: CreateSignatureRequest): Promise<SignatureRequestResponse> => {
    const response = await apiClient.post<SignatureRequestResponse>(`/api/ai/cases/${caseId}/signatures`, payload)
    return response.data
  },

  cancel: async (caseId: string, signatureId: string): Promise<SignatureRequestResponse> => {
    const response = await apiClient.post<SignatureRequestResponse>(
      `/api/ai/cases/${caseId}/signatures/${signatureId}/cancel`
    )
    return response.data
  },
}
