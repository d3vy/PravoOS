import apiClient from './client'
import type {
  AiResponseDto,
  CaseResponse,
  CreateCaseRequest,
  DocumentResponse,
  DocumentUploadResponse,
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
}
