import apiClient from './client'
import type {
  CaseMessageResponse,
  DocumentResponse,
  DocumentUploadResponse,
  InvoicePaymentResponse,
  InvoiceResponse,
  InvoiceSummary,
  PortalCaseDetailResponse,
  PortalCaseResponse,
  SignatureRequestResponse,
} from '../types'

export const portalApi = {
  listCases: async (): Promise<PortalCaseResponse[]> => {
    const response = await apiClient.get<PortalCaseResponse[]>('/api/ai/portal/cases')
    return response.data
  },

  getCase: async (caseId: string): Promise<PortalCaseDetailResponse> => {
    const response = await apiClient.get<PortalCaseDetailResponse>(`/api/ai/portal/cases/${caseId}`)
    return response.data
  },

  listCaseDocuments: async (caseId: string): Promise<DocumentResponse[]> => {
    const response = await apiClient.get<DocumentResponse[]>(`/api/ai/portal/cases/${caseId}/documents`)
    return response.data
  },

  uploadCaseDocument: async (caseId: string, file: File, title: string): Promise<DocumentUploadResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', title)
    const response = await apiClient.post<DocumentUploadResponse>(
      `/api/ai/portal/cases/${caseId}/documents`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    return response.data
  },

  downloadCaseDocument: async (caseId: string, doc: DocumentResponse): Promise<void> => {
    const response = await apiClient.get<Blob>(
      `/api/ai/portal/cases/${caseId}/documents/${doc.id}/content`,
      { responseType: 'blob' }
    )
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = doc.fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },

  listCaseMessages: async (caseId: string): Promise<CaseMessageResponse[]> => {
    const response = await apiClient.get<CaseMessageResponse[]>(`/api/ai/portal/cases/${caseId}/messages`)
    return response.data
  },

  sendCaseMessage: async (caseId: string, body: string): Promise<CaseMessageResponse> => {
    const response = await apiClient.post<CaseMessageResponse>(`/api/ai/portal/cases/${caseId}/messages`, { body })
    return response.data
  },

  listCaseSignatures: async (caseId: string): Promise<SignatureRequestResponse[]> => {
    const response = await apiClient.get<SignatureRequestResponse[]>(`/api/ai/portal/cases/${caseId}/signatures`)
    return response.data
  },

  signDocument: async (
    signatureId: string,
    signerName: string
  ): Promise<SignatureRequestResponse> => {
    const response = await apiClient.post<SignatureRequestResponse>(
      `/api/ai/portal/signatures/${signatureId}/sign`,
      { signerName, consent: true }
    )
    return response.data
  },

  signDocumentWithCms: async (signatureId: string, file: File): Promise<SignatureRequestResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await apiClient.post<SignatureRequestResponse>(
      `/api/ai/portal/signatures/${signatureId}/sign-cms`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    return response.data
  },

  downloadSignatureProtocol: async (signatureId: string): Promise<void> => {
    const response = await apiClient.get<Blob>(`/api/ai/portal/signatures/${signatureId}/protocol`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `signature-protocol-${signatureId}.pdf`
    link.click()
    window.URL.revokeObjectURL(url)
  },

  declineSignature: async (signatureId: string, reason: string): Promise<SignatureRequestResponse> => {
    const response = await apiClient.post<SignatureRequestResponse>(
      `/api/ai/portal/signatures/${signatureId}/decline`,
      { reason }
    )
    return response.data
  },

  listInvoices: async (): Promise<InvoiceSummary[]> => {
    const response = await apiClient.get<InvoiceSummary[]>('/api/ai/portal/invoices')
    return response.data
  },

  getInvoice: async (invoiceId: string): Promise<InvoiceResponse> => {
    const response = await apiClient.get<InvoiceResponse>(`/api/ai/portal/invoices/${invoiceId}`)
    return response.data
  },

  payInvoice: async (invoiceId: string): Promise<InvoicePaymentResponse> => {
    const response = await apiClient.post<InvoicePaymentResponse>(
      `/api/ai/portal/invoices/${invoiceId}/pay`
    )
    return response.data
  },
}
