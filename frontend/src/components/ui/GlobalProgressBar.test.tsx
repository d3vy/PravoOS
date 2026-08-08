import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GlobalProgressBar } from './GlobalProgressBar'

afterEach(cleanup)

function renderWithClient(client: QueryClient, children: React.ReactNode) {
  return render(<QueryClientProvider client={client}>{children}</QueryClientProvider>)
}

describe('GlobalProgressBar', () => {
  it('renders nothing when nothing is fetching or mutating', () => {
    const client = new QueryClient()
    renderWithClient(client, <GlobalProgressBar />)
    expect(screen.queryByRole('status', { name: 'loading' })).toBeNull()
  })

  it('shows the bar while a query is fetching', async () => {
    const client = new QueryClient()
    client.fetchQuery({
      queryKey: ['probe'],
      queryFn: () => new Promise((resolve) => setTimeout(() => resolve('ok'), 50)),
    })

    renderWithClient(client, <GlobalProgressBar />)

    await waitFor(() => {
      expect(screen.getByRole('status', { name: 'loading' })).toBeInTheDocument()
    })
  })
})
