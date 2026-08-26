import { create } from 'zustand'
import { chatApi, streamMessage } from '../api/chat'
import type { LocalMessage } from '../components/chat/ChatMessageBubble'
import {
  applyDone,
  applyError,
  applyRating,
  applyToken,
  createOptimisticPair,
  historyToLocal,
} from '../lib/chat/chatSession'
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
    if (!message || state.isSending) return

    const { baseId, streamingId, userMessage, streamingMessage } = createOptimisticPair(message)
    const attachedDocumentIds =
      state.attachedDocumentIds.length > 0 ? state.attachedDocumentIds : undefined
    const abortController = new AbortController()

    set({
      messages: [...state.messages, userMessage, streamingMessage],
      isSending: true,
      draft: '',
      attachedDocumentIds: [],
      abortController,
    })

    void streamMessage(
      {
        conversationId: state.conversationId ?? undefined,
        message,
        attachedDocumentIds,
        pageContext: state.contextEnabled ? usePageContextStore.getState().toRequest() : undefined,
      },
      {
        onToken: (token) =>
          set((current) => ({ messages: applyToken(current.messages, streamingId, token) })),
        onDone: (data) =>
          set((current) => ({
            messages: applyDone(current.messages, streamingId, data, baseId),
            conversationId: data.conversationId,
            isSending: false,
            abortController: null,
            unread: !current.open,
          })),
        onError: (errorMessage) =>
          set((current) => ({
            messages: applyError(current.messages, streamingId, errorMessage),
            isSending: false,
            abortController: null,
          })),
      },
      abortController.signal
    )
  },

  stop: () => {
    const { abortController, messages } = get()
    abortController?.abort()
    set({
      isSending: false,
      abortController: null,
      messages: messages.filter((message) => !message.isStreaming),
    })
  },

  rate: async (messageId, rating, comment) => {
    set((state) => ({ messages: applyRating(state.messages, messageId, rating) }))
    await chatApi.rateMessage(messageId, { rating, comment })
  },

  startNewChat: () => {
    get().abortController?.abort()
    set({
      conversationId: null,
      messages: [],
      isSending: false,
      abortController: null,
      attachedDocumentIds: [],
      draft: '',
    })
  },

  selectConversation: async (conversationId) => {
    get().abortController?.abort()
    set({
      conversationId,
      messages: [],
      isSending: false,
      abortController: null,
      historyLoading: true,
    })
    try {
      const history = await chatApi.getMessages(conversationId)
      if (get().conversationId !== conversationId) return
      set({ messages: historyToLocal(history), historyLoading: false })
    } catch {
      set({ historyLoading: false })
    }
  },
}))
