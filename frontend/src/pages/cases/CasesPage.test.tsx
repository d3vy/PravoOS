import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CasesPage from './CasesPage'
import { casesApi } from '../../api/cases'
import { clientsApi } from '../../api/clients'
import { organizationsApi } from '../../api/organizations'
import { savedViewsApi } from '../../api/savedViews'
import type { CaseResponse } from '../../types'

vi.mock('../../api/cases', () => ({
  casesApi: {
    list: vi.fn(),
    create: vi.fn(),
    updateStatus: vi.fn(),
    changeOrg: vi.fn(),
    transferOwner: vi.fn(),
  },
}))

vi.mock('../../api/clients', () => ({
  clientsApi: {
    getAll: vi.fn(),
  },
}))

vi.mock('../../api/organizations', () => ({
  organizationsApi: {
    list: vi.fn(),
    members: vi.fn(),
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
      <MemoryRouter initialEntries={['/cases']}>
        <CasesPage />
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
    clientId: null,
    clientName: null,
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

describe('CasesPage', () => {
  beforeEach(() => {
    vi.mocked(casesApi.list).mockReset()
    vi.mocked(casesApi.create).mockReset()
    vi.mocked(casesApi.updateStatus).mockReset()
    vi.mocked(casesApi.changeOrg).mockReset()
    vi.mocked(casesApi.transferOwner).mockReset()
    vi.mocked(clientsApi.getAll).mockReset().mockResolvedValue([])
    vi.mocked(organizationsApi.list).mockReset().mockResolvedValue([])
    vi.mocked(organizationsApi.members).mockReset().mockResolvedValue([])
    vi.mocked(savedViewsApi.list).mockReset().mockResolvedValue([])
    navigateMock.mockReset()
    localStorage.clear()
  })

  afterEach(() => {
    cleanup()
  })

  it('renders the cases returned by the API', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [makeCase()], total: 1 })
    renderPage()
    expect(await screen.findByText('Банкротство ООО Ромашка')).toBeInTheDocument()
  })

  it('shows an empty state with a call to action when there are no cases', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [], total: 0 })
    renderPage()
    expect(await screen.findByText('No cases yet')).toBeInTheDocument()
    expect(screen.getByText('Create your first case to upload documents and run AI analysis.')).toBeInTheDocument()
  })

  it('filters by status using the filter chips', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [makeCase()], total: 1 })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Банкротство ООО Ромашка')

    await user.click(screen.getByRole('button', { name: 'In progress' }))

    await waitFor(() =>
      expect(casesApi.list).toHaveBeenLastCalledWith('IN_PROGRESS', undefined, 0, expect.any(Number), undefined)
    )
  })

  it('debounces search input before querying the API', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [makeCase()], total: 1 })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Банкротство ООО Ромашка')

    await user.type(screen.getByPlaceholderText('Search by title, description or client…'), 'Ромашка')

    await waitFor(
      () => expect(casesApi.list).toHaveBeenLastCalledWith(undefined, 'Ромашка', 0, expect.any(Number), undefined),
      { timeout: 3000 }
    )
  })

  it('navigates to the case detail page when a row is clicked', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [makeCase()], total: 1 })
    const user = userEvent.setup()
    renderPage()
    const titleCell = await screen.findByText('Банкротство ООО Ромашка')
    await user.click(titleCell)
    expect(navigateMock).toHaveBeenCalledWith('/cases/case-1')
  })

  it('opens the create-case modal and submits a new case', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [], total: 0 })
    vi.mocked(casesApi.create).mockResolvedValue(makeCase({ title: 'New case' }))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('No cases yet')

    await user.click(screen.getAllByRole('button', { name: 'New case' })[0])
    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Case title'), { target: { value: 'New case' } })
    await user.click(within(dialog).getByRole('button', { name: 'Create case' }))

    await waitFor(() => expect(casesApi.create).toHaveBeenCalled(), { timeout: 5000 })
    expect(casesApi.create).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'New case' }),
      expect.anything()
    )
  })

  it('shows a validation error when submitting the create form without a title', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [], total: 0 })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('No cases yet')

    await user.click(screen.getAllByRole('button', { name: 'New case' })[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Create case' }))

    expect(await within(dialog).findByText('Enter a case title')).toBeInTheDocument()
    expect(casesApi.create).not.toHaveBeenCalled()
  })

  it('shows a create-error message in the modal when creation fails', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [], total: 0 })
    vi.mocked(casesApi.create).mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('No cases yet')

    await user.click(screen.getAllByRole('button', { name: 'New case' })[0])
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Case title'), 'New case')
    await user.click(within(dialog).getByRole('button', { name: 'Create case' }))

    expect(
      await within(dialog).findByText('Failed to create the case. Please try again.')
    ).toBeInTheDocument()
  })

  it('switches to the cards view and selecting a card shows the bulk action bar', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [makeCase()], total: 1 })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Банкротство ООО Ромашка')

    await user.click(screen.getByRole('button', { name: 'List' }))
    await screen.findByRole('checkbox', { name: /Select case/i })

    await user.click(screen.getByRole('checkbox', { name: /Select case.*Банкротство ООО Ромашка/i }))
    expect(await screen.findAllByText('Selected: 1')).toHaveLength(2)

    await user.click(screen.getByRole('button', { name: 'Deselect' }))
    await waitFor(() => expect(screen.queryByText('Selected: 1')).not.toBeInTheDocument())
  })

  it('applies a bulk status change to selected cases', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({ items: [makeCase()], total: 1 })
    vi.mocked(casesApi.updateStatus).mockResolvedValue(makeCase({ status: 'IN_PROGRESS' }))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Банкротство ООО Ромашка')

    await user.click(screen.getByRole('button', { name: 'List' }))
    await user.click(await screen.findByRole('checkbox', { name: /Select case.*Банкротство ООО Ромашка/i }))
    await screen.findAllByText('Selected: 1')

    await user.selectOptions(screen.getByDisplayValue('Change status…'), 'IN_PROGRESS')

    await waitFor(() => expect(casesApi.updateStatus).toHaveBeenCalledWith('case-1', 'IN_PROGRESS'))
  })

  it('renders the board view grouped by status', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({
      items: [makeCase(), makeCase({ id: 'case-2', title: 'Второе дело', status: 'IN_PROGRESS' })],
      total: 2,
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Банкротство ООО Ромашка')

    await user.click(screen.getByRole('button', { name: 'Board' }))

    expect(await screen.findByText('Второе дело')).toBeInTheDocument()
    expect(screen.getByText('Банкротство ООО Ромашка')).toBeInTheDocument()
  })
})
