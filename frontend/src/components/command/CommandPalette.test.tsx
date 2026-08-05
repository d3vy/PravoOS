import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../i18n'
import { CommandPalette } from './CommandPalette'
import { useCommandPaletteStore } from '../../store/commandPaletteStore'
import { useRecentEntitiesStore } from '../../store/recentEntitiesStore'
import { casesApi } from '../../api/cases'
import { searchApi } from '../../api/search'
import { timeApi } from '../../api/time'
import type { CaseResponse, GlobalSearchResponse } from '../../types'

vi.mock('../../api/cases', () => ({
  casesApi: {
    list: vi.fn(),
  },
}))

vi.mock('../../api/search', () => ({
  searchApi: {
    global: vi.fn(),
  },
}))

vi.mock('../../api/time', () => ({
  timeApi: {
    startTimer: vi.fn(),
  },
}))

const emptySearchResponse: GlobalSearchResponse = {
  cases: [],
  conversations: [],
  documents: [],
  clients: [],
  invoices: [],
}

function makeCase(overrides: Partial<CaseResponse>): CaseResponse {
  return {
    id: 'case-1',
    ownerId: 'owner-1',
    orgId: null,
    title: 'Дело по умолчанию',
    description: null,
    clientId: null,
    clientName: null,
    status: 'IN_PROGRESS',
    statusName: 'В работе',
    filingDeadline: null,
    nextHearingDate: null,
    expiresAt: null,
    courtSystem: 'ARBITR' as const,
    courtSystemName: 'КАД.Арбитр',
    courtCaseNumber: null,
    courtCardUrl: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function renderPalette(): void {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CommandPalette />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeAll(async () => {
  await i18n.changeLanguage('ru')
})

beforeEach(() => {
  useCommandPaletteStore.setState({ open: false })
  useRecentEntitiesStore.setState({ entries: [] })
  vi.mocked(searchApi.global).mockResolvedValue(emptySearchResponse)
  vi.mocked(casesApi.list).mockResolvedValue({ items: [], total: 0 })
  vi.mocked(timeApi.startTimer).mockResolvedValue(undefined as never)
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('CommandPalette', () => {
  it('renders nothing when closed', () => {
    renderPalette()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows the default action list when opened', () => {
    useCommandPaletteStore.setState({ open: true })
    renderPalette()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Новое дело')).toBeInTheDocument()
    expect(screen.getByText('Новый клиент')).toBeInTheDocument()
    expect(screen.getByText('Начать таймер')).toBeInTheDocument()
  })

  it('filters actions by query', async () => {
    useCommandPaletteStore.setState({ open: true })
    renderPalette()
    const input = screen.getByLabelText('Поиск и команды')
    await userEvent.type(input, 'клиент')
    await waitFor(() => {
      expect(screen.getByText('Новый клиент')).toBeInTheDocument()
      expect(screen.queryByText('Задать вопрос AI')).not.toBeInTheDocument()
    })
  })

  it('navigates the list with arrow keys and selects with Enter', async () => {
    useCommandPaletteStore.setState({ open: true })
    renderPalette()
    const input = screen.getByLabelText('Поиск и команды')
    await userEvent.type(input, 'клиент')
    await waitFor(() => expect(screen.getByText('Новый клиент')).toBeInTheDocument())

    await userEvent.keyboard('{ArrowDown}')
    await userEvent.keyboard('{Enter}')

    await waitFor(() => expect(useCommandPaletteStore.getState().open).toBe(false))
  })

  it('closes on Escape', async () => {
    useCommandPaletteStore.setState({ open: true })
    renderPalette()
    const input = screen.getByLabelText('Поиск и команды')
    await userEvent.click(input)
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(useCommandPaletteStore.getState().open).toBe(false))
  })

  it('shows clients and invoices groups from global search results', async () => {
    vi.mocked(searchApi.global).mockResolvedValue({
      ...emptySearchResponse,
      clients: [{ id: 'client-1', name: 'Иванов Иван', email: 'ivanov@example.com', phone: null }],
      invoices: [
        {
          id: 'invoice-1',
          number: 'INV-001',
          clientName: 'Иванов Иван',
          total: 1000,
          currency: 'RUB',
          status: 'ISSUED',
          statusName: 'Выставлен',
        },
      ],
    })
    useCommandPaletteStore.setState({ open: true })
    renderPalette()
    const input = screen.getByLabelText('Поиск и команды')
    await userEvent.type(input, 'иванов')

    await waitFor(() => {
      expect(screen.getByText('Клиенты')).toBeInTheDocument()
      expect(screen.getByText('Счета')).toBeInTheDocument()
      expect(screen.getAllByText('Иванов Иван').length).toBeGreaterThan(0)
      expect(screen.getByText('INV-001')).toBeInTheDocument()
    })
  })

  it('switches to the timer case picker and starts a timer for the chosen case', async () => {
    vi.mocked(casesApi.list).mockResolvedValue({
      items: [
        makeCase({ id: 'case-open', title: 'Открытое дело', status: 'IN_PROGRESS' }),
        makeCase({ id: 'case-closed', title: 'Закрытое дело', status: 'CLOSED_WON' }),
      ],
      total: 2,
    })
    useCommandPaletteStore.setState({ open: true })
    renderPalette()

    await userEvent.click(screen.getByText('Начать таймер'))

    await waitFor(() => {
      expect(screen.getByText('Открытое дело')).toBeInTheDocument()
    })
    expect(screen.queryByText('Закрытое дело')).not.toBeInTheDocument()

    await userEvent.click(screen.getByText('Открытое дело'))

    await waitFor(() => expect(timeApi.startTimer).toHaveBeenCalledWith('case-open', expect.any(Object)))
  })

  it('returns from the timer case picker to the root list on Escape', async () => {
    useCommandPaletteStore.setState({ open: true })
    renderPalette()

    await userEvent.click(screen.getByText('Начать таймер'))
    const timerInput = await screen.findByPlaceholderText('Выберите дело для таймера…')

    await userEvent.click(timerInput)
    await userEvent.keyboard('{Escape}')

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Поиск по делам, беседам, документам или команда…')).toBeInTheDocument()
    })
    expect(useCommandPaletteStore.getState().open).toBe(true)
  })
})
