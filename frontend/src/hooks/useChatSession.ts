import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { chatApi, streamMessage } from '../api/chat'
import type { ChatRequest, MessageResponse } from '../types'
import type { LocalMessage } from '../components/chat/ChatMessageBubble'
import {
  applyDone,
  applyError,
  applyRating,
  applyToken,
  createOptimisticPair,
  historyToLocal,
  lastFollowUps,
} from '../lib/chat/chatSession'

export interface ChatSessionOptions {
  buildRequest: (message: string, conversationId?: string) => ChatRequest
  conversationsQueryKey: unknown[]
}

export interface ChatSession {
  messages: LocalMessage[]
  isSending: boolean
  messagesLoading: boolean
  activeConversationId: string | null
  followUps: string[]
  send: (text: string) => void
  rate: (messageId: string, rating: number, comment?: string) => void
  selectConversation: (conversationId: string) => void
  startNewChat: () => void
}

export function useChatSession({
  buildRequest,
  conversationsQueryKey,
}: ChatSessionOptions): ChatSession {
  const queryClient = useQueryClient()
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<LocalMessage[]>([])
  const [isSending, setIsSending] = useState(false)
  const skipNextHistorySyncRef = useRef(false)
  const streamAbortRef = useRef<AbortController | null>(null)

  useEffect(() => () => streamAbortRef.current?.abort(), [])

  const { data: historyMessages, isLoading: messagesLoading } = useQuery<MessageResponse[]>({
    queryKey: ['messages', activeConversationId],
    queryFn: () => chatApi.getMessages(activeConversationId!),
    enabled: activeConversationId !== null,
  })

  useEffect(() => {
    if (!historyMessages) return
    if (skipNextHistorySyncRef.current) {
      skipNextHistorySyncRef.current = false
      return
    }
    setMessages(historyToLocal(historyMessages))
  }, [historyMessages])

  const rateMutation = useMutation({
    mutationFn: ({
      messageId,
      rating,
      comment,
    }: {
      messageId: string
      rating: number
      comment?: string
    }) => chatApi.rateMessage(messageId, { rating, comment }),
    onMutate: ({ messageId, rating }) => {
      setMessages((prev) => applyRating(prev, messageId, rating))
    },
  })

  const send = useCallback(
    (text: string): void => {
      const message = text.trim()
      if (!message || isSending) return

      setIsSending(true)
      const { baseId, streamingId, userMessage, streamingMessage } = createOptimisticPair(message)
      setMessages((prev) => [...prev, userMessage, streamingMessage])

      streamAbortRef.current?.abort()
      const abortController = new AbortController()
      streamAbortRef.current = abortController

      void streamMessage(
        buildRequest(message, activeConversationId ?? undefined),
        {
          onToken: (token) => setMessages((prev) => applyToken(prev, streamingId, token)),
          onDone: (data) => {
            setMessages((prev) => applyDone(prev, streamingId, data, baseId))
            if (data.conversationId !== activeConversationId) {
              skipNextHistorySyncRef.current = true
              setActiveConversationId(data.conversationId)
            }
            queryClient.invalidateQueries({ queryKey: conversationsQueryKey })
            setIsSending(false)
          },
          onError: (errorMessage) => {
            setMessages((prev) => applyError(prev, streamingId, errorMessage))
            setIsSending(false)
          },
        },
        abortController.signal
      )
    },
    [activeConversationId, buildRequest, conversationsQueryKey, isSending, queryClient]
  )

  const startNewChat = useCallback((): void => {
    setActiveConversationId(null)
    setMessages([])
  }, [])

  const rate = useCallback(
    (messageId: string, rating: number, comment?: string): void => {
      rateMutation.mutate({ messageId, rating, comment })
    },
    [rateMutation]
  )

  return {
    messages,
    isSending,
    messagesLoading,
    activeConversationId,
    followUps: lastFollowUps(messages),
    send,
    rate,
    selectConversation: setActiveConversationId,
    startNewChat,
  }
}
