import apiClient from './client'
import type {
  CaseTimeSummary,
  CreateTimeEntryRequest,
  StartTimerRequest,
  TimeEntryResponse,
  UpdateTimeEntryRequest,
} from '../types'

export const timeApi = {
  summary: async (caseId: string): Promise<CaseTimeSummary> => {
    const response = await apiClient.get<CaseTimeSummary>(`/api/ai/cases/${caseId}/time`)
    return response.data
  },

  create: async (caseId: string, data: CreateTimeEntryRequest): Promise<TimeEntryResponse> => {
    const response = await apiClient.post<TimeEntryResponse>(`/api/ai/cases/${caseId}/time`, data)
    return response.data
  },

  update: async (
    caseId: string,
    entryId: string,
    data: UpdateTimeEntryRequest
  ): Promise<TimeEntryResponse> => {
    const response = await apiClient.patch<TimeEntryResponse>(
      `/api/ai/cases/${caseId}/time/${entryId}`,
      data
    )
    return response.data
  },

  remove: async (caseId: string, entryId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/cases/${caseId}/time/${entryId}`)
  },

  startTimer: async (caseId: string, data: StartTimerRequest): Promise<TimeEntryResponse> => {
    const response = await apiClient.post<TimeEntryResponse>(`/api/ai/cases/${caseId}/time/timer/start`, data)
    return response.data
  },

  stopTimer: async (caseId: string): Promise<TimeEntryResponse> => {
    const response = await apiClient.post<TimeEntryResponse>(`/api/ai/cases/${caseId}/time/timer/stop`)
    return response.data
  },

  activeTimer: async (): Promise<TimeEntryResponse | null> => {
    const response = await apiClient.get<TimeEntryResponse>('/api/ai/time/active')
    return response.status === 204 ? null : response.data
  },
}
