import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PortalCaseDetailPage from './PortalCaseDetailPage'
import { portalApi } from '../../api/portal'
import type { PortalCaseDetailResponse, DocumentResponse } from '../../types'

vi.mock('../../api/portal', () => ({
  portalApi: {
    getCase: vi.fn(),
    listCaseDocuments: vi.fn(),
    uploadCaseDocument: vi.fn(),
    downloadCaseDocument: vi.fn(),
    listCaseSignatures: vi.fn(),
    listCaseMessages: vi.fn(),
    sendCaseMessage: vi.fn(),
  },
}))

function renderPage(caseId = 'case-1'): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/portal/cases/${caseId}`]}>
        <Routes>
          <Route path="/portal/cases/:caseId" element={<PortalCaseDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

function makeCase(overrides: Partial<PortalCaseDetailResponse> = {}): PortalCaseDetailResponse {
  return {
    id: 'case-1',
    title: 'Банкротство ООО Ромашка',
    description: null,
    status: 'IN_PROGRESS',
    statusName: 'In progress',
    filingDeadline: null,
    nextHearingDate: null,
    courtSystem: 'ARBITR',
    courtSystemName: 'Arbitration',
    courtCaseNumber: null,
    courtCardUrl: null,
    createdAt: '2026-01-01T00:00:00Z',
    hearings: [],
    ...overrides,
  } as PortalCaseDetailResponse
}

function makeDocument(overrides: Partial<DocumentResponse> = {}): DocumentResponse {
  return {
    id: 'doc-1',
    title: 'Заявление',
    fileName: 'application.pdf',
    status: 'READY',
    fileType: 'PDF',
    createdAt: '2026-01-01T00:00:00Z',
    visibleToClient: true,
    ...overrides,
  } as DocumentResponse
}

describe('PortalCaseDetailPage', () => {
  beforeEach(() => {
    vi.mocked(portalApi.getCase).mockReset()
    vi.mocked(portalApi.listCaseDocuments).mockReset().mockResolvedValue([])
    vi.mocked(portalApi.uploadCaseDocument).mockReset()
    vi.mocked(portalApi.downloadCaseDocument).mockReset()
    vi.mocked(portalApi.listCaseSignatures).mockReset().mockResolvedValue([])
    vi.mocked(portalApi.listCaseMessages).mockReset().mockResolvedValue([])
  })

  afterEach(() => {
    cleanup()
  })

  it('shows a not-found message when the case query fails', async () => {
    vi.mocked(portalApi.getCase).mockRejectedValue(new Error('404'))
    renderPage()
    expect(await screen.findByText('The case was not found or is unavailable.')).toBeInTheDocument()
  })

  it('renders case details, hearings and documents', async () => {
    vi.mocked(portalApi.getCase).mockResolvedValue(
      makeCase({
        description: 'Дело о банкротстве',
        courtCaseNumber: 'А40-12345/2024',
        hearings: [
          { id: 'h1', eventDate: '2026-03-01T10:00:00Z', eventType: 'Preliminary hearing', description: null, courtName: 'Арбитражный суд' },
        ],
      })
    )
    vi.mocked(portalApi.listCaseDocuments).mockResolvedValue([makeDocument()])
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Банкротство ООО Ромашка' })).toBeInTheDocument()
    expect(screen.getByText('Дело о банкротстве')).toBeInTheDocument()
    expect(screen.getByText('А40-12345/2024')).toBeInTheDocument()
    expect(screen.getByText('Preliminary hearing')).toBeInTheDocument()
    expect(screen.getByText('Арбитражный суд')).toBeInTheDocument()
    expect(await screen.findByText('Заявление')).toBeInTheDocument()
  })

  it('shows the empty-hearings and empty-documents placeholders', async () => {
    vi.mocked(portalApi.getCase).mockResolvedValue(makeCase())
    renderPage()

    expect(await screen.findByText('No hearings yet.')).toBeInTheDocument()
    expect(await screen.findByText('No documents yet.')).toBeInTheDocument()
  })

  it('rejects unsupported file extensions on upload without calling the API', async () => {
    vi.mocked(portalApi.getCase).mockResolvedValue(makeCase())
    renderPage()
    await screen.findByText('No documents yet.')

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement
    const badFile = new File(['x'], 'malware.exe', { type: 'application/octet-stream' })
    fireEvent.change(fileInput, { target: { files: [badFile] } })

    expect(await screen.findByText('Only PDF, DOCX and TXT are supported')).toBeInTheDocument()
    expect(portalApi.uploadCaseDocument).not.toHaveBeenCalled()
  })

  it('uploads a valid document and refreshes the document list', async () => {
    vi.mocked(portalApi.getCase).mockResolvedValue(makeCase())
    vi.mocked(portalApi.uploadCaseDocument).mockResolvedValue({ id: 'doc-2' } as never)
    renderPage()
    await screen.findByText('No documents yet.')

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement
    const goodFile = new File(['%PDF-1.4'], 'contract.pdf', { type: 'application/pdf' })
    await userEvent.upload(fileInput, goodFile)

    await waitFor(() => expect(portalApi.uploadCaseDocument).toHaveBeenCalledWith('case-1', goodFile, 'contract'))
  })

  it('downloads a document when clicked', async () => {
    vi.mocked(portalApi.getCase).mockResolvedValue(makeCase())
    vi.mocked(portalApi.listCaseDocuments).mockResolvedValue([makeDocument()])
    vi.mocked(portalApi.downloadCaseDocument).mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('Заявление'))

    await waitFor(() => expect(portalApi.downloadCaseDocument).toHaveBeenCalledWith('case-1', expect.objectContaining({ id: 'doc-1' })))
  })
})
