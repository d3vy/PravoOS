import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AiStatsPage from './AiStatsPage'
import { aiStatsApi } from '../../api/aiStats'
import { adminApi } from '../../api/admin'
import type { AiStatsResponse } from '../../types'

vi.mock('../../api/aiStats', () => ({
  aiStatsApi: {
    getStats: vi.fn(),
    getRecentResponses: vi.fn(),
  },
}))

vi.mock('../../api/admin', async () => {
  const actual = await vi.importActual<typeof import('../../api/admin')>('../../api/admin')
  return {
    ...actual,
    adminApi: {
      ...actual.adminApi,
      getClientStats: vi.fn(),
    },
  }
})

const mockedAiStatsApi = vi.mocked(aiStatsApi)
const mockedAdminApi = vi.mocked(adminApi)

function stats(overrides: Partial<AiStatsResponse> = {}): AiStatsResponse {
  return {
    totalResponses: 0,
    ratedResponses: 0,
    positiveRatings: 0,
    negativeRatings: 0,
    guardChecks: 0,
    guardRefusals: 0,
    citationsChecked: 0,
    citationsVerified: 0,
    workflows: [],
    agentTools: [],
    ...overrides,
  }
}

function renderPage(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AiStatsPage />
    </QueryClientProvider>
  )
}

describe('AiStatsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedAiStatsApi.getRecentResponses.mockResolvedValue([])
    mockedAdminApi.getClientStats.mockResolvedValue({ newThisWeek: 0, totalActive: 0 })
  })

  it('shows a placeholder when the agent has no proposals yet', async () => {
    mockedAiStatsApi.getStats.mockResolvedValue(stats())
    renderPage()

    expect(await screen.findByText('The agent has not created any proposals yet.')).toBeInTheDocument()
  })

  it('renders the per-tool proposal funnel with an approval rate', async () => {
    mockedAiStatsApi.getStats.mockResolvedValue(
      stats({
        agentTools: [
          { toolName: 'create_client', created: 10, approved: 6, rejected: 2, expired: 1, failed: 1 },
        ],
      })
    )
    renderPage()

    expect(await screen.findByText('Creating a client')).toBeInTheDocument()
    expect(screen.getByText('60%')).toBeInTheDocument()
  })
})
