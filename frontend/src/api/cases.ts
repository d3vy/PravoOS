import apiClient from './client'
import type {
  AiResponseDto,
  CaseResponse,
  CaseDraftDto,
  CaseDraftSummaryDto,
  CaseStatus,
  CaseTaskResponse,
  CreateCaseRequest,
  CreateCaseTaskRequest,
  DocumentResponse,
  DocumentUploadResponse,
  DraftTypeInfo,
  GenerateDraftRequest,
  UpdateCaseRequest,
  UpdateCaseTaskRequest,
} from '../types'

export const casesApi = {
  getAll: async (status?: CaseStatus): Promise<CaseResponse[]> => {
    const response = await apiClient.get<CaseResponse[]>('/api/ai/cases', {
      params: status ? { status } : undefined,
    })
    return response.data
  },

  updateStatus: async (caseId: string, status: CaseStatus): Promise<CaseResponse> => {
    const response = await apiClient.patch<CaseResponse>(`/api/ai/cases/${caseId}/status`, { status })
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

  update: async (caseId: string, data: UpdateCaseRequest): Promise<CaseResponse> => {
    const response = await apiClient.patch<CaseResponse>(`/api/ai/cases/${caseId}`, data)
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

  getDrafts: async (caseId: string): Promise<CaseDraftSummaryDto[]> => {
    const response = await apiClient.get<CaseDraftSummaryDto[]>(`/api/ai/cases/${caseId}/drafts`)
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

  getTasks: async (caseId: string): Promise<CaseTaskResponse[]> => {
    const response = await apiClient.get<CaseTaskResponse[]>(`/api/ai/cases/${caseId}/tasks`)
    return response.data
  },

  createTask: async (caseId: string, data: CreateCaseTaskRequest): Promise<CaseTaskResponse> => {
    const response = await apiClient.post<CaseTaskResponse>(`/api/ai/cases/${caseId}/tasks`, data)
    return response.data
  },

  updateTask: async (caseId: string, taskId: string, data: UpdateCaseTaskRequest): Promise<CaseTaskResponse> => {
    const response = await apiClient.patch<CaseTaskResponse>(`/api/ai/cases/${caseId}/tasks/${taskId}`, data)
    return response.data
  },

  deleteTask: async (caseId: string, taskId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/cases/${caseId}/tasks/${taskId}`)
  },

  generateTasks: async (caseId: string): Promise<CaseTaskResponse[]> => {
    const response = await apiClient.post<CaseTaskResponse[]>(`/api/ai/cases/${caseId}/tasks/generate`)
    return response.data
  },

  exportCase: async (caseId: string, format: 'docx' | 'pdf', fileName: string): Promise<void> => {
    const response = await apiClient.get<Blob>(`/api/ai/cases/${caseId}/export`, {
      params: { format },
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `${fileName}.${format}`
    link.click()
    window.URL.revokeObjectURL(url)
  },
}
