import apiClient from './client'
import type { CalendarEvent } from '../types'

export interface CalendarFilters {
  caseId?: string
  clientId?: string
}

function buildParams(from: string, to: string, filters: CalendarFilters): Record<string, string> {
  const params: Record<string, string> = { from, to }
  if (filters.caseId) params.caseId = filters.caseId
  if (filters.clientId) params.clientId = filters.clientId
  return params
}

export const calendarApi = {
  list: async (from: string, to: string, filters: CalendarFilters = {}): Promise<CalendarEvent[]> => {
    const response = await apiClient.get<CalendarEvent[]>('/api/ai/calendar', {
      params: buildParams(from, to, filters),
    })
    return response.data
  },

  exportIcs: async (from: string, to: string, filters: CalendarFilters = {}): Promise<void> => {
    const response = await apiClient.get<Blob>('/api/ai/calendar/export.ics', {
      params: buildParams(from, to, filters),
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = 'calendar.ics'
    link.click()
    window.URL.revokeObjectURL(url)
  },
}
