import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BillingPage from './BillingPage'
import { billingApi } from '../../api/billing'
import type { BillingPlan, BillingStatus, PaymentRecord } from '../../types'

vi.mock('../../api/billing', () => ({
  billingApi: {
    status: vi.fn(),
    plans: vi.fn(),
    payments: vi.fn(),
    subscribe: vi.fn(),
    cancel: vi.fn(),
  },
}))

vi.mock('../../api/client', () => ({
  refreshSession: vi.fn().mockRejectedValue(new Error('no session')),
}))

const { askMock } = vi.hoisted(() => ({ askMock: vi.fn() }))
vi.mock('../../hooks/useConfirm', () => ({
  useConfirm: () => askMock,
}))

function setLocation(): { href: string } {
  const location = { href: '' }
  Object.defineProperty(window, 'location', { value: location, writable: true, configurable: true })
  return location
}

function renderPage(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <BillingPage />
    </QueryClientProvider>
  )
}

const activeStatus: BillingStatus = {
  planCode: 'pro',
  planName: 'Pro',
  status: 'ACTIVE',
  dailyRequests: 100,
  dailyTokens: 5000,
  seats: 3,
  trialEnd: null,
  currentPeriodEnd: '2026-09-01',
  cancelAtPeriodEnd: false,
} as BillingStatus

const plans: BillingPlan[] = [
  { code: 'free', name: 'Free', priceKopecks: 0, dailyRequests: 10, dailyTokens: 0, seats: 1 } as BillingPlan,
  { code: 'pro', name: 'Pro', priceKopecks: 990000, dailyRequests: 100, dailyTokens: 5000, seats: 3 } as BillingPlan,
]

const payments: PaymentRecord[] = []

describe('BillingPage', () => {
  beforeEach(() => {
    vi.mocked(billingApi.status).mockReset().mockResolvedValue(activeStatus)
    vi.mocked(billingApi.plans).mockReset().mockResolvedValue(plans)
    vi.mocked(billingApi.payments).mockReset().mockResolvedValue(payments)
    vi.mocked(billingApi.subscribe).mockReset()
    vi.mocked(billingApi.cancel).mockReset()
    askMock.mockReset()
  })

  afterEach(() => {
    cleanup()
  })

  it('renders the current plan and status once loaded', async () => {
    renderPage()
    expect(await screen.findByText('Current plan')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Pro · Active')
  })

  it('marks the current plan and shows a disabled default-plan button for the free tier', async () => {
    renderPage()
    await screen.findByText(/Pro ·/)
    expect(screen.getByText('Your current plan')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Default plan/i })).toBeDisabled()
  })

  it('cancel button asks for confirmation and cancels only when confirmed', async () => {
    askMock.mockResolvedValue(false)
    const user = userEvent.setup()
    renderPage()
    const cancelButton = await screen.findByRole('button', { name: /Cancel subscription/i })

    await user.click(cancelButton)

    expect(askMock).toHaveBeenCalledWith(expect.objectContaining({ danger: true }))
    expect(billingApi.cancel).not.toHaveBeenCalled()
  })

  it('cancels the subscription when the confirmation is accepted', async () => {
    askMock.mockResolvedValue(true)
    vi.mocked(billingApi.cancel).mockResolvedValue(undefined as never)
    const user = userEvent.setup()
    renderPage()
    const cancelButton = await screen.findByRole('button', { name: /Cancel subscription/i })

    await user.click(cancelButton)

    await waitFor(() => expect(billingApi.cancel).toHaveBeenCalled())
  })

  it('redirects to the confirmation URL when subscribing succeeds', async () => {
    const location = setLocation()
    vi.mocked(billingApi.status).mockResolvedValue({ ...activeStatus, planCode: 'free' })
    vi.mocked(billingApi.subscribe).mockResolvedValue({ confirmationUrl: 'https://pay.example/checkout' } as never)
    const user = userEvent.setup()
    renderPage()

    const payButtons = await screen.findAllByRole('button', { name: /Pay/i })
    await user.click(payButtons[0])

    await waitFor(() => expect(location.href).toBe('https://pay.example/checkout'))
  })

  it('shows an error when subscribing fails', async () => {
    vi.mocked(billingApi.status).mockResolvedValue({ ...activeStatus, planCode: 'free' })
    vi.mocked(billingApi.subscribe).mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()
    renderPage()

    const payButtons = await screen.findAllByRole('button', { name: /Pay/i })
    await user.click(payButtons[0])

    expect(await screen.findByText('Failed to create the payment. Please try again later.')).toBeInTheDocument()
  })
})
