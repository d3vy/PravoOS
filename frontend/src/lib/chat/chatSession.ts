import type { ChatResponse, MessageResponse } from '../../types'
import type { LocalMessage } from '../../components/chat/ChatMessageBubble'

export interface OptimisticPair {
  baseId: string
  streamingId: string
  userMessage: LocalMessage
  streamingMessage: LocalMessage
}

export function createOptimisticPair(text: string): OptimisticPair {
  const baseId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const streamingId = `loading-${baseId}`
  return {
    baseId,
    streamingId,
    userMessage: { id: `user-${baseId}`, role: 'USER', content: text },
    streamingMessage: { id: streamingId, role: 'ASSISTANT', content: '', isStreaming: true },
  }
}

export function applyToken(
  messages: LocalMessage[],
  streamingId: string,
  token: string
): LocalMessage[] {
  return messages.map((message) =>
    message.id === streamingId ? { ...message, content: message.content + token } : message
  )
}

export function applyDone(
  messages: LocalMessage[],
  streamingId: string,
  response: ChatResponse,
  baseId: string
): LocalMessage[] {
  return messages.map((message) =>
    message.id === streamingId
      ? {
          id: response.messageId ?? `assistant-${baseId}`,
          role: 'ASSISTANT' as const,
          content: response.answer,
          sources: response.sources,
          followUps: response.followUps ?? [],
          autoCheckCitations: true,
        }
      : message
  )
}

export function applyError(
  messages: LocalMessage[],
  streamingId: string,
  errorText: string
): LocalMessage[] {
  return [
    ...messages.filter((message) => message.id !== streamingId),
    { id: `error-${Date.now()}`, role: 'ASSISTANT', content: errorText },
  ]
}

export function historyToLocal(history: MessageResponse[]): LocalMessage[] {
  return history.map((message) => ({
    id: message.id,
    role: message.role,
    content: message.content,
    sources: message.sources,
    rating: message.rating,
  }))
}

export function lastSettledAssistant(messages: LocalMessage[]): LocalMessage | null {
  for (let index = messages.length - 1; index >= 0; index--) {
    const message = messages[index]
    if (message.role === 'ASSISTANT' && !message.isStreaming) return message
  }
  return null
}

export function lastFollowUps(messages: LocalMessage[]): string[] {
  return lastSettledAssistant(messages)?.followUps ?? []
}

export function applyRating(
  messages: LocalMessage[],
  messageId: string,
  rating: number
): LocalMessage[] {
  return messages.map((message) => (message.id === messageId ? { ...message, rating } : message))
}

export function autosizeTextarea(textarea: HTMLTextAreaElement | null, maxHeight: number): void {
  if (!textarea) return
  textarea.style.height = 'auto'
  textarea.style.height = Math.min(textarea.scrollHeight, maxHeight) + 'px'
}

export function resetTextareaHeight(textarea: HTMLTextAreaElement | null): void {
  if (!textarea) return
  textarea.style.height = 'auto'
}
