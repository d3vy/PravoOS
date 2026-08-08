import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PortalInvoiceDetailPage from './PortalInvoiceDetailPage'
import { portalApi } from '../../api/portal'
import type { InvoiceResponse } from '../../types'

vi.mock('../../api/portal', () => ({
  portalApi: {
    getInvoice: vi.fn(),
    payInvoice: vi.fn(),
  },
}))

function renderPage(invoiceId = 'inv-1'): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/portal/invoices/${invoiceId}`]}>
        <Routes>
          <Route path="/portal/invoices/:invoiceId" element={<PortalInvoiceDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

function makeInvoice(overrides: Partial<InvoiceResponse> = {}): InvoiceResponse {
  return {
    id: 'inv-1',
    clientId: 'client-1',
    clientName: 'ООО Ромашка',
    number: '2026-001',
    status: 'ISSUED',
    statusLabel: 'Issued',
    issueDate: '2026-01-01',
    dueDate: '2026-02-01',
    currency: 'RUB',
    subtotal: 15000,
    vatRate: null,
    vatAmount: 0,
    total: 15000,
    notes: null,
    lines: [{ id: 'line-1', description: 'Consultation', minutes: 60, hourlyRate: 15000, amount: 15000 }],
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  } as InvoiceResponse
}

describe('PortalInvoiceDetailPage', () => {
  beforeEach(() => {
    vi.mocked(portalApi.getInvoice).mockReset()
    vi.mocked(portalApi.payInvoice).mockReset()
  })

  afterEach(() => {
    cleanup()
  })

  it('renders invoice lines and the total due', async () => {
    vi.mocked(portalApi.getInvoice).mockResolvedValue(makeInvoice())
    renderPage()

    expect(await screen.findByText('Consultation')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Invoice 2026-001' })).toBeInTheDocument()
    const amountCells = screen.getAllByText('RUB 15,000.00')
    expect(amountCells.length).toBeGreaterThan(0)
  })

  it('shows a pay button for an ISSUED invoice and redirects on success', async () => {
    vi.mocked(portalApi.getInvoice).mockResolvedValue(makeInvoice({ status: 'ISSUED' }))
    vi.mocked(portalApi.payInvoice).mockResolvedValue({
      invoiceId: 'inv-1',
      confirmationUrl: 'https://pay.example.com/checkout',
    })
    const user = userEvent.setup()
    const originalLocation = window.location
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...originalLocation, href: '' },
    })

    renderPage()
    await screen.findByRole('heading', { name: 'Invoice 2026-001' })

    await user.click(screen.getByRole('button', { name: 'Pay online' }))

    await vi.waitFor(() => expect(window.location.href).toBe('https://pay.example.com/checkout'))
    Object.defineProperty(window, 'location', { configurable: true, value: originalLocation })
  })

  it('does not show a pay button for a non-ISSUED invoice', async () => {
    vi.mocked(portalApi.getInvoice).mockResolvedValue(makeInvoice({ status: 'PAID' }))
    renderPage()
    await screen.findByRole('heading', { name: 'Invoice 2026-001' })
    expect(screen.queryByRole('button', { name: 'Pay online' })).not.toBeInTheDocument()
  })

  it('shows a payment error message when starting the payment fails', async () => {
    vi.mocked(portalApi.getInvoice).mockResolvedValue(makeInvoice({ status: 'ISSUED' }))
    vi.mocked(portalApi.payInvoice).mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('heading', { name: 'Invoice 2026-001' })

    await user.click(screen.getByRole('button', { name: 'Pay online' }))

    expect(await screen.findByText('Could not start the payment. Please try again later.')).toBeInTheDocument()
  })

  it('renders notes when present', async () => {
    vi.mocked(portalApi.getInvoice).mockResolvedValue(makeInvoice({ notes: 'Please pay by wire transfer.' }))
    renderPage()
    expect(await screen.findByText('Please pay by wire transfer.')).toBeInTheDocument()
  })
})
