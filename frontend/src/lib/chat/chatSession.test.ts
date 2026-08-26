import { describe, it, expect } from 'vitest'
import {
  applyDone,
  applyError,
  applyRating,
  applyToken,
  createOptimisticPair,
  historyToLocal,
  lastFollowUps,
} from './chatSession'
import type { ChatResponse, MessageResponse } from '../../types'

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
      { id: 'm1', role: 'USER', content: 'вопрос', sources: undefined, rating: undefined },
      { id: 'm2', role: 'ASSISTANT', content: 'ответ', sources: ['ГК РФ'], rating: 1 },
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
})
