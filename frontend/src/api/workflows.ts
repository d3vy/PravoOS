import apiClient from './client'
import type {
  AiResponseDto,
  RateRequest,
  SaveWorkflowDefinitionRequest,
  WorkflowDefinitionDto,
  WorkflowInfo,
} from '../types'

export const workflowsApi = {
  getAll: async (): Promise<WorkflowInfo[]> => {
    const response = await apiClient.get<WorkflowInfo[]>('/api/ai/workflows')
    return response.data
  },

  rateResponse: async (responseId: string, data: RateRequest): Promise<AiResponseDto> => {
    const response = await apiClient.post<AiResponseDto>(`/api/ai/responses/${responseId}/rate`, data)
    return response.data
  },
}

export const workflowDefinitionsApi = {
  getAll: async (): Promise<WorkflowDefinitionDto[]> => {
    const response = await apiClient.get<WorkflowDefinitionDto[]>('/api/ai/workflow-definitions')
    return response.data
  },

  get: async (id: string): Promise<WorkflowDefinitionDto> => {
    const response = await apiClient.get<WorkflowDefinitionDto>(`/api/ai/workflow-definitions/${id}`)
    return response.data
  },

  create: async (data: SaveWorkflowDefinitionRequest): Promise<WorkflowDefinitionDto> => {
    const response = await apiClient.post<WorkflowDefinitionDto>('/api/ai/workflow-definitions', data)
    return response.data
  },

  update: async (id: string, data: SaveWorkflowDefinitionRequest): Promise<WorkflowDefinitionDto> => {
    const response = await apiClient.put<WorkflowDefinitionDto>(`/api/ai/workflow-definitions/${id}`, data)
    return response.data
  },

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/ai/workflow-definitions/${id}`)
  },
}
