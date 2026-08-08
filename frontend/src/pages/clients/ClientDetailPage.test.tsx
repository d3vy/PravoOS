import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClientDetailPage from './ClientDetailPage'
import { clientsApi } from '../../api/clients'
import { useRecentEntitiesStore } from '../../store/recentEntitiesStore'
import type { CaseResponse, ClientDetailResponse, ClientResponse } from '../../types'

vi.mock('../../api/clients', () => ({
  clientsApi: {
    get: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    checkConflicts: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../../components/clients/ClientPortalSection', () => ({
  ClientPortalSection: () => <div data-testid="portal-section" />,
}))
vi.mock('../../components/clients/ClientContactsSection', () => ({
  ClientContactsSection: () => <div data-testid="contacts-section" />,
}))
vi.mock('../../components/clients/ClientEmailSection', () => ({
  ClientEmailSection: () => <div data-testid="email-section" />,
}))

const navigateMock = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => navigateMock }
})

function renderPage(clientId = 'client-1'): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/clients/${clientId}`]}>
        <Routes>
          <Route path="/clients/:clientId" element={<ClientDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

function makeClient(overrides: Partial<ClientResponse> = {}): ClientResponse {
  return {
    id: 'client-1',
    name: 'ООО Ромашка',
    type: 'COMPANY',
    typeName: 'Компания',
    phone: '+7 900 000-00-00',
    email: 'client@example.com',
    inn: '7701234567',
    notes: null,
    createdAt: '2026-01-01T00:00:00Z',
    caseCount: 0,
    ...overrides,
  } as ClientResponse
}

function makeCase(overrides: Partial<CaseResponse> = {}): CaseResponse {
  return {
    id: 'case-1',
    title: 'Дело №1',
    status: 'INTAKE',
    createdAt: '2026-01-05T00:00:00Z',
    ...overrides,
  } as CaseResponse
}

function makeDetail(overrides: Partial<ClientDetailResponse> = {}): ClientDetailResponse {
  return { client: makeClient(), cases: [], ...overrides }
}

describe('ClientDetailPage', () => {
  beforeEach(() => {
    vi.mocked(clientsApi.get).mockReset()
    vi.mocked(clientsApi.update).mockReset()
    vi.mocked(clientsApi.delete).mockReset()
    navigateMock.mockReset()
    useRecentEntitiesStore.setState({ entries: [] })
  })

  afterEach(() => {
    cleanup()
  })

  it('shows a not-found message when the client does not exist', async () => {
    vi.mocked(clientsApi.get).mockRejectedValue(new Error('404'))
    renderPage()
    expect(await screen.findByText('Client not found')).toBeInTheDocument()
  })

  it('renders client details and records a recent-entity visit', async () => {
    vi.mocked(clientsApi.get).mockResolvedValue(makeDetail())
    renderPage()

    expect(await screen.findByRole('heading', { name: 'ООО Ромашка' })).toBeInTheDocument()
    expect(screen.getByText('+7 900 000-00-00')).toBeInTheDocument()
    expect(screen.getByText('7701234567')).toBeInTheDocument()
    expect(screen.getByTestId('portal-section')).toBeInTheDocument()
    expect(screen.getByTestId('contacts-section')).toBeInTheDocument()
    expect(screen.getByTestId('email-section')).toBeInTheDocument()

    await waitFor(() =>
      expect(useRecentEntitiesStore.getState().entries).toEqual([
        expect.objectContaining({ type: 'client', id: 'client-1', label: 'ООО Ромашка' }),
      ])
    )
  })

  it('shows the empty-cases placeholder and lists linked cases otherwise', async () => {
    vi.mocked(clientsApi.get).mockResolvedValue(makeDetail())
    renderPage()
    expect(await screen.findByText('No cases are linked to this client yet.')).toBeInTheDocument()

    cleanup()
    vi.mocked(clientsApi.get).mockResolvedValue(makeDetail({ cases: [makeCase()] }))
    renderPage()
    expect(await screen.findByText('Дело №1')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Дело №1/ })).toHaveAttribute('href', '/cases/case-1')
  })

  it('edits the client and saves changes', async () => {
    vi.mocked(clientsApi.get).mockResolvedValue(makeDetail())
    vi.mocked(clientsApi.update).mockResolvedValue(makeClient({ name: 'ООО Ромашка 2' }))
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('heading', { name: 'ООО Ромашка' })

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const nameInput = await screen.findByLabelText(/Name/i)
    await user.clear(nameInput)
    await user.type(nameInput, 'RR')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(clientsApi.update).toHaveBeenCalledWith('client-1', expect.objectContaining({ name: 'RR' })))
  })

  it('shows an update error when saving fails', async () => {
    vi.mocked(clientsApi.get).mockResolvedValue(makeDetail())
    vi.mocked(clientsApi.update).mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('heading', { name: 'ООО Ромашка' })

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Failed to save changes. Check the data.')).toBeInTheDocument()
  })

  it('deletes a client with no cases directly', async () => {
    vi.mocked(clientsApi.get).mockResolvedValue(makeDetail())
    vi.mocked(clientsApi.delete).mockResolvedValue(undefined as never)
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('heading', { name: 'ООО Ромашка' })

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    const dialog = screen.getByText('Delete client?').closest('div') as HTMLElement
    await user.click(within(dialog.parentElement as HTMLElement).getByRole('button', { name: 'Delete client' }))

    await waitFor(() => expect(clientsApi.delete).toHaveBeenCalledWith('client-1', false))
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/clients'))
  })

  it('offers keep-cases / delete-cases choices when the client has cases', async () => {
    vi.mocked(clientsApi.get).mockResolvedValue(makeDetail({ cases: [makeCase()] }))
    vi.mocked(clientsApi.delete).mockResolvedValue(undefined as never)
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('heading', { name: 'ООО Ромашка' })

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('The client has 1 case. Choose what to do with it.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Delete client and all cases' }))

    await waitFor(() => expect(clientsApi.delete).toHaveBeenCalledWith('client-1', true))
  })
})
