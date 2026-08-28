import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { chatApi, streamMessage } from '../api/chat'
import type { AiActionProposal, ChatRequest, MessageResponse } from '../types'
import type { LocalMessage } from '../components/chat/ChatMessageBubble'
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
} from '../lib/chat/chatSession'
import {
  applyRemoteStreamEvent,
  broadcastBusy,
  broadcastStreamEvent,
  broadcastStreamStart,
  subscribeChatSync,
} from '../lib/chat/chatSync'

export interface ChatSessionOptions {
  buildRequest: (message: string, conversationId?: string) => ChatRequest
  conversationsQueryKey: unknown[]
}

export interface ChatSession {
  messages: LocalMessage[]
  isSending: boolean
  remoteBusy: boolean
  messagesLoading: boolean
  activeConversationId: string | null
  followUps: string[]
  send: (text: string) => void
  rate: (messageId: string, rating: number, comment?: string) => void
  proposalDecided: (proposal: AiActionProposal) => void
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
  const [remoteBusy, setRemoteBusy] = useState(false)
  const skipHistorySyncForRef = useRef<string | null>(null)
  const streamAbortRef = useRef<AbortController | null>(null)

  useEffect(() => () => streamAbortRef.current?.abort(), [])

  useEffect(
    () =>
      subscribeChatSync((message) => {
        if (message.conversationId === null || message.conversationId !== activeConversationId) return
        if (message.type === 'stream-event') {
          if (isSending) return
          setMessages((prev) => applyRemoteStreamEvent(prev, message.event))
        } else if (message.type === 'busy-changed') {
          setRemoteBusy(message.isSending)
        }
      }),
    [activeConversationId, isSending]
  )

  const { data: historyMessages, isLoading: messagesLoading } = useQuery<MessageResponse[]>({
    queryKey: ['messages', activeConversationId],
    queryFn: () => chatApi.getMessages(activeConversationId!),
    enabled: activeConversationId !== null,
  })

  const { data: pendingProposals } = useQuery<AiActionProposal[]>({
    queryKey: ['chat-proposals', activeConversationId],
    queryFn: () => chatApi.getPendingProposals(activeConversationId!),
    enabled: activeConversationId !== null,
  })

  useEffect(() => {
    if (!historyMessages) return
    if (skipHistorySyncForRef.current === activeConversationId) return
    setMessages(attachPendingProposals(historyToLocal(historyMessages), pendingProposals ?? []))
  }, [activeConversationId, historyMessages, pendingProposals])

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
      if (!message || isSending || remoteBusy) return

      setIsSending(true)
      const { baseId, streamingId, userMessage, streamingMessage } = createOptimisticPair(message)
      setMessages((prev) => [...prev, userMessage, streamingMessage])
      const startConversationId = activeConversationId
      broadcastStreamStart(startConversationId, baseId, streamingId, userMessage, streamingMessage)
      broadcastBusy(startConversationId, true)

      streamAbortRef.current?.abort()
      const abortController = new AbortController()
      streamAbortRef.current = abortController

      void streamMessage(
        buildRequest(message, activeConversationId ?? undefined),
        {
          onToken: (token) => {
            setMessages((prev) => applyToken(prev, streamingId, token))
            broadcastStreamEvent(startConversationId, { kind: 'token', streamingId, token })
          },
          onToolStep: (step) => {
            setMessages((prev) => applyToolStep(prev, streamingId, step))
            broadcastStreamEvent(startConversationId, { kind: 'toolStep', streamingId, step })
          },
          onProposal: (proposal) => {
            setMessages((prev) => applyProposal(prev, streamingId, proposal))
            broadcastStreamEvent(startConversationId, { kind: 'proposal', streamingId, proposal })
          },
          onDone: (data) => {
            setMessages((prev) => applyDone(prev, streamingId, data, baseId))
            if (data.conversationId !== activeConversationId) {
              skipHistorySyncForRef.current = data.conversationId
              setActiveConversationId(data.conversationId)
            }
            queryClient.invalidateQueries({ queryKey: conversationsQueryKey })
            broadcastStreamEvent(startConversationId, {
              kind: 'done',
              streamingId,
              baseId,
              response: data,
            })
            broadcastBusy(startConversationId, false)
            setIsSending(false)
          },
          onError: (errorMessage) => {
            setMessages((prev) => applyError(prev, streamingId, errorMessage))
            broadcastStreamEvent(startConversationId, {
              kind: 'error',
              streamingId,
              errorText: errorMessage,
            })
            broadcastBusy(startConversationId, false)
            setIsSending(false)
          },
        },
        abortController.signal
      )
    },
    [activeConversationId, buildRequest, conversationsQueryKey, isSending, queryClient, remoteBusy]
  )

  const proposalDecided = useCallback((proposal: AiActionProposal): void => {
    setMessages((prev) => applyProposalDecision(prev, proposal))
  }, [])

  const startNewChat = useCallback((): void => {
    skipHistorySyncForRef.current = null
    setActiveConversationId(null)
    setMessages([])
    setRemoteBusy(false)
  }, [])

  const selectConversation = useCallback((conversationId: string): void => {
    skipHistorySyncForRef.current = null
    setActiveConversationId(conversationId)
    setRemoteBusy(false)
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
    remoteBusy,
    messagesLoading,
    activeConversationId,
    followUps: lastFollowUps(messages),
    send,
    rate,
    proposalDecided,
    selectConversation,
    startNewChat,
  }
}
