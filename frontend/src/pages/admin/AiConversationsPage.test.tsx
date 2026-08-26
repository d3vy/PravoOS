import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import AiConversationsPage from './AiConversationsPage'
import { adminApi } from '../../api/admin'
import type { AdminConversationResponse, MessageResponse } from '../../types'

vi.mock('../../api/admin', async () => {
  const actual = await vi.importActual<typeof import('../../api/admin')>('../../api/admin')
  return {
    ...actual,
    adminApi: {
      getConversations: vi.fn(),
      getConversationMessages: vi.fn(),
    },
  }
})

const mockedAdminApi = vi.mocked(adminApi)

function conversation(overrides: Partial<AdminConversationResponse> = {}): AdminConversationResponse {
  return {
    id: 'conv-1',
    lawyerId: 'lawyer-1',
    orgId: 'org-1',
    title: 'Сроки по договору',
    caseId: null,
    documentId: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  }
}

function message(overrides: Partial<MessageResponse> = {}): MessageResponse {
  return {
    id: 'm1',
    role: 'USER',
    content: 'Какой срок исковой давности?',
    sources: [],
    rating: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function renderPage(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AiConversationsPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('AiConversationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedAdminApi.getConversations.mockResolvedValue({ items: [conversation()], total: 1 })
    mockedAdminApi.getConversationMessages.mockResolvedValue({ items: [message()], total: 1 })
  })

  it('lists conversations without loading any transcript upfront', async () => {
    renderPage()

    expect(await screen.findByText('Сроки по договору')).toBeInTheDocument()
    expect(mockedAdminApi.getConversationMessages).not.toHaveBeenCalled()
  })

  it('loads the transcript only after a conversation is picked', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByText('Сроки по договору'))

    await waitFor(() =>
      expect(mockedAdminApi.getConversationMessages).toHaveBeenCalledWith('conv-1')
    )
    expect(await screen.findByText('Какой срок исковой давности?')).toBeInTheDocument()
  })

  it('passes filters to the api and resets to the first page', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.type(screen.getByPlaceholderText(/организации|Organization/i), 'org-9')

    await waitFor(() =>
      expect(mockedAdminApi.getConversations).toHaveBeenLastCalledWith(
        expect.objectContaining({ orgId: 'org-9' }),
        0
      )
    )
  })
})
