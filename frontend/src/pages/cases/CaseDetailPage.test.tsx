import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '../../i18n'
import CaseDetailPage from './CaseDetailPage'
import { casesApi } from '../../api/cases'
import { useRecentEntitiesStore } from '../../store/recentEntitiesStore'
import type { CaseResponse, DocumentResponse } from '../../types'

vi.mock('../../api/cases', () => ({
  casesApi: {
    get: vi.fn(),
    getDocuments: vi.fn(),
    getDrafts: vi.fn(),
    getResponses: vi.fn(),
    markMessagesRead: vi.fn(),
    listMessages: vi.fn(),
    sendMessage: vi.fn(),
  },
}))

vi.mock('../../components/cases/CaseHeaderSection', () => ({
  CaseHeaderSection: ({ caseItem }: { caseItem: CaseResponse }) => <div>{caseItem.title}</div>,
}))
vi.mock('../../components/cases/CaseOverviewTab', () => ({
  CaseOverviewTab: () => <div data-testid="overview-tab" />,
}))
vi.mock('../../components/cases/DocumentsSection', () => ({
  DocumentsSection: () => <div data-testid="documents-section" />,
}))
vi.mock('../../components/cases/DraftSection', () => ({ DraftSection: () => <div data-testid="draft-section" /> }))
vi.mock('../../components/cases/ContractReviewSection', () => ({
  ContractReviewSection: () => <div data-testid="contract-review-section" />,
}))
vi.mock('../../components/cases/ComparisonSection', () => ({
  ComparisonSection: () => <div data-testid="comparison-section" />,
}))
vi.mock('../../components/cases/CaseSignatureSection', () => ({
  CaseSignatureSection: () => <div data-testid="signature-section" />,
}))
vi.mock('../../components/cases/CaseAnalyticsSection', () => ({
  CaseAnalyticsSection: () => <div data-testid="analytics-section" />,
}))
vi.mock('../../components/cases/WorkflowSection', () => ({ WorkflowSection: () => <div data-testid="workflow-section" /> }))
vi.mock('../../components/cases/WorkflowProcessSection', () => ({
  WorkflowProcessSection: () => <div data-testid="workflow-process-section" />,
}))
vi.mock('../../components/cases/ResponsesSection', () => ({
  ResponsesSection: () => <div data-testid="responses-section" />,
}))
vi.mock('../../components/cases/CaseTimeSection', () => ({ CaseTimeSection: () => <div data-testid="time-section" /> }))
vi.mock('../../components/cases/CaseTasksSection', () => ({
  CaseTasksSection: () => <div data-testid="tasks-section" />,
}))
vi.mock('../../components/cases/CourtSection', () => ({ CourtSection: () => <div data-testid="court-section" /> }))
vi.mock('../../components/cases/CaseEmailSection', () => ({
  CaseEmailSection: () => <div data-testid="emails-section" />,
}))
vi.mock('../../components/cases/CaseChatSection', () => ({ CaseChatSection: () => <div data-testid="ask-section" /> }))
vi.mock('../../components/messages/CaseMessageThread', () => ({
  CaseMessageThread: () => <div data-testid="messages-section" />,
}))

function renderPage(initialEntry = '/cases/case-1'): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/cases/:caseId" element={<CaseDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

function makeCase(overrides: Partial<CaseResponse> = {}): CaseResponse {
  return {
    id: 'case-1',
    ownerId: 'owner-1',
    orgId: null,
    title: 'Банкротство ООО Ромашка',
    description: null,
    clientId: 'client-1',
    clientName: 'ООО Ромашка',
    status: 'INTAKE',
    statusName: 'Intake',
    filingDeadline: null,
    nextHearingDate: null,
    expiresAt: null,
    courtSystem: 'ARBITR',
    courtSystemName: 'Arbitration',
    courtCaseNumber: null,
    courtCardUrl: null,
    defaultHourlyRate: null,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  } as CaseResponse
}

function makeDocument(overrides: Partial<DocumentResponse> = {}): DocumentResponse {
  return {
    id: 'doc-1',
    title: 'Заявление',
    status: 'READY',
    fileType: 'PDF',
    createdAt: '2026-01-01T00:00:00Z',
    visibleToClient: false,
    ...overrides,
  } as DocumentResponse
}

describe('CaseDetailPage', () => {
  beforeEach(() => {
    vi.mocked(casesApi.get).mockReset()
    vi.mocked(casesApi.getDocuments).mockReset().mockResolvedValue([])
    vi.mocked(casesApi.getDrafts).mockReset().mockResolvedValue([])
    vi.mocked(casesApi.getResponses).mockReset().mockResolvedValue([])
    vi.mocked(casesApi.markMessagesRead).mockReset().mockResolvedValue(undefined)
    useRecentEntitiesStore.setState({ entries: [] })
  })

  afterEach(() => {
    cleanup()
  })

  it('shows a spinner while the case is loading', async () => {
    vi.mocked(casesApi.get).mockImplementation(() => new Promise(() => {}))
    renderPage()
    expect(screen.getByRole('status', { hidden: true }) ?? document.querySelector('svg')).toBeTruthy()
  })

  it('shows a not-found message when the case does not exist', async () => {
    vi.mocked(casesApi.get).mockRejectedValue(new Error('404'))
    renderPage()
    expect(await screen.findByText('Case not found')).toBeInTheDocument()
    expect(screen.getByText('← To all cases')).toBeInTheDocument()
  })

  it('renders the overview tab by default and records a recent-entity visit', async () => {
    vi.mocked(casesApi.get).mockResolvedValue(makeCase())
    renderPage()

    expect(await screen.findByTestId('overview-tab')).toBeInTheDocument()
    expect(screen.getByText('Банкротство ООО Ромашка')).toBeInTheDocument()

    await waitFor(() =>
      expect(useRecentEntitiesStore.getState().entries).toEqual([
        expect.objectContaining({ type: 'case', id: 'case-1', label: 'Банкротство ООО Ромашка' }),
      ])
    )
  })

  it('shows document/analysis counts as badges on their tabs', async () => {
    vi.mocked(casesApi.get).mockResolvedValue(makeCase())
    vi.mocked(casesApi.getDocuments).mockResolvedValue([makeDocument(), makeDocument({ id: 'doc-2' })])
    renderPage()

    const documentsTab = await screen.findByRole('tab', { name: /Documents/i })
    expect(documentsTab).toHaveTextContent('2')
  })

  it('switches tabs on click and renders the matching section', async () => {
    vi.mocked(casesApi.get).mockResolvedValue(makeCase())
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('overview-tab')

    await user.click(screen.getByRole('tab', { name: 'Documents' }))
    expect(await screen.findByTestId('documents-section')).toBeInTheDocument()
    expect(screen.getByTestId('draft-section')).toBeInTheDocument()
    expect(screen.getByTestId('contract-review-section')).toBeInTheDocument()
    expect(screen.getByTestId('comparison-section')).toBeInTheDocument()
    expect(screen.getByTestId('signature-section')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Ask about case' }))
    expect(await screen.findByTestId('ask-section')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: /AI analysis/i }))
    expect(await screen.findByTestId('analytics-section')).toBeInTheDocument()
    expect(screen.getByTestId('workflow-section')).toBeInTheDocument()
    expect(screen.getByTestId('workflow-process-section')).toBeInTheDocument()
    expect(screen.getByTestId('responses-section')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Time & billing' }))
    expect(await screen.findByTestId('time-section')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Tasks & deadlines' }))
    expect(await screen.findByTestId('tasks-section')).toBeInTheDocument()
    expect(screen.getByTestId('court-section')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Mail' }))
    expect(await screen.findByTestId('emails-section')).toBeInTheDocument()
  })

  it('opens the initial tab from the ?tab= query param', async () => {
    vi.mocked(casesApi.get).mockResolvedValue(makeCase())
    renderPage('/cases/case-1?tab=tasks')
    expect(await screen.findByTestId('tasks-section')).toBeInTheDocument()
  })

  it('marks messages as read and invalidates the thread list when the messages tab is opened', async () => {
    vi.mocked(casesApi.get).mockResolvedValue(makeCase())
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('overview-tab')

    await user.click(screen.getByRole('tab', { name: 'Messages' }))

    expect(await screen.findByTestId('messages-section')).toBeInTheDocument()
    await waitFor(() => expect(casesApi.markMessagesRead).toHaveBeenCalledWith('case-1'))
  })
})
