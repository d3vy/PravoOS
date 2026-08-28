import { useCallback, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { AnimatePresence } from 'framer-motion'
import { chatApi } from '../../api/chat'
import type { ChatRequest, ConversationResponse } from '../../types'
import { useChatSession } from '../../hooks/useChatSession'
import { useScrollToBottom } from '../../hooks/useScrollToBottom'
import { autosizeTextarea, resetTextareaHeight } from '../../lib/chat/chatSession'
import { Spinner } from '../ui/Spinner'
import { MessageBubble } from './ChatMessageBubble'

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
  const [inputValue, setInputValue] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const buildRequest = useCallback(
    (message: string, conversationId?: string): ChatRequest => ({
      conversationId,
      message,
      caseId,
      documentId,
    }),
    [caseId, documentId]
  )

  const {
    messages,
    isSending,
    remoteBusy,
    messagesLoading,
    activeConversationId,
    followUps,
    send,
    rate,
    proposalDecided,
    selectConversation,
    startNewChat,
  } = useChatSession({ buildRequest, conversationsQueryKey })

  const { data: conversations = [] } = useQuery<ConversationResponse[]>({
    queryKey: conversationsQueryKey,
    queryFn: async () => (await chatApi.getConversations(undefined, caseId, documentId)).items,
    enabled: caseId !== '' && documentId !== '',
  })

  useScrollToBottom(messagesEndRef, messages)

  const handleSend = (text?: string): void => {
    const message = text ?? inputValue
    if (!message.trim() || isSending || remoteBusy) return
    setInputValue('')
    resetTextareaHeight(textareaRef.current)
    send(message)
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>): void => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleTextareaInput = (): void => {
    autosizeTextarea(textareaRef.current, TEXTAREA_MAX_HEIGHT)
  }

  const lastAssistantFollowUps = followUps

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
              onClick={() => selectConversation(conversation.id)}
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

      <div className="rounded-2xl border border-line bg-surface">
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
                  onRate={(rating, comment) => rate(message.id, rating, comment)}
                  onProposalDecided={proposalDecided}
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
              disabled={isSending || remoteBusy || inputValue.trim() === ''}
              title={remoteBusy ? t('aiWidget.remoteBusy') : undefined}
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
