import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import RecycleBinPage from './RecycleBinPage'
import { recycleBinApi } from '../../api/recycleBin'
import type { RecycleBinEntry } from '../../types'

vi.mock('../../api/recycleBin', () => ({
  recycleBinApi: {
    list: vi.fn(),
    items: vi.fn(),
    restore: vi.fn(),
  },
}))

vi.mock('../../store/confirmStore', () => ({
  useConfirmStore: { getState: () => ({ ask: vi.fn().mockResolvedValue(true) }) },
}))

const mockedRecycleBinApi = vi.mocked(recycleBinApi)

function entry(overrides: Partial<RecycleBinEntry> = {}): RecycleBinEntry {
  return {
    id: 'entry-1',
    orgId: 'org-1',
    ownerId: 'lawyer-1',
    entityType: 'CASE',
    entityId: 'case-1',
    title: 'Дело Иванова',
    area: 'CASES',
    deletedBy: 'lawyer-1',
    deletedByRole: 'LAWYER',
    deletedAt: new Date().toISOString(),
    purgeAfter: new Date().toISOString(),
    daysUntilPurge: 5,
    cascadeGroupId: 'group-1',
    cascadeRoot: true,
    nestedCount: 2,
    payload: {},
    ...overrides,
  }
}

function renderPage(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RecycleBinPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('RecycleBinPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedRecycleBinApi.list.mockResolvedValue({ items: [entry()], total: 1 })
    mockedRecycleBinApi.items.mockResolvedValue([])
    mockedRecycleBinApi.restore.mockResolvedValue(undefined)
  })

  it('lists deleted entries', async () => {
    renderPage()

    expect(await screen.findByText('Дело Иванова')).toBeInTheDocument()
  })

  it('shows an empty state when nothing is deleted', async () => {
    mockedRecycleBinApi.list.mockResolvedValue({ items: [], total: 0 })
    renderPage()

    expect(await screen.findByText(/пуста|empty/i)).toBeInTheDocument()
  })

  it('resets to the first page when a filter changes', async () => {
    renderPage()
    const user = userEvent.setup()
    await screen.findByText('Дело Иванова')

    await user.type(screen.getByPlaceholderText(/поиск|search/i), 'test')

    await waitFor(() =>
      expect(mockedRecycleBinApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: 'test' }),
        0
      )
    )
  })

  it('restores an entry after confirmation', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /восстановить|restore/i }))

    await waitFor(() =>
      expect(mockedRecycleBinApi.restore).toHaveBeenCalledWith('entry-1', expect.anything())
    )
  })

  it('expands nested cascade items on demand', async () => {
    mockedRecycleBinApi.items.mockResolvedValue([
      entry({ id: 'nested-1', title: 'Документ 1', entityType: 'DOCUMENT', area: 'DOCUMENTS' }),
    ])
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /2/ }))

    expect(await screen.findByText('Документ 1')).toBeInTheDocument()
    expect(mockedRecycleBinApi.items).toHaveBeenCalledWith('entry-1')
  })
})
