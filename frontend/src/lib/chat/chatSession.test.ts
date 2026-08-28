import { describe, it, expect } from 'vitest'
import {
  applyDone,
  applyError,
  applyProposal,
  applyProposalDecision,
  applyRating,
  applyToken,
  applyToolStep,
  attachPendingProposals,
  createOptimisticPair,
  historyToLocal,
  lastFollowUps,
} from './chatSession'
import type { AiActionProposal, ChatResponse, MessageResponse } from '../../types'

function proposal(overrides: Partial<AiActionProposal> = {}): AiActionProposal {
  return {
    id: 'prop-1',
    conversationId: 'conv-1',
    toolName: 'create_case',
    title: 'Завести дело «Иванов против ООО»',
    status: 'PENDING',
    arguments: { title: 'Иванов против ООО' },
    createdAt: '2026-08-27T10:00:00',
    expiresAt: '2026-08-27T10:15:00',
    ...overrides,
  }
}

describe('chatSession', () => {
  it('creates a user message and a streaming placeholder', () => {
    const pair = createOptimisticPair('Какой срок исковой давности?')

    expect(pair.userMessage.role).toBe('USER')
    expect(pair.userMessage.content).toBe('Какой срок исковой давности?')
    expect(pair.streamingMessage.isStreaming).toBe(true)
    expect(pair.streamingMessage.content).toBe('')
    expect(pair.streamingId).toBe(pair.streamingMessage.id)
  })

  it('appends tokens only to the streaming message', () => {
    const pair = createOptimisticPair('вопрос')
    const withFirst = applyToken([pair.userMessage, pair.streamingMessage], pair.streamingId, 'Три')
    const withSecond = applyToken(withFirst, pair.streamingId, ' года')

    expect(withSecond[0].content).toBe('вопрос')
    expect(withSecond[1].content).toBe('Три года')
  })

  it('replaces the streaming placeholder with the persisted answer', () => {
    const pair = createOptimisticPair('вопрос')
    const response: ChatResponse = {
      conversationId: 'conv-1',
      messageId: 'msg-42',
      answer: 'Три года.',
      sources: ['ГК РФ ст. 196'],
      followUps: ['А для недвижимости?'],
    }

    const result = applyDone(
      [pair.userMessage, pair.streamingMessage],
      pair.streamingId,
      response,
      pair.baseId
    )

    expect(result).toHaveLength(2)
    expect(result[1].id).toBe('msg-42')
    expect(result[1].isStreaming).toBeUndefined()
    expect(result[1].content).toBe('Три года.')
    expect(result[1].sources).toEqual(['ГК РФ ст. 196'])
    expect(result[1].autoCheckCitations).toBe(true)
  })

  it('falls back to a local id when the backend returns no messageId', () => {
    const pair = createOptimisticPair('вопрос')
    const response = {
      conversationId: 'conv-1',
      answer: 'Ответ',
      sources: [],
      followUps: [],
    } as unknown as ChatResponse

    const result = applyDone(
      [pair.userMessage, pair.streamingMessage],
      pair.streamingId,
      response,
      pair.baseId
    )

    expect(result[1].id).toBe(`assistant-${pair.baseId}`)
  })

  it('drops the placeholder and appends an error bubble', () => {
    const pair = createOptimisticPair('вопрос')
    const result = applyError(
      [pair.userMessage, pair.streamingMessage],
      pair.streamingId,
      'Ошибка'
    )

    expect(result).toHaveLength(2)
    expect(result.some((message) => message.isStreaming)).toBe(false)
    expect(result[1].content).toBe('Ошибка')
  })

  it('maps server history to local messages', () => {
    const history: MessageResponse[] = [
      {
        id: 'm1',
        role: 'USER',
        content: 'вопрос',
        createdAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'm2',
        role: 'ASSISTANT',
        content: 'ответ',
        sources: ['ГК РФ'],
        rating: 1,
        createdAt: '2026-01-01T00:00:01Z',
      },
    ]

    expect(historyToLocal(history)).toEqual([
      {
        id: 'm1',
        role: 'USER',
        content: 'вопрос',
        sources: undefined,
        rating: undefined,
        toolSteps: [],
      },
      {
        id: 'm2',
        role: 'ASSISTANT',
        content: 'ответ',
        sources: ['ГК РФ'],
        rating: 1,
        toolSteps: [],
      },
    ])
  })

  it('ignores follow-ups of a still streaming answer', () => {
    const messages = [
      { id: 'a', role: 'ASSISTANT' as const, content: 'старый', followUps: ['первый'] },
      { id: 'b', role: 'USER' as const, content: 'вопрос' },
      { id: 'c', role: 'ASSISTANT' as const, content: '', isStreaming: true, followUps: ['новый'] },
    ]

    expect(lastFollowUps(messages)).toEqual(['первый'])
  })

  it('applies a rating to one message only', () => {
    const messages = [
      { id: 'a', role: 'ASSISTANT' as const, content: 'первый' },
      { id: 'b', role: 'ASSISTANT' as const, content: 'второй' },
    ]

    const result = applyRating(messages, 'b', -1)

    expect(result[0].rating).toBeUndefined()
    expect(result[1].rating).toBe(-1)
  })
  it('appends a running tool step to the streaming message only', () => {
    const messages = [
      { id: 'user-1', role: 'USER' as const, content: 'вопрос' },
      { id: 'loading-1', role: 'ASSISTANT' as const, content: '', isStreaming: true },
    ]

    const next = applyToolStep(messages, 'loading-1', { name: 'get_case', status: 'RUNNING' })

    expect(next[0].toolSteps).toBeUndefined()
    expect(next[1].toolSteps).toEqual([{ name: 'get_case', status: 'RUNNING' }])
  })

  it('replaces a running step with its finished result instead of duplicating it', () => {
    const messages = [
      {
        id: 'loading-1',
        role: 'ASSISTANT' as const,
        content: '',
        isStreaming: true,
        toolSteps: [{ name: 'get_case' as const, status: 'RUNNING' as const }],
      },
    ]

    const next = applyToolStep(messages, 'loading-1', { name: 'get_case', status: 'OK' })

    expect(next[0].toolSteps).toEqual([{ name: 'get_case', status: 'OK' }])
  })

  it('keeps parallel steps of different tools apart', () => {
    const messages = [
      {
        id: 'loading-1',
        role: 'ASSISTANT' as const,
        content: '',
        isStreaming: true,
        toolSteps: [
          { name: 'get_case', status: 'RUNNING' as const },
          { name: 'get_client', status: 'RUNNING' as const },
        ],
      },
    ]

    const next = applyToolStep(messages, 'loading-1', { name: 'get_client', status: 'ERROR' })

    expect(next[0].toolSteps).toEqual([
      { name: 'get_case', status: 'RUNNING' },
      { name: 'get_client', status: 'ERROR' },
    ])
  })

  it('drops unfinished steps when the answer settles', () => {
    const messages = [
      {
        id: 'loading-1',
        role: 'ASSISTANT' as const,
        content: 'частичный',
        isStreaming: true,
        toolSteps: [
          { name: 'get_case', status: 'OK' as const },
          { name: 'get_client', status: 'RUNNING' as const },
        ],
      },
    ]
    const response: ChatResponse = {
      conversationId: 'conv-1',
      messageId: 'm-1',
      answer: 'ответ',
      sources: [],
      followUps: [],
    }

    const next = applyDone(messages, 'loading-1', response, 'base-1')

    expect(next[0].toolSteps).toEqual([{ name: 'get_case', status: 'OK' }])
  })


  it('attaches a proposal to the streaming message and replaces it on decision', () => {
    const pair = createOptimisticPair('заведи дело')
    const streaming = applyProposal(
      [pair.userMessage, pair.streamingMessage],
      pair.streamingId,
      proposal()
    )

    expect(streaming[0].proposals ?? []).toHaveLength(0)
    expect(streaming[1].proposals).toHaveLength(1)

    const decided = applyProposalDecision(streaming, proposal({ status: 'APPROVED', result: 'Дело создано' }))

    expect(decided[1].proposals?.[0].status).toBe('APPROVED')
    expect(decided[1].proposals?.[0].result).toBe('Дело создано')
  })

  it('does not duplicate a proposal that arrives twice', () => {
    const pair = createOptimisticPair('заведи дело')
    const once = applyProposal([pair.streamingMessage], pair.streamingId, proposal())
    const twice = applyProposal(once, pair.streamingId, proposal({ title: 'Уточнённое название' }))

    expect(twice[0].proposals).toHaveLength(1)
    expect(twice[0].proposals?.[0].title).toBe('Уточнённое название')
  })

  it('keeps proposals when the stream settles', () => {
    const pair = createOptimisticPair('заведи дело')
    const streaming = applyProposal([pair.streamingMessage], pair.streamingId, proposal())
    const response: ChatResponse = {
      conversationId: 'conv-1',
      messageId: 'msg-1',
      answer: 'Подготовил действие.',
      sources: [],
      followUps: [],
    }

    const settled = applyDone(streaming, pair.streamingId, response, pair.baseId)

    expect(settled[0].proposals).toHaveLength(1)
  })

  it('attaches pending proposals to the last settled assistant message of the history', () => {
    const history = historyToLocal([
      { id: 'm1', role: 'USER', content: 'заведи дело', createdAt: '2026-08-27T10:00:00' },
      { id: 'm2', role: 'ASSISTANT', content: 'Подготовил', createdAt: '2026-08-27T10:00:01' },
    ] as MessageResponse[])

    const attached = attachPendingProposals(history, [proposal()])

    expect(attached[0].proposals).toBeUndefined()
    expect(attached[1].proposals).toHaveLength(1)
  })

  it('attaches a proposal to the message it was raised on, not the last one', () => {
    const history = historyToLocal([
      { id: 'm1', role: 'USER', content: 'заведи дело', createdAt: '2026-08-27T10:00:00' },
      { id: 'm2', role: 'ASSISTANT', content: 'Подготовил', createdAt: '2026-08-27T10:00:01' },
      { id: 'm3', role: 'USER', content: 'спасибо', createdAt: '2026-08-27T10:01:00' },
      { id: 'm4', role: 'ASSISTANT', content: 'Пожалуйста', createdAt: '2026-08-27T10:01:01' },
    ] as MessageResponse[])

    const attached = attachPendingProposals(history, [proposal({ messageId: 'm2' })])

    expect(attached[1].proposals).toHaveLength(1)
    expect(attached[3].proposals).toBeUndefined()
  })

  it('falls back to the last settled assistant message for a proposal whose message is not in history', () => {
    const history = historyToLocal([
      { id: 'm1', role: 'ASSISTANT', content: 'Ответ', createdAt: '2026-08-27T10:00:00' },
    ] as MessageResponse[])

    const attached = attachPendingProposals(history, [proposal({ messageId: 'unknown' })])

    expect(attached[0].proposals).toHaveLength(1)
  })

  it('leaves the history untouched when there is nothing pending', () => {
    const history = historyToLocal([
      { id: 'm1', role: 'ASSISTANT', content: 'Ответ', createdAt: '2026-08-27T10:00:00' },
    ] as MessageResponse[])

    expect(attachPendingProposals(history, [])).toBe(history)
  })

  it('picks up proposals carried by the done payload when no event arrived', () => {
    const pair = createOptimisticPair('заведи дело')
    const response: ChatResponse = {
      conversationId: 'conv-1',
      messageId: 'msg-1',
      answer: 'Подготовил действие.',
      sources: [],
      followUps: [],
      proposals: [proposal()],
    }

    const settled = applyDone([pair.streamingMessage], pair.streamingId, response, pair.baseId)

    expect(settled[0].proposals).toHaveLength(1)
  })

  it('does not duplicate a proposal that came both as an event and in the done payload', () => {
    const pair = createOptimisticPair('заведи дело')
    const streaming = applyProposal([pair.streamingMessage], pair.streamingId, proposal())
    const response: ChatResponse = {
      conversationId: 'conv-1',
      messageId: 'msg-1',
      answer: 'Подготовил действие.',
      sources: [],
      followUps: [],
      proposals: [proposal({ title: 'Уточнённое название' })],
    }

    const settled = applyDone(streaming, pair.streamingId, response, pair.baseId)

    expect(settled[0].proposals).toHaveLength(1)
    expect(settled[0].proposals?.[0].title).toBe('Уточнённое название')
  })
})
