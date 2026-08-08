import { cleanup, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PortalCasesPage from './PortalCasesPage'
import { portalApi } from '../../api/portal'
import type { PortalCaseResponse } from '../../types'

vi.mock('../../api/portal', () => ({
  portalApi: {
    listCases: vi.fn(),
  },
}))

function renderPage(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/portal']}>
        <PortalCasesPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const caseItem: PortalCaseResponse = {
  id: 'case-1',
  title: 'Спор о поставке',
  description: null,
  status: 'IN_PROGRESS',
  statusName: 'В работе',
  filingDeadline: null,
  nextHearingDate: null,
  createdAt: '2026-08-01T10:00:00',
}

describe('PortalCasesPage', () => {
  beforeEach(() => {
    vi.mocked(portalApi.listCases).mockReset()
  })

  afterEach(() => {
    cleanup()
  })

  it('shows an empty state when there are no cases', async () => {
    vi.mocked(portalApi.listCases).mockResolvedValue([])
    renderPage()
    expect(await screen.findByText('You have no cases yet. Your lawyer will add them here.')).toBeInTheDocument()
  })

  it('renders a link per case with its title', async () => {
    vi.mocked(portalApi.listCases).mockResolvedValue([caseItem])
    renderPage()
    const link = await screen.findByRole('link', { name: /Спор о поставке/ })
    expect(link).toHaveAttribute('href', '/portal/cases/case-1')
  })

  it('shows an error message when the query fails', async () => {
    vi.mocked(portalApi.listCases).mockRejectedValue(new Error('network error'))
    renderPage()
    expect(await screen.findByText('Could not load cases. Try refreshing the page.')).toBeInTheDocument()
  })
})
