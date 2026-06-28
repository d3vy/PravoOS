import apiClient from './client'
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, readTotal, type Page } from './pagination'
import type { DocumentResponse, DocumentUploadResponse } from '../types'

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
}
