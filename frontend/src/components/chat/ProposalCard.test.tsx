import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '../../i18n'
import { ProposalCard } from './ProposalCard'
import { chatApi } from '../../api/chat'
import type { AiActionProposal } from '../../types'

vi.mock('../../api/chat', () => ({
  chatApi: {
    approveProposal: vi.fn(),
    rejectProposal: vi.fn(),
  },
}))

const mockedChatApi = vi.mocked(chatApi)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function proposal(overrides: Partial<AiActionProposal> = {}): AiActionProposal {
  return {
    id: 'proposal-1',
    conversationId: 'conv-1',
    toolName: 'create_client',
    title: 'Создать клиента «Иванов»',
    status: 'PENDING',
    arguments: {},
    createdAt: new Date().toISOString(),
    expiresAt: new Date().toISOString(),
    ...overrides,
  }
}

function renderCard(props: Partial<Parameters<typeof ProposalCard>[0]> = {}) {
  const queryClient = new QueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <ProposalCard proposal={proposal()} {...props} />
    </QueryClientProvider>
  )
}

describe('ProposalCard', () => {
  it('approves without alwaysAllow when the checkbox is left unchecked', async () => {
    mockedChatApi.approveProposal.mockResolvedValue(proposal({ status: 'APPROVED' }))
    renderCard()

    await userEvent.click(screen.getByRole('button', { name: /Approve/i }))

    expect(mockedChatApi.approveProposal).toHaveBeenCalledWith('proposal-1', false)
  })

  it('approves with alwaysAllow when the checkbox is checked', async () => {
    mockedChatApi.approveProposal.mockResolvedValue(proposal({ status: 'APPROVED' }))
    renderCard()

    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: /Approve/i }))

    expect(mockedChatApi.approveProposal).toHaveBeenCalledWith('proposal-1', true)
  })

  it('rejects without touching approveProposal', async () => {
    mockedChatApi.rejectProposal.mockResolvedValue(proposal({ status: 'REJECTED' }))
    renderCard()

    await userEvent.click(screen.getByRole('button', { name: /Reject/i }))

    expect(mockedChatApi.rejectProposal).toHaveBeenCalledWith('proposal-1')
    expect(mockedChatApi.approveProposal).not.toHaveBeenCalled()
  })
})
