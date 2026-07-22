import apiClient from './client'
import type { SignatureProviderType, SignatureRequestResponse } from '../types'

export interface CreateSignatureRequest {
  documentId: string
  provider?: SignatureProviderType
  message?: string
  expiresInDays?: number
}

const downloadBlob = (blob: Blob, fileName: string): void => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  window.URL.revokeObjectURL(url)
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

  downloadProtocol: async (caseId: string, signatureId: string): Promise<void> => {
    const response = await apiClient.get<Blob>(
      `/api/ai/cases/${caseId}/signatures/${signatureId}/protocol`,
      { responseType: 'blob' }
    )
    downloadBlob(response.data, `signature-protocol-${signatureId}.pdf`)
  },

  downloadSignatureFile: async (caseId: string, signatureId: string): Promise<void> => {
    const response = await apiClient.get<Blob>(
      `/api/ai/cases/${caseId}/signatures/${signatureId}/signature-file`,
      { responseType: 'blob' }
    )
    downloadBlob(response.data, `signature-${signatureId}.sig`)
  },

  cancel: async (caseId: string, signatureId: string): Promise<SignatureRequestResponse> => {
    const response = await apiClient.post<SignatureRequestResponse>(
      `/api/ai/cases/${caseId}/signatures/${signatureId}/cancel`
    )
    return response.data
  },
}
