import apiClient from './client'
import type {
  CaseMessageResponse,
  DocumentResponse,
  DocumentUploadResponse,
  PortalCaseDetailResponse,
  PortalCaseResponse,
} from '../types'

export const portalApi = {
  listCases: async (): Promise<PortalCaseResponse[]> => {
    const response = await apiClient.get<PortalCaseResponse[]>('/api/ai/portal/cases')
    return response.data
  },

  getCase: async (caseId: string): Promise<PortalCaseDetailResponse> => {
    const response = await apiClient.get<PortalCaseDetailResponse>(`/api/ai/portal/cases/${caseId}`)
    return response.data
  },

  listCaseDocuments: async (caseId: string): Promise<DocumentResponse[]> => {
    const response = await apiClient.get<DocumentResponse[]>(`/api/ai/portal/cases/${caseId}/documents`)
    return response.data
  },

  uploadCaseDocument: async (caseId: string, file: File, title: string): Promise<DocumentUploadResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', title)
    const response = await apiClient.post<DocumentUploadResponse>(
      `/api/ai/portal/cases/${caseId}/documents`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    return response.data
  },

  downloadCaseDocument: async (caseId: string, doc: DocumentResponse): Promise<void> => {
    const response = await apiClient.get<Blob>(
      `/api/ai/portal/cases/${caseId}/documents/${doc.id}/content`,
      { responseType: 'blob' }
    )
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = doc.fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },

  listCaseMessages: async (caseId: string): Promise<CaseMessageResponse[]> => {
    const response = await apiClient.get<CaseMessageResponse[]>(`/api/ai/portal/cases/${caseId}/messages`)
    return response.data
  },

  sendCaseMessage: async (caseId: string, body: string): Promise<CaseMessageResponse> => {
    const response = await apiClient.post<CaseMessageResponse>(`/api/ai/portal/cases/${caseId}/messages`, { body })
    return response.data
  },
}
