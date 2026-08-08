import { cleanup, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PortalInvoicesPage from './PortalInvoicesPage'
import { portalApi } from '../../api/portal'
import type { InvoiceSummary } from '../../types'

vi.mock('../../api/portal', () => ({
  portalApi: {
    listInvoices: vi.fn(),
  },
}))

function renderPage(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/portal/invoices']}>
        <PortalInvoicesPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const invoice: InvoiceSummary = {
  id: 'inv-1',
  clientId: 'client-1',
  clientName: 'ООО «Ромашка»',
  number: '2026-001',
  status: 'ISSUED',
  statusLabel: 'Выставлен',
  issueDate: '2026-01-01',
  dueDate: null,
  currency: 'RUB',
  total: 15000,
  createdAt: '2026-01-01T10:00:00',
}

describe('PortalInvoicesPage', () => {
  beforeEach(() => {
    vi.mocked(portalApi.listInvoices).mockReset()
  })

  afterEach(() => {
    cleanup()
  })

  it('shows an empty state when there are no invoices', async () => {
    vi.mocked(portalApi.listInvoices).mockResolvedValue([])
    renderPage()
    expect(await screen.findByText('You have no invoices yet.')).toBeInTheDocument()
  })

  it('renders a link per invoice with its number and formatted total', async () => {
    vi.mocked(portalApi.listInvoices).mockResolvedValue([invoice])
    renderPage()
    const link = await screen.findByRole('link', { name: /2026-001/ })
    expect(link).toHaveAttribute('href', '/portal/invoices/inv-1')
    expect(link.textContent).toMatch(/15,000/)
  })

  it('shows an error message when the query fails', async () => {
    vi.mocked(portalApi.listInvoices).mockRejectedValue(new Error('network error'))
    renderPage()
    expect(await screen.findByText('Could not load invoices. Try refreshing the page.')).toBeInTheDocument()
  })
})
