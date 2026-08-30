import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../i18n'
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

  it('shows the confirmation controls only while pending', () => {
    renderCard()

    expect(screen.getByRole('button', { name: /Approve/i })).toBeInTheDocument()
    expect(screen.getByRole('checkbox')).toBeInTheDocument()
  })

  it.each(['APPROVED', 'REJECTED', 'EXPIRED', 'FAILED'] as const)(
    'renders the %s status without confirmation controls',
    (status) => {
      renderCard({ proposal: proposal({ status }) })

      expect(i18n.t(`chat.proposal.status.${status}`)).not.toBe(`chat.proposal.status.${status}`)
      expect(screen.getByText(i18n.t(`chat.proposal.status.${status}`))).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /Approve/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /Reject/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    }
  )

  it('renders the failure reason for a FAILED proposal', () => {
    renderCard({ proposal: proposal({ status: 'FAILED', failureReason: 'клиент с таким ИНН уже есть' }) })

    expect(screen.getByText('клиент с таким ИНН уже есть')).toBeInTheDocument()
  })

  it('renders the result for an APPROVED proposal', () => {
    renderCard({ proposal: proposal({ status: 'APPROVED', result: 'Клиент создан: Иванов' }) })

    expect(screen.getByText('Клиент создан: Иванов')).toBeInTheDocument()
  })

  it('shows an error and keeps the controls when the mutation fails', async () => {
    mockedChatApi.approveProposal.mockRejectedValue(new Error('boom'))
    renderCard()

    await userEvent.click(screen.getByRole('button', { name: /Approve/i }))

    expect(await screen.findByText(i18n.t('chat.proposal.error'))).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Approve/i })).toBeInTheDocument()
  })

  it('notifies onDecided with the mutation result', async () => {
    const decided = proposal({ status: 'APPROVED' })
    mockedChatApi.approveProposal.mockResolvedValue(decided)
    const onDecided = vi.fn()
    renderCard({ onDecided })

    await userEvent.click(screen.getByRole('button', { name: /Approve/i }))

    await screen.findByText(i18n.t('chat.proposal.status.APPROVED'))
    expect(onDecided).toHaveBeenCalledWith(decided)
  })
})
