import apiClient from './client'
import type {
  CaseDraftDto,
  CreateTemplateRequest,
  TemplateResponse,
  UpdateTemplateRequest,
} from '../types'

export const templatesApi = {
  getAll: async (): Promise<TemplateResponse[]> => {
    const response = await apiClient.get<TemplateResponse[]>('/api/ai/templates')
    return response.data
  },

  get: async (templateId: string): Promise<TemplateResponse> => {
    const response = await apiClient.get<TemplateResponse>(`/api/ai/templates/${templateId}`)
    return response.data
  },

  create: async (data: CreateTemplateRequest): Promise<TemplateResponse> => {
    const response = await apiClient.post<TemplateResponse>('/api/ai/templates', data)
    return response.data
  },

  update: async (templateId: string, data: UpdateTemplateRequest): Promise<TemplateResponse> => {
    const response = await apiClient.put<TemplateResponse>(`/api/ai/templates/${templateId}`, data)
    return response.data
  },

  delete: async (templateId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/templates/${templateId}`)
  },

  applyToCase: async (caseId: string, templateId: string): Promise<CaseDraftDto> => {
    const response = await apiClient.post<CaseDraftDto>(
      `/api/ai/cases/${caseId}/templates/${templateId}/apply`,
    )
    return response.data
  },
}
