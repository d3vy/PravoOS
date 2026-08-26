import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { ChatStreamCallbacks } from '../api/chat'
import { useAiChatStore, AI_PANEL_MIN_WIDTH, AI_PANEL_MAX_WIDTH } from './aiChatStore'
import { usePageContextStore } from './pageContextStore'

const streamMessageMock = vi.fn()

vi.mock('../api/chat', () => ({
  streamMessage: (...args: unknown[]) => streamMessageMock(...args),
  chatApi: {
    getMessages: vi.fn(),
    rateMessage: vi.fn(),
  },
}))

function callbacksOfLastCall(): ChatStreamCallbacks {
  return streamMessageMock.mock.calls[streamMessageMock.mock.calls.length - 1][1]
}

describe('aiChatStore', () => {
  beforeEach(() => {
    streamMessageMock.mockReset()
    localStorage.clear()
    usePageContextStore.setState({ context: null })
    useAiChatStore.setState({
      open: false,
      unread: false,
      contextEnabled: true,
      conversationId: null,
      messages: [],
      isSending: false,
      historyLoading: false,
      draft: '',
      attachedDocumentIds: [],
      abortController: null,
    })
  })

  it('sends the draft and shows an optimistic pair', () => {
    useAiChatStore.getState().send('Проверь срок')

    const state = useAiChatStore.getState()
    expect(state.messages).toHaveLength(2)
    expect(state.messages[0].content).toBe('Проверь срок')
    expect(state.messages[1].isStreaming).toBe(true)
    expect(state.isSending).toBe(true)
    expect(state.draft).toBe('')
  })

  it('ignores an empty message and a second send while streaming', () => {
    useAiChatStore.getState().send('   ')
    expect(streamMessageMock).not.toHaveBeenCalled()

    useAiChatStore.getState().send('первый')
    useAiChatStore.getState().send('второй')
    expect(streamMessageMock).toHaveBeenCalledTimes(1)
  })

  it('attaches page context only while the context chip is on', () => {
    usePageContextStore
      .getState()
      .setContext({ route: '/cases/7', label: 'Дело', entityType: 'CASE', entityId: '7' })

    useAiChatStore.getState().send('вопрос')
    expect(streamMessageMock.mock.calls[0][0].pageContext).toEqual({
      route: '/cases/7',
      entityType: 'CASE',
      entityId: '7',
    })

    useAiChatStore.setState({ isSending: false, contextEnabled: false })
    useAiChatStore.getState().send('второй вопрос')
    expect(streamMessageMock.mock.calls[1][0].pageContext).toBeUndefined()
  })

  it('raises the unread badge only when the panel is closed', () => {
    useAiChatStore.getState().send('вопрос')
    callbacksOfLastCall().onDone({
      conversationId: 'conv-1',
      messageId: 'msg-1',
      answer: 'ответ',
      sources: [],
      followUps: [],
    })

    let state = useAiChatStore.getState()
    expect(state.unread).toBe(true)
    expect(state.conversationId).toBe('conv-1')
    expect(state.isSending).toBe(false)

    useAiChatStore.getState().openWidget()
    expect(useAiChatStore.getState().unread).toBe(false)

    useAiChatStore.getState().send('ещё вопрос')
    callbacksOfLastCall().onDone({
      conversationId: 'conv-1',
      messageId: 'msg-2',
      answer: 'ответ',
      sources: [],
      followUps: [],
    })
    state = useAiChatStore.getState()
    expect(state.unread).toBe(false)
  })

  it('drops the streaming placeholder on stop', () => {
    useAiChatStore.getState().send('вопрос')
    useAiChatStore.getState().stop()

    const state = useAiChatStore.getState()
    expect(state.isSending).toBe(false)
    expect(state.messages.some((message) => message.isStreaming)).toBe(false)
    expect(state.messages).toHaveLength(1)
  })

  it('keeps the error bubble and stops sending', () => {
    useAiChatStore.getState().send('вопрос')
    callbacksOfLastCall().onError('Сервис недоступен')

    const state = useAiChatStore.getState()
    expect(state.isSending).toBe(false)
    expect(state.messages[1].content).toBe('Сервис недоступен')
  })

  it('clamps and persists the panel width', () => {
    useAiChatStore.getState().setWidth(10_000)
    expect(useAiChatStore.getState().width).toBe(AI_PANEL_MAX_WIDTH)

    useAiChatStore.getState().setWidth(10)
    expect(useAiChatStore.getState().width).toBe(AI_PANEL_MIN_WIDTH)
    expect(localStorage.getItem('pravoos.ai.width')).toBe(String(AI_PANEL_MIN_WIDTH))
  })

  it('clears the conversation on new chat', () => {
    useAiChatStore.setState({ conversationId: 'conv-1', messages: [
      { id: 'm1', role: 'USER', content: 'вопрос' },
    ] })

    useAiChatStore.getState().startNewChat()

    const state = useAiChatStore.getState()
    expect(state.conversationId).toBeNull()
    expect(state.messages).toEqual([])
  })
})
