import apiClient from './client'
import { DEFAULT_PAGE_SIZE, readTotal, type Page } from './pagination'
import type {
  AiResponseDto,
  CaseResponse,
  CaseDraftDto,
  CaseDraftSummaryDto,
  CaseDraftVersionDto,
  CaseHearingEvent,
  CaseMessageResponse,
  CaseStatus,
  CaseTaskResponse,
  CreateCaseRequest,
  CreateCaseTaskRequest,
  DocumentResponse,
  DocumentUploadResponse,
  DraftTypeInfo,
  GenerateDraftRequest,
  RefineDraftRequest,
  RefineDraftResponse,
  UpdateCaseRequest,
  UpdateDraftRequest,
  UpdateCaseTaskRequest,
  WorkflowRunDto,
} from '../types'

export const casesApi = {
  list: async (
    status?: CaseStatus,
    q?: string,
    page = 0,
    size = DEFAULT_PAGE_SIZE,
    orgId?: string
  ): Promise<Page<CaseResponse>> => {
    const params: Record<string, string | number> = { page, size }
    if (status) params.status = status
    if (q && q.trim()) params.q = q.trim()
    if (orgId) params.orgId = orgId
    const response = await apiClient.get<CaseResponse[]>('/api/ai/cases', { params })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
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

  changeOrg: async (caseId: string, orgId: string | null): Promise<CaseResponse> => {
    const response = await apiClient.patch<CaseResponse>(`/api/ai/cases/${caseId}/org`, { orgId })
    return response.data
  },

  transferOwner: async (caseId: string, newOwnerId: string): Promise<CaseResponse> => {
    const response = await apiClient.patch<CaseResponse>(`/api/ai/cases/${caseId}/owner`, { newOwnerId })
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

  setDocumentVisibility: async (
    caseId: string,
    documentId: string,
    visibleToClient: boolean
  ): Promise<DocumentResponse> => {
    const response = await apiClient.patch<DocumentResponse>(
      `/api/ai/cases/${caseId}/documents/${documentId}/visibility`,
      { visibleToClient }
    )
    return response.data
  },

  listMessages: async (caseId: string): Promise<CaseMessageResponse[]> => {
    const response = await apiClient.get<CaseMessageResponse[]>(`/api/ai/cases/${caseId}/messages`)
    return response.data
  },

  sendMessage: async (caseId: string, body: string): Promise<CaseMessageResponse> => {
    const response = await apiClient.post<CaseMessageResponse>(`/api/ai/cases/${caseId}/messages`, { body })
    return response.data
  },

  getHearings: async (caseId: string): Promise<CaseHearingEvent[]> => {
    const response = await apiClient.get<CaseHearingEvent[]>(`/api/ai/cases/${caseId}/hearings`)
    return response.data
  },

  syncArbitr: async (caseId: string): Promise<CaseHearingEvent[]> => {
    const response = await apiClient.post<CaseHearingEvent[]>(`/api/ai/cases/${caseId}/arbitr/sync`)
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

  runWorkflowDefinition: async (caseId: string, definitionId: string): Promise<WorkflowRunDto> => {
    const response = await apiClient.post<WorkflowRunDto>(
      `/api/ai/cases/${caseId}/workflow-runs`,
      { definitionId }
    )
    return response.data
  },

  getWorkflowRuns: async (caseId: string): Promise<WorkflowRunDto[]> => {
    const response = await apiClient.get<WorkflowRunDto[]>(`/api/ai/cases/${caseId}/workflow-runs`)
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

  getDraft: async (draftId: string): Promise<CaseDraftDto> => {
    const response = await apiClient.get<CaseDraftDto>(`/api/ai/drafts/${draftId}`)
    return response.data
  },

  updateDraft: async (draftId: string, data: UpdateDraftRequest): Promise<CaseDraftDto> => {
    const response = await apiClient.put<CaseDraftDto>(`/api/ai/drafts/${draftId}`, data)
    return response.data
  },

  getDraftVersions: async (draftId: string): Promise<CaseDraftVersionDto[]> => {
    const response = await apiClient.get<CaseDraftVersionDto[]>(`/api/ai/drafts/${draftId}/versions`)
    return response.data
  },

  restoreDraftVersion: async (draftId: string, versionId: string): Promise<CaseDraftDto> => {
    const response = await apiClient.post<CaseDraftDto>(
      `/api/ai/drafts/${draftId}/versions/${versionId}/restore`
    )
    return response.data
  },

  refineDraft: async (draftId: string, data: RefineDraftRequest): Promise<RefineDraftResponse> => {
    const response = await apiClient.post<RefineDraftResponse>(`/api/ai/drafts/${draftId}/refine`, data)
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
