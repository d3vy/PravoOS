import { useState, useRef, useEffect, useCallback, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence } from 'framer-motion'
import { chatApi, streamMessage } from '../../api/chat'
import type { ConversationResponse, MessageResponse } from '../../types'
import { Spinner } from '../ui/Spinner'
import { MessageBubble, type LocalMessage } from './ChatMessageBubble'

const TEXTAREA_MAX_HEIGHT = 160

export interface ScopedChatPanelProps {
  scope: { caseId: string } | { documentId: string }
  i18nPrefix: 'caseChat' | 'documentChat'
  conversationsQueryKey: unknown[]
}

export function ScopedChatPanel({
  scope,
  i18nPrefix,
  conversationsQueryKey,
}: ScopedChatPanelProps): JSX.Element {
  const { t } = useTranslation()
  const caseId = 'caseId' in scope ? scope.caseId : undefined
  const documentId = 'documentId' in scope ? scope.documentId : undefined
  const suggestionKeys = [
    `${i18nPrefix}.suggestion1`,
    `${i18nPrefix}.suggestion2`,
    `${i18nPrefix}.suggestion3`,
  ]
  const queryClient = useQueryClient()
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<LocalMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [isSending, setIsSending] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const skipNextHistorySyncRef = useRef(false)

  const { data: conversations = [] } = useQuery<ConversationResponse[]>({
    queryKey: conversationsQueryKey,
    queryFn: () => chatApi.getConversations(undefined, caseId, documentId),
    enabled: caseId !== '' && documentId !== '',
  })

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
    setMessages(
      historyMessages.map((m) => ({
        id: m.id,
        role: m.role,
        content: m.content,
        sources: m.sources,
        rating: m.rating,
      }))
    )
  }, [historyMessages])

  useEffect(() => {
    if (messages.length > 0) messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

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
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, rating } : m)))
    },
  })

  const handleSend = useCallback(
    (text?: string): void => {
      const message = (text ?? inputValue).trim()
      if (!message || isSending) return

      setInputValue('')
      setIsSending(true)

      const baseId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      const streamingId = `loading-${baseId}`
      setMessages((prev) => [
        ...prev,
        { id: `user-${baseId}`, role: 'USER', content: message },
        { id: streamingId, role: 'ASSISTANT', content: '', isStreaming: true },
      ])
      if (textareaRef.current) textareaRef.current.style.height = 'auto'

      void streamMessage(
        { conversationId: activeConversationId ?? undefined, message, caseId, documentId },
        {
          onToken: (token) => {
            setMessages((prev) =>
              prev.map((m) => (m.id === streamingId ? { ...m, content: m.content + token } : m))
            )
          },
          onDone: (data) => {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === streamingId
                  ? {
                      id: data.messageId ?? `assistant-${baseId}`,
                      role: 'ASSISTANT' as const,
                      content: data.answer,
                      sources: data.sources,
                      followUps: data.followUps ?? [],
                      autoCheckCitations: true,
                    }
                  : m
              )
            )
            if (data.conversationId !== activeConversationId) {
              skipNextHistorySyncRef.current = true
              setActiveConversationId(data.conversationId)
            }
            queryClient.invalidateQueries({ queryKey: conversationsQueryKey })
            setIsSending(false)
          },
          onError: (errorMessage) => {
            setMessages((prev) => [
              ...prev.filter((m) => m.id !== streamingId),
              { id: `error-${Date.now()}`, role: 'ASSISTANT', content: errorMessage },
            ])
            setIsSending(false)
          },
        }
      )
    },
    [inputValue, isSending, activeConversationId, caseId, documentId, conversationsQueryKey, queryClient]
  )

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>): void => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleTextareaInput = (): void => {
    const textarea = textareaRef.current
    if (!textarea) return
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, TEXTAREA_MAX_HEIGHT) + 'px'
  }

  const startNewChat = (): void => {
    setActiveConversationId(null)
    setMessages([])
  }

  const lastAssistantFollowUps = (() => {
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].role === 'ASSISTANT' && !messages[i].isStreaming) {
        return messages[i].followUps ?? []
      }
    }
    return []
  })()

  return (
    <div>
      <div className="flex items-center justify-between gap-3 mb-3">
        <div>
          <h2 className="text-base font-semibold text-fg">{t(`${i18nPrefix}.title`)}</h2>
          <p className="text-xs text-fg-muted mt-0.5">{t(`${i18nPrefix}.subtitle`)}</p>
        </div>
        <button
          type="button"
          onClick={startNewChat}
          className="text-xs px-3 py-1.5 rounded-lg border border-line text-fg-muted hover:text-fg hover:border-accent transition-colors shrink-0"
        >
          {t(`${i18nPrefix}.newChat`)}
        </button>
      </div>

      {conversations.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-3">
          {conversations.map((conversation) => (
            <button
              key={conversation.id}
              type="button"
              onClick={() => setActiveConversationId(conversation.id)}
              title={conversation.title}
              className={`text-xs px-2.5 py-1 rounded-full border transition-colors truncate max-w-[220px] ${
                conversation.id === activeConversationId
                  ? 'border-accent text-accent'
                  : 'border-line text-fg-muted hover:text-fg'
              }`}
            >
              {conversation.title}
            </button>
          ))}
        </div>
      )}

      <div className="rounded-xl border border-line bg-surface">
        <div className="max-h-[520px] overflow-y-auto px-4 py-4 flex flex-col gap-5">
          {messagesLoading && activeConversationId !== null && messages.length === 0 ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : messages.length === 0 ? (
            <div className="py-6 text-center">
              <p className="text-sm text-fg-muted mb-4">{t(`${i18nPrefix}.empty`)}</p>
              <div className="flex flex-wrap justify-center gap-2">
                {suggestionKeys.map((key) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => handleSend(t(key))}
                    className="text-xs px-3 py-1.5 rounded-lg border border-line text-fg-muted hover:text-fg hover:border-accent transition-colors"
                  >
                    {t(key)}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <AnimatePresence initial={false}>
              {messages.map((message) => (
                <MessageBubble
                  key={message.id}
                  message={message}
                  caseId={caseId}
                  onRate={(rating, comment) =>
                    rateMutation.mutate({ messageId: message.id, rating, comment })
                  }
                />
              ))}
            </AnimatePresence>
          )}
          <div ref={messagesEndRef} />
        </div>

        {lastAssistantFollowUps.length > 0 && !isSending && (
          <div className="px-4 pb-3 flex flex-wrap gap-2">
            {lastAssistantFollowUps.map((followUp) => (
              <button
                key={followUp}
                type="button"
                onClick={() => handleSend(followUp)}
                className="text-xs px-3 py-1.5 rounded-lg border border-line text-fg-muted hover:text-fg hover:border-accent transition-colors text-left"
              >
                {followUp}
              </button>
            ))}
          </div>
        )}

        <div className="border-t border-line p-3">
          <div className="flex items-end gap-2">
            <textarea
              ref={textareaRef}
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onInput={handleTextareaInput}
              onKeyDown={handleKeyDown}
              rows={1}
              placeholder={t(`${i18nPrefix}.placeholder`)}
              className="flex-1 resize-none rounded-lg border border-line bg-bg px-3 py-2 text-sm text-fg placeholder-fg-muted focus:outline-none focus:ring-1 focus:ring-accent"
            />
            <button
              type="button"
              onClick={() => handleSend()}
              disabled={isSending || inputValue.trim() === ''}
              className="px-4 py-2 rounded-lg bg-accent-solid text-accent-fg text-sm hover:bg-accent-solid-hover transition-colors disabled:opacity-50"
            >
              {isSending ? <Spinner size="sm" /> : t(`${i18nPrefix}.send`)}
            </button>
          </div>
          <p className="text-[11px] text-fg-muted mt-2">{t(`${i18nPrefix}.disclaimer`)}</p>
        </div>
      </div>
    </div>
  )
}
