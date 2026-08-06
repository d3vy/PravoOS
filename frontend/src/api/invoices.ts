import apiClient from './client'
import { DEFAULT_PAGE_SIZE, readTotal, type Page } from './pagination'
import type {
  BillingProfileRequest,
  BillingProfileResponse,
  CreateInvoiceRequest,
  InvoiceResponse,
  InvoiceStatus,
  InvoiceSummary,
} from '../types'
import i18n from '../i18n'

export const invoicesApi = {
  list: async (
    clientId?: string,
    page = 0,
    size = DEFAULT_PAGE_SIZE
  ): Promise<Page<InvoiceSummary>> => {
    const params: Record<string, string | number> = { page, size }
    if (clientId) params.clientId = clientId
    const response = await apiClient.get<InvoiceSummary[]>('/api/ai/invoices', { params })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  get: async (invoiceId: string): Promise<InvoiceResponse> => {
    const response = await apiClient.get<InvoiceResponse>(`/api/ai/invoices/${invoiceId}`)
    return response.data
  },

  create: async (data: CreateInvoiceRequest): Promise<InvoiceResponse> => {
    const response = await apiClient.post<InvoiceResponse>('/api/ai/invoices', data)
    return response.data
  },

  updateStatus: async (invoiceId: string, status: InvoiceStatus): Promise<InvoiceResponse> => {
    const response = await apiClient.patch<InvoiceResponse>(`/api/ai/invoices/${invoiceId}/status`, {
      status,
    })
    return response.data
  },

  remove: async (invoiceId: string): Promise<void> => {
    await apiClient.delete(`/api/ai/invoices/${invoiceId}`)
  },

  getBillingProfile: async (): Promise<BillingProfileResponse | null> => {
    const response = await apiClient.get<BillingProfileResponse | ''>('/api/ai/billing-profile')
    return response.status === 204 || !response.data
      ? null
      : (response.data as BillingProfileResponse)
  },

  saveBillingProfile: async (data: BillingProfileRequest): Promise<BillingProfileResponse> => {
    const response = await apiClient.put<BillingProfileResponse>('/api/ai/billing-profile', data)
    return response.data
  },

  exportPdf: async (invoiceId: string, number: string): Promise<void> => {
    const response = await apiClient.get<Blob>(`/api/ai/invoices/${invoiceId}/export`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = i18n.t('invoices.fileName', { number })
    link.click()
    window.URL.revokeObjectURL(url)
  },
}
