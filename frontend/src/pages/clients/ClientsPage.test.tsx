import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClientsPage from './ClientsPage'
import { clientsApi } from '../../api/clients'
import { organizationsApi } from '../../api/organizations'
import { savedViewsApi } from '../../api/savedViews'
import type { ClientResponse } from '../../types'

vi.mock('../../api/clients', () => ({
  clientsApi: {
    list: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
    checkConflicts: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../../api/organizations', () => ({
  organizationsApi: {
    list: vi.fn(),
  },
}))

vi.mock('../../api/savedViews', () => ({
  savedViewsApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

const navigateMock = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => navigateMock }
})

function renderPage(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/clients']}>
        <ClientsPage />
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
    caseCount: 2,
    ...overrides,
  } as ClientResponse
}

describe('ClientsPage', () => {
  beforeEach(() => {
    vi.mocked(clientsApi.list).mockReset()
    vi.mocked(clientsApi.create).mockReset()
    vi.mocked(clientsApi.delete).mockReset()
    vi.mocked(organizationsApi.list).mockReset().mockResolvedValue([])
    vi.mocked(savedViewsApi.list).mockReset().mockResolvedValue([])
    navigateMock.mockReset()
    localStorage.clear()
  })

  afterEach(() => {
    cleanup()
  })

  it('renders the clients returned by the API', async () => {
    vi.mocked(clientsApi.list).mockResolvedValue({ items: [makeClient()], total: 1 })
    renderPage()
    expect(await screen.findByText('ООО Ромашка')).toBeInTheDocument()
  })

  it('shows an empty state with a call to action when there are no clients', async () => {
    vi.mocked(clientsApi.list).mockResolvedValue({ items: [], total: 0 })
    renderPage()
    expect(await screen.findByText('Create your first client to link cases and invoices to them.')).toBeInTheDocument()
  })

  it('filters the current page client-side by name/email/phone/inn', async () => {
    vi.mocked(clientsApi.list).mockResolvedValue({
      items: [makeClient(), makeClient({ id: 'client-2', name: 'ИП Иванов', email: 'ivanov@example.com', inn: '111' })],
      total: 2,
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('ООО Ромашка')

    await user.type(screen.getByPlaceholderText('Search by name, email, phone…'), 'Иванов')

    await waitFor(() => expect(screen.queryByText('ООО Ромашка')).not.toBeInTheDocument())
    expect(screen.getByText('ИП Иванов')).toBeInTheDocument()
  })

  it('navigates to the client detail page when a row is clicked', async () => {
    vi.mocked(clientsApi.list).mockResolvedValue({ items: [makeClient()], total: 1 })
    const user = userEvent.setup()
    renderPage()
    const nameCell = await screen.findByText('ООО Ромашка')
    await user.click(nameCell)
    expect(navigateMock).toHaveBeenCalledWith('/clients/client-1')
  })

  it('opens the create-client modal and submits a new client', async () => {
    vi.mocked(clientsApi.list).mockResolvedValue({ items: [], total: 0 })
    vi.mocked(clientsApi.create).mockResolvedValue(makeClient({ name: 'New Co' }))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Create your first client to link cases and invoices to them.')

    await user.click(screen.getAllByRole('button', { name: 'New client' })[0])
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Name/i), 'NC')
    await user.click(within(dialog).getByRole('button', { name: /Create/i }))

    await waitFor(() => expect(clientsApi.create).toHaveBeenCalled(), { timeout: 5000 })
  })

  it('shows a create-error message in the modal when creation fails', async () => {
    vi.mocked(clientsApi.list).mockResolvedValue({ items: [], total: 0 })
    vi.mocked(clientsApi.create).mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Create your first client to link cases and invoices to them.')

    await user.click(screen.getAllByRole('button', { name: 'New client' })[0])
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Name/i), 'NC')
    await user.click(within(dialog).getByRole('button', { name: /Create/i }))

    expect(
      await within(dialog).findByText('Could not create the client. Check the data and try again.')
    ).toBeInTheDocument()
  })

  it('selecting a row shows the bulk action bar, and deleting clears the selection', async () => {
    vi.mocked(clientsApi.list).mockResolvedValue({ items: [makeClient()], total: 1 })
    vi.mocked(clientsApi.delete).mockResolvedValue(undefined as never)
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('ООО Ромашка')

    await user.click(screen.getByRole('checkbox', { name: /Select client "ООО Ромашка"/i }))
    expect(await screen.findByText('Selected: 1')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Delete…' }))
    await user.click(screen.getByRole('button', { name: 'Delete clients, keep cases' }))

    await waitFor(() => expect(clientsApi.delete).toHaveBeenCalledWith('client-1', false), { timeout: 5000 })
  })
})
