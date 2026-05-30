import apiClient from './client'
import type { DocumentResponse, DocumentUploadResponse } from '../types'

export const documentsApi = {
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
    const response = await apiClient.get<DocumentResponse[]>('/api/ai/documents')
    return response.data
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/ai/documents/${id}`)
  },
}
