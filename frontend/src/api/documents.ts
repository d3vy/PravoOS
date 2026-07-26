import apiClient from './client'
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, readTotal, type Page } from './pagination'
import type {
  DocumentInsightResponse,
  DocumentResponse,
  DocumentUploadResponse,
  LegislationResponse,
  LegislationUpload,
} from '../types'

export const documentsApi = {
  list: async (page = 0, size = DEFAULT_PAGE_SIZE): Promise<Page<DocumentResponse>> => {
    const response = await apiClient.get<DocumentResponse[]>('/api/ai/documents', { params: { page, size } })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  upload: async (file: File, title: string): Promise<DocumentUploadResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', title)
    const response = await apiClient.post<DocumentUploadResponse>('/api/ai/documents', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return response.data
  },

  getAll: async (): Promise<DocumentResponse[]> => {
    const response = await apiClient.get<DocumentResponse[]>('/api/ai/documents', {
      params: { page: 0, size: MAX_PAGE_SIZE },
    })
    return response.data
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/ai/documents/${id}`)
  },

  getContent: async (id: string): Promise<Blob> => {
    const response = await apiClient.get(`/api/ai/documents/${id}/content`, {
      responseType: 'blob',
    })
    return response.data
  },

  getInsight: async (id: string): Promise<DocumentInsightResponse> => {
    const response = await apiClient.get<DocumentInsightResponse>(`/api/ai/document-insights/${id}`)
    return response.data
  },

  regenerateInsight: async (id: string): Promise<DocumentInsightResponse> => {
    const response = await apiClient.post<DocumentInsightResponse>(
      `/api/ai/document-insights/${id}/regenerate`
    )
    return response.data
  },

  listLegislation: async (page = 0, size = DEFAULT_PAGE_SIZE): Promise<Page<LegislationResponse>> => {
    const response = await apiClient.get<LegislationResponse[]>('/api/ai/documents/legislation', {
      params: { page, size },
    })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  uploadLegislation: async (input: LegislationUpload): Promise<DocumentUploadResponse> => {
    const formData = new FormData()
    formData.append('file', input.file)
    formData.append('actCanonical', input.actCanonical)
    formData.append('articleNumber', input.articleNumber)
    formData.append('editionDate', input.editionDate)
    if (input.title) formData.append('title', input.title)
    const response = await apiClient.post<DocumentUploadResponse>(
      '/api/ai/documents/legislation',
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    return response.data
  },
}
