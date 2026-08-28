import { create } from 'zustand'
import { chatApi, streamMessage } from '../api/chat'
import type { LocalMessage } from '../components/chat/ChatMessageBubble'
import type { AiActionProposal } from '../types'
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
} from '../lib/chat/chatSession'
import {
  applyRemoteStreamEvent,
  broadcastBusy,
  broadcastListChanged,
  broadcastStreamEvent,
  broadcastStreamStart,
  chatSyncTabId,
  replyState,
  requestState,
  subscribeChatSync,
} from '../lib/chat/chatSync'
import { usePageContextStore } from './pageContextStore'

const WIDTH_STORAGE_KEY = 'pravoos.ai.width'
export const AI_PANEL_MIN_WIDTH = 320
export const AI_PANEL_MAX_WIDTH = 720
export const AI_PANEL_DEFAULT_WIDTH = 440

function readStoredWidth(): number {
  const stored = Number(localStorage.getItem(WIDTH_STORAGE_KEY))
  if (!Number.isFinite(stored) || stored <= 0) return AI_PANEL_DEFAULT_WIDTH
  return clampWidth(stored)
}

export function clampWidth(width: number): number {
  return Math.min(AI_PANEL_MAX_WIDTH, Math.max(AI_PANEL_MIN_WIDTH, Math.round(width)))
}

interface AiChatState {
  open: boolean
  width: number
  unread: boolean
  contextEnabled: boolean
  conversationId: string | null
  messages: LocalMessage[]
  isSending: boolean
  historyLoading: boolean
  draft: string
  attachedDocumentIds: string[]
  abortController: AbortController | null
  remoteBusy: boolean

  openWidget: (draft?: string) => void
  close: () => void
  toggle: () => void
  setWidth: (width: number) => void
  setContextEnabled: (enabled: boolean) => void
  setDraft: (draft: string) => void
  setAttachedDocumentIds: (ids: string[]) => void
  toggleAttachedDocument: (id: string) => void
  send: (text: string) => void
  stop: () => void
  rate: (messageId: string, rating: number, comment?: string) => Promise<void>
  proposalDecided: (proposal: AiActionProposal) => void
  startNewChat: () => void
  selectConversation: (conversationId: string) => Promise<void>
}

export const useAiChatStore = create<AiChatState>((set, get) => ({
  open: false,
  width: readStoredWidth(),
  unread: false,
  contextEnabled: true,
  conversationId: null,
  messages: [],
  isSending: false,
  historyLoading: false,
  draft: '',
  attachedDocumentIds: [],
  abortController: null,
  remoteBusy: false,

  openWidget: (draft) =>
    set((state) => ({ open: true, unread: false, draft: draft ?? state.draft })),

  close: () => set({ open: false }),

  toggle: () => (get().open ? get().close() : get().openWidget()),

  setWidth: (width) => {
    const clamped = clampWidth(width)
    localStorage.setItem(WIDTH_STORAGE_KEY, String(clamped))
    set({ width: clamped })
  },

  setContextEnabled: (contextEnabled) => set({ contextEnabled }),

  setDraft: (draft) => set({ draft }),

  setAttachedDocumentIds: (attachedDocumentIds) => set({ attachedDocumentIds }),

  toggleAttachedDocument: (id) =>
    set((state) => ({
      attachedDocumentIds: state.attachedDocumentIds.includes(id)
        ? state.attachedDocumentIds.filter((current) => current !== id)
        : [...state.attachedDocumentIds, id],
    })),

  send: (text) => {
    const message = text.trim()
    const state = get()
    if (!message || state.isSending || state.remoteBusy) return

    const { baseId, streamingId, userMessage, streamingMessage } = createOptimisticPair(message)
    const attachedDocumentIds =
      state.attachedDocumentIds.length > 0 ? state.attachedDocumentIds : undefined
    const abortController = new AbortController()
    const startConversationId = state.conversationId

    set({
      messages: [...state.messages, userMessage, streamingMessage],
      isSending: true,
      draft: '',
      attachedDocumentIds: [],
      abortController,
    })
    broadcastStreamStart(startConversationId, baseId, streamingId, userMessage, streamingMessage)
    broadcastBusy(startConversationId, true)

    void streamMessage(
      {
        conversationId: state.conversationId ?? undefined,
        message,
        attachedDocumentIds,
        pageContext: state.contextEnabled ? usePageContextStore.getState().toRequest() : undefined,
      },
      {
        onToken: (token) => {
          set((current) => ({ messages: applyToken(current.messages, streamingId, token) }))
          broadcastStreamEvent(startConversationId, { kind: 'token', streamingId, token })
        },
        onToolStep: (step) => {
          set((current) => ({ messages: applyToolStep(current.messages, streamingId, step) }))
          broadcastStreamEvent(startConversationId, { kind: 'toolStep', streamingId, step })
        },
        onProposal: (proposal) => {
          set((current) => ({ messages: applyProposal(current.messages, streamingId, proposal) }))
          broadcastStreamEvent(startConversationId, { kind: 'proposal', streamingId, proposal })
        },
        onDone: (data) => {
          set((current) => ({
            messages: applyDone(current.messages, streamingId, data, baseId),
            conversationId: data.conversationId,
            isSending: false,
            abortController: null,
            unread: !current.open,
          }))
          broadcastStreamEvent(startConversationId, {
            kind: 'done',
            streamingId,
            baseId,
            response: data,
          })
          broadcastBusy(startConversationId, false)
          if (!startConversationId) broadcastListChanged('created', data.conversationId)
        },
        onError: (errorMessage) => {
          set((current) => ({
            messages: applyError(current.messages, streamingId, errorMessage),
            isSending: false,
            abortController: null,
          }))
          broadcastStreamEvent(startConversationId, { kind: 'error', streamingId, errorText: errorMessage })
          broadcastBusy(startConversationId, false)
        },
      },
      abortController.signal
    )
  },

  stop: () => {
    const { abortController, messages, conversationId } = get()
    abortController?.abort()
    set({
      isSending: false,
      abortController: null,
      messages: messages.filter((message) => !message.isStreaming),
    })
    broadcastBusy(conversationId, false)
  },

  proposalDecided: (proposal) =>
    set((state) => ({ messages: applyProposalDecision(state.messages, proposal) })),

  rate: async (messageId, rating, comment) => {
    set((state) => ({ messages: applyRating(state.messages, messageId, rating) }))
    await chatApi.rateMessage(messageId, { rating, comment })
  },

  startNewChat: () => {
    const { abortController, conversationId, isSending } = get()
    abortController?.abort()
    if (isSending) broadcastBusy(conversationId, false)
    set({
      conversationId: null,
      messages: [],
      isSending: false,
      remoteBusy: false,
      abortController: null,
      attachedDocumentIds: [],
      draft: '',
    })
  },

  selectConversation: async (conversationId) => {
    const previous = get()
    previous.abortController?.abort()
    if (previous.isSending) broadcastBusy(previous.conversationId, false)
    set({
      conversationId,
      messages: [],
      isSending: false,
      remoteBusy: false,
      abortController: null,
      historyLoading: true,
    })
    try {
      const [history, proposals] = await Promise.all([
        chatApi.getMessages(conversationId),
        chatApi.getPendingProposals(conversationId).catch(() => [] as AiActionProposal[]),
      ])
      if (get().conversationId !== conversationId) return
      set({
        messages: attachPendingProposals(historyToLocal(history), proposals),
        historyLoading: false,
      })
      requestState(conversationId)
    } catch {
      set({ historyLoading: false })
    }
  },
}))

subscribeChatSync((message) => {
  const state = useAiChatStore.getState()
  switch (message.type) {
    case 'stream-event': {
      if (message.conversationId === null || message.conversationId !== state.conversationId) return
      if (state.isSending) return
      useAiChatStore.setState((current) => ({
        messages: applyRemoteStreamEvent(current.messages, message.event),
      }))
      return
    }
    case 'busy-changed': {
      if (message.conversationId !== null && message.conversationId === state.conversationId) {
        useAiChatStore.setState({ remoteBusy: message.isSending })
      }
      return
    }
    case 'state-request': {
      if (message.conversationId === state.conversationId && state.isSending) {
        replyState(message.conversationId, message.tabId, state.messages)
      }
      return
    }
    case 'state-snapshot': {
      if (message.requesterTabId !== chatSyncTabId) return
      if (message.conversationId !== state.conversationId) return
      useAiChatStore.setState({ messages: message.messages })
      return
    }
    case 'list-changed': {
      if (message.reason === 'deleted' && message.conversationId === state.conversationId) {
        useAiChatStore.getState().startNewChat()
      }
      return
    }
  }
})
