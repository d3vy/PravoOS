import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../i18n'
import { CaseChatSection } from './CaseChatSection'
import { chatApi, streamMessage } from '../../api/chat'
import type { ChatStreamCallbacks } from '../../api/chat'
import type { ChatRequest, ConversationResponse, MessageResponse } from '../../types'

vi.mock('../../api/chat', () => ({
  chatApi: {
    getConversations: vi.fn(),
    getMessages: vi.fn(),
    rateMessage: vi.fn(),
  },
  streamMessage: vi.fn(),
}))

vi.mock('../../api/citations', () => ({
  citationsApi: {
    checkText: vi.fn(),
  },
}))

const CASE_ID = 'case-1'

const mockedChatApi = vi.mocked(chatApi)
const mockedStreamMessage = vi.mocked(streamMessage)

function renderSection(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <CaseChatSection caseId={CASE_ID} />
    </QueryClientProvider>
  )
}

function conversation(overrides: Partial<ConversationResponse> = {}): ConversationResponse {
  return {
    id: 'conv-1',
    title: 'Сроки по договору',
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function assistantMessage(): MessageResponse {
  return {
    id: 'msg-1',
    role: 'ASSISTANT',
    content: 'Ответ из истории',
    sources: ['Материалы дела: Договор'],
    rating: null,
    createdAt: new Date().toISOString(),
  }
}

beforeEach(async () => {
  await i18n.changeLanguage('ru')
  mockedChatApi.getConversations.mockResolvedValue([])
  mockedChatApi.getMessages.mockResolvedValue([])
  mockedStreamMessage.mockResolvedValue(undefined)
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('CaseChatSection', () => {
  it('loads only conversations of this case', async () => {
    renderSection()

    await waitFor(() => {
      expect(mockedChatApi.getConversations).toHaveBeenCalledWith(undefined, CASE_ID)
    })
  })

  it('sends the message with caseId and streams the answer', async () => {
    mockedStreamMessage.mockImplementation(
      async (_request: ChatRequest, callbacks: ChatStreamCallbacks) => {
        callbacks.onToken('Срок ')
        callbacks.onToken('поставки — 10 дней.')
        callbacks.onDone({
          conversationId: 'conv-new',
          answer: 'Срок поставки — 10 дней.',
          sources: ['Материалы дела: Договор'],
          followUps: ['Какая неустойка предусмотрена?'],
        })
      }
    )

    renderSection()
    const user = userEvent.setup()

    await user.type(
      screen.getByPlaceholderText(i18n.t('caseChat.placeholder')),
      'Какой срок поставки?'
    )
    await user.click(screen.getByRole('button', { name: i18n.t('caseChat.send') }))

    await waitFor(() => {
      expect(mockedStreamMessage).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'Какой срок поставки?', caseId: CASE_ID }),
        expect.anything()
      )
    })
    expect(await screen.findByText('Срок поставки — 10 дней.')).toBeInTheDocument()
    expect(screen.getByText('Материалы дела: Договор')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Какая неустойка предусмотрена?' })
    ).toBeInTheDocument()
  })

  it('opens the history of a selected case conversation', async () => {
    mockedChatApi.getConversations.mockResolvedValue([conversation()])
    mockedChatApi.getMessages.mockResolvedValue([assistantMessage()])

    renderSection()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Сроки по договору' }))

    await waitFor(() => {
      expect(mockedChatApi.getMessages).toHaveBeenCalledWith('conv-1')
    })
    expect(await screen.findByText('Ответ из истории')).toBeInTheDocument()
  })

  it('sends a suggestion as a message', async () => {
    renderSection()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: i18n.t('caseChat.suggestion1') }))

    await waitFor(() => {
      expect(mockedStreamMessage).toHaveBeenCalledWith(
        expect.objectContaining({ message: i18n.t('caseChat.suggestion1'), caseId: CASE_ID }),
        expect.anything()
      )
    })
  })
})
