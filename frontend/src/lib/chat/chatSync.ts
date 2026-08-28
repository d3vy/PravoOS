import type { AiActionProposal, ChatResponse, ChatToolStep } from '../../types'
import type { LocalMessage } from '../../components/chat/ChatMessageBubble'
import { applyDone, applyError, applyProposal, applyToken, applyToolStep } from './chatSession'

const CHANNEL_NAME = 'pravoos-ai-chat-v1'

export const chatSyncTabId: string =
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

export type RemoteStreamEvent =
  | {
      kind: 'start'
      baseId: string
      streamingId: string
      userMessage: LocalMessage
      streamingMessage: LocalMessage
    }
  | { kind: 'token'; streamingId: string; token: string }
  | { kind: 'toolStep'; streamingId: string; step: ChatToolStep }
  | { kind: 'proposal'; streamingId: string; proposal: AiActionProposal }
  | { kind: 'done'; streamingId: string; baseId: string; response: ChatResponse }
  | { kind: 'error'; streamingId: string; errorText: string }

export type ChatSyncMessage =
  | { type: 'stream-event'; tabId: string; conversationId: string | null; event: RemoteStreamEvent }
  | { type: 'busy-changed'; tabId: string; conversationId: string | null; isSending: boolean }
  | { type: 'list-changed'; tabId: string; reason: 'created' | 'deleted'; conversationId: string | null }
  | { type: 'state-request'; tabId: string; conversationId: string }
  | {
      type: 'state-snapshot'
      tabId: string
      conversationId: string
      requesterTabId: string
      messages: LocalMessage[]
    }

type ChatSyncListener = (message: ChatSyncMessage) => void

const listeners = new Set<ChatSyncListener>()

const channel: BroadcastChannel | null =
  typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CHANNEL_NAME) : null

channel?.addEventListener('message', (event: MessageEvent<ChatSyncMessage>) => {
  const message = event.data
  if (!message || message.tabId === chatSyncTabId) return
  listeners.forEach((listener) => listener(message))
})

function post(message: ChatSyncMessage): void {
  channel?.postMessage(message)
}

export function subscribeChatSync(listener: ChatSyncListener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function broadcastStreamStart(
  conversationId: string | null,
  baseId: string,
  streamingId: string,
  userMessage: LocalMessage,
  streamingMessage: LocalMessage
): void {
  post({
    type: 'stream-event',
    tabId: chatSyncTabId,
    conversationId,
    event: { kind: 'start', baseId, streamingId, userMessage, streamingMessage },
  })
}

export function broadcastStreamEvent(
  conversationId: string | null,
  event: RemoteStreamEvent
): void {
  post({ type: 'stream-event', tabId: chatSyncTabId, conversationId, event })
}

export function broadcastBusy(conversationId: string | null, isSending: boolean): void {
  post({ type: 'busy-changed', tabId: chatSyncTabId, conversationId, isSending })
}

export function broadcastListChanged(
  reason: 'created' | 'deleted',
  conversationId: string | null
): void {
  post({ type: 'list-changed', tabId: chatSyncTabId, reason, conversationId })
}

export function requestState(conversationId: string): void {
  post({ type: 'state-request', tabId: chatSyncTabId, conversationId })
}

export function replyState(
  conversationId: string,
  requesterTabId: string,
  messages: LocalMessage[]
): void {
  post({
    type: 'state-snapshot',
    tabId: chatSyncTabId,
    conversationId,
    requesterTabId,
    messages,
  })
}

export function applyRemoteStreamEvent(
  messages: LocalMessage[],
  event: RemoteStreamEvent
): LocalMessage[] {
  switch (event.kind) {
    case 'start':
      return [...messages, event.userMessage, event.streamingMessage]
    case 'token':
      return applyToken(messages, event.streamingId, event.token)
    case 'toolStep':
      return applyToolStep(messages, event.streamingId, event.step)
    case 'proposal':
      return applyProposal(messages, event.streamingId, event.proposal)
    case 'done':
      return applyDone(messages, event.streamingId, event.response, event.baseId)
    case 'error':
      return applyError(messages, event.streamingId, event.errorText)
  }
}
