import apiClient from './client'
import type {
  AiResponseDto,
  CaseResponse,
  CaseDraftDto,
  CreateCaseRequest,
  DocumentResponse,
  DocumentUploadResponse,
  DraftTypeInfo,
  GenerateDraftRequest,
} from '../types'

export const casesApi = {
  getAll: async (): Promise<CaseResponse[]> => {
    const response = await apiClient.get<CaseResponse[]>('/api/ai/cases')
    return response.data
  },

  get: async (caseId: string): Promise<CaseResponse> => {
    const response = await apiClient.get<CaseResponse>(`/api/ai/cases/${caseId}`)
    return response.data
  },

  create: async (data: CreateCaseRequest): Promise<CaseResponse> => {
    const response = await apiClient.post<CaseResponse>('/api/ai/cases', data)
    return response.data
  },

  delete: async (caseId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/cases/${caseId}`)
  },

  getDocuments: async (caseId: string): Promise<DocumentResponse[]> => {
    const response = await apiClient.get<DocumentResponse[]>(`/api/ai/cases/${caseId}/documents`)
    return response.data
  },

  uploadDocument: async (caseId: string, file: File, title: string): Promise<DocumentUploadResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', title)
    const response = await apiClient.post<DocumentUploadResponse>(
      `/api/ai/cases/${caseId}/documents`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    return response.data
  },

  getResponses: async (caseId: string): Promise<AiResponseDto[]> => {
    const response = await apiClient.get<AiResponseDto[]>(`/api/ai/cases/${caseId}/responses`)
    return response.data
  },

  runWorkflow: async (caseId: string, workflowId: string, question?: string): Promise<AiResponseDto> => {
    const response = await apiClient.post<AiResponseDto>(
      `/api/ai/cases/${caseId}/workflows/${workflowId}/run`,
      { question: question ?? null }
    )
    return response.data
  },

  getDraftTypes: async (): Promise<DraftTypeInfo[]> => {
    const response = await apiClient.get<DraftTypeInfo[]>('/api/ai/draft-types')
    return response.data
  },

  generateDraft: async (caseId: string, data: GenerateDraftRequest): Promise<CaseDraftDto> => {
    const response = await apiClient.post<CaseDraftDto>(`/api/ai/cases/${caseId}/drafts`, data)
    return response.data
  },

  getDrafts: async (caseId: string): Promise<CaseDraftDto[]> => {
    const response = await apiClient.get<CaseDraftDto[]>(`/api/ai/cases/${caseId}/drafts`)
    return response.data
  },

  downloadDraft: async (draftId: string, fileName: string): Promise<void> => {
    const response = await apiClient.get<Blob>(`/api/ai/drafts/${draftId}/download`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `${fileName}.docx`
    link.click()
    window.URL.revokeObjectURL(url)
  },
}
