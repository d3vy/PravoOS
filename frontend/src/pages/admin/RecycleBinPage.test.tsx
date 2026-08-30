import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import RecycleBinPage from './RecycleBinPage'
import { adminApi } from '../../api/admin'
import type { RecycleBinEntry } from '../../types'

vi.mock('../../api/admin', async () => {
  const actual = await vi.importActual<typeof import('../../api/admin')>('../../api/admin')
  return {
    ...actual,
    adminApi: {
      getRecycleBin: vi.fn(),
      restoreRecycleBinEntry: vi.fn(),
      purgeRecycleBinEntry: vi.fn(),
    },
  }
})

vi.mock('../../store/confirmStore', () => ({
  useConfirmStore: { getState: () => ({ ask: vi.fn().mockResolvedValue(true) }) },
}))

const mockedAdminApi = vi.mocked(adminApi)

function entry(overrides: Partial<RecycleBinEntry> = {}): RecycleBinEntry {
  return {
    id: 'entry-1',
    orgId: 'org-1',
    ownerId: 'lawyer-1',
    entityType: 'CLIENT',
    entityId: 'client-1',
    title: 'Клиент Петров',
    area: 'CLIENTS',
    deletedBy: 'lawyer-1',
    deletedByRole: 'LAWYER',
    deletedAt: new Date().toISOString(),
    purgeAfter: new Date().toISOString(),
    daysUntilPurge: 3,
    cascadeGroupId: 'group-1',
    cascadeRoot: true,
    nestedCount: 0,
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

describe('Admin RecycleBinPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedAdminApi.getRecycleBin.mockResolvedValue({ items: [entry()], total: 1 })
    mockedAdminApi.restoreRecycleBinEntry.mockResolvedValue(undefined)
    mockedAdminApi.purgeRecycleBinEntry.mockResolvedValue(undefined)
  })

  it('lists deleted entries across organizations', async () => {
    renderPage()

    expect(await screen.findByText('Клиент Петров')).toBeInTheDocument()
  })

  it('passes the org filter to the api and resets to the first page', async () => {
    renderPage()
    const user = userEvent.setup()
    await screen.findByText('Клиент Петров')

    await user.type(screen.getByPlaceholderText('Org ID'), 'org-9')

    await waitFor(() =>
      expect(mockedAdminApi.getRecycleBin).toHaveBeenLastCalledWith(
        expect.objectContaining({ orgId: 'org-9' }),
        0
      )
    )
  })

  it('restores an entry after confirmation', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /восстановить|restore/i }))

    await waitFor(() =>
      expect(mockedAdminApi.restoreRecycleBinEntry).toHaveBeenCalledWith('entry-1', expect.anything())
    )
  })

  it('purges an entry permanently after confirmation', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /удалить навсегда|delete forever/i }))

    await waitFor(() => expect(mockedAdminApi.purgeRecycleBinEntry).toHaveBeenCalledWith('entry-1'))
  })
})
