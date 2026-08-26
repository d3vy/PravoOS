import { useCallback, useEffect, useRef, type ChangeEvent, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { motion, AnimatePresence } from 'framer-motion'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { chatApi } from '../../api/chat'
import type { DocumentResponse } from '../../types'
import { useAiChatStore, AI_PANEL_MIN_WIDTH, AI_PANEL_MAX_WIDTH } from '../../store/aiChatStore'
import { usePageContextStore } from '../../store/pageContextStore'
import { useScrollToBottom } from '../../hooks/useScrollToBottom'
import { autosizeTextarea, resetTextareaHeight, lastFollowUps } from '../../lib/chat/chatSession'
import { useMediaQuery } from '../../hooks/useMediaQuery'
import { useAuthStore } from '../../store/authStore'
import { MessageBubble } from '../chat/ChatMessageBubble'
import { PravoIcon } from '../ui/Logo'
import { Spinner } from '../ui/Spinner'

const TEXTAREA_MAX_HEIGHT = 140
const SUGGESTION_KEYS = ['chat.suggestion1', 'chat.suggestion2', 'chat.suggestion3']
const WIDE_SCREEN_QUERY = '(min-width: 768px)'

interface AiWidgetPanelProps {
  onClose: () => void
}

export function AiWidgetPanel({ onClose }: AiWidgetPanelProps): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const isWideScreen = useMediaQuery(WIDE_SCREEN_QUERY)

  const width = useAiChatStore((state) => state.width)
  const setWidth = useAiChatStore((state) => state.setWidth)
  const messages = useAiChatStore((state) => state.messages)
  const isSending = useAiChatStore((state) => state.isSending)
  const historyLoading = useAiChatStore((state) => state.historyLoading)
  const conversationId = useAiChatStore((state) => state.conversationId)
  const draft = useAiChatStore((state) => state.draft)
  const setDraft = useAiChatStore((state) => state.setDraft)
  const attachedDocumentIds = useAiChatStore((state) => state.attachedDocumentIds)
  const toggleAttachedDocument = useAiChatStore((state) => state.toggleAttachedDocument)
  const contextEnabled = useAiChatStore((state) => state.contextEnabled)
  const setContextEnabled = useAiChatStore((state) => state.setContextEnabled)
  const send = useAiChatStore((state) => state.send)
  const stop = useAiChatStore((state) => state.stop)
  const rate = useAiChatStore((state) => state.rate)
  const startNewChat = useAiChatStore((state) => state.startNewChat)
  const pageContext = usePageContextStore((state) => state.context)
  const canOpenFullPage = useAuthStore((state) => state.effectiveRole()) === 'LAWYER'

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useScrollToBottom(messagesEndRef, messages)

  useEffect(() => {
    const focusTimer = setTimeout(() => textareaRef.current?.focus(), 50)
    return () => clearTimeout(focusTimer)
  }, [])

  useEffect(() => {
    const handleKeyDown = (event: globalThis.KeyboardEvent): void => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const { data: attachments = [] } = useQuery<DocumentResponse[]>({
    queryKey: ['chat', 'attachments'],
    queryFn: chatApi.getAttachments,
    refetchInterval: (query) =>
      (query.state.data ?? []).some((document) => document.status === 'PROCESSING') ? 2000 : false,
  })

  const attachedDocuments = attachedDocumentIds
    .map((id) => attachments.find((document) => document.id === id))
    .filter((document): document is DocumentResponse => document !== undefined)
  const hasIndexingAttachment = attachedDocuments.some(
    (document) => document.status === 'PROCESSING'
  )

  const uploadMutation = useMutation({
    mutationFn: chatApi.uploadAttachment,
    onSuccess: (uploaded) => {
      toggleAttachedDocument(uploaded.id)
      queryClient.invalidateQueries({ queryKey: ['chat', 'attachments'] })
    },
  })

  const handleSend = useCallback(
    (text?: string): void => {
      const message = text ?? draft
      if (!message.trim() || isSending || hasIndexingAttachment) return
      resetTextareaHeight(textareaRef.current)
      send(message)
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
    },
    [draft, hasIndexingAttachment, isSending, queryClient, send]
  )

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>): void => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
  }

  const handleFileSelected = (event: ChangeEvent<HTMLInputElement>): void => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (file) uploadMutation.mutate(file)
  }

  const openFullPage = (): void => {
    const target = conversationId ? `/ai?conversation=${conversationId}` : '/ai'
    window.open(target, '_blank', 'noopener,noreferrer')
  }

  const startResize = (event: React.PointerEvent<HTMLDivElement>): void => {
    event.preventDefault()
    const handleMove = (moveEvent: PointerEvent): void =>
      setWidth(window.innerWidth - moveEvent.clientX)
    const handleUp = (): void => {
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
    }
    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp)
  }

  const followUps = lastFollowUps(messages)
  const hasMessages = messages.length > 0

  return (
    <motion.aside
      role="dialog"
      aria-label={t('aiWidget.title')}
      initial={{ x: '100%' }}
      animate={{ x: 0 }}
      exit={{ x: '100%' }}
      transition={{ duration: 0.2, ease: 'easeOut' }}
      style={isWideScreen ? { width } : undefined}
      className="fixed inset-y-0 right-0 z-[130] flex w-full max-w-full flex-col border-l border-line bg-surface shadow-card"
    >
      {isWideScreen && (
        <div
          onPointerDown={startResize}
          role="separator"
          aria-orientation="vertical"
          aria-label={t('aiWidget.resize')}
          aria-valuenow={width}
          aria-valuemin={AI_PANEL_MIN_WIDTH}
          aria-valuemax={AI_PANEL_MAX_WIDTH}
          className="absolute inset-y-0 -left-1 w-2 cursor-col-resize hover:bg-accent/30"
        />
      )}

      <p className="sr-only" aria-live="polite" aria-atomic="true">
        {isSending ? t('chat.a11yGenerating') : ''}
      </p>

      <header className="flex items-start justify-between gap-3 border-b border-line px-4 py-3 shrink-0">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="grid h-7 w-7 place-items-center rounded-full border border-line text-accent">
              <PravoIcon className="w-4 h-4" />
            </span>
            <p className="truncate font-semibold text-fg">{t('aiWidget.title')}</p>
          </div>
          {pageContext && (
            <button
              type="button"
              onClick={() => setContextEnabled(!contextEnabled)}
              title={contextEnabled ? t('aiWidget.contextDisable') : t('aiWidget.contextEnable')}
              className={`mt-1.5 inline-flex max-w-full items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs transition-colors ${
                contextEnabled
                  ? 'border-accent/30 bg-accent/10 text-accent'
                  : 'border-line text-fg-muted hover:text-fg'
              }`}
            >
              <span className="truncate">
                {t('aiWidget.contextChip', { label: pageContext.label })}
              </span>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                {contextEnabled ? (
                  <>
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </>
                ) : (
                  <polyline points="20 6 9 17 4 12" />
                )}
              </svg>
            </button>
          )}
        </div>

        <div className="flex items-center gap-0.5 shrink-0">
          <IconButton onClick={startNewChat} label={t('chat.newChat')}>
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </IconButton>
          {canOpenFullPage && (
            <IconButton onClick={openFullPage} label={t('aiWidget.fullScreen')}>
              <polyline points="15 3 21 3 21 9" />
              <line x1="10" y1="14" x2="21" y2="3" />
              <path d="M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5" />
            </IconButton>
          )}
          <IconButton onClick={onClose} label={t('aiWidget.close')}>
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </IconButton>
        </div>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto scrollbar-thin px-4 py-4">
        {historyLoading ? (
          <div className="flex justify-center py-10">
            <Spinner size="md" />
          </div>
        ) : !hasMessages ? (
          <div className="flex h-full flex-col items-center justify-center text-center">
            <div className="mb-4 grid h-12 w-12 place-items-center rounded-2xl border border-line bg-bg text-fg-muted">
              <PravoIcon className="w-6 h-6" />
            </div>
            <h2 className="text-lg font-semibold text-fg">{t('aiWidget.emptyTitle')}</h2>
            <p className="mt-2 text-sm leading-relaxed text-fg-muted">
              {t('aiWidget.emptySubtitle')}
            </p>
            <div className="mt-6 flex w-full flex-col gap-2">
              {SUGGESTION_KEYS.map((suggestionKey) => (
                <button
                  key={suggestionKey}
                  type="button"
                  onClick={() => handleSend(t(suggestionKey))}
                  className="rounded-lg border border-line px-3 py-2 text-left text-sm text-fg-muted transition-colors hover:border-accent/40 hover:bg-bg hover:text-fg"
                >
                  {t(suggestionKey)}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-5">
            <AnimatePresence initial={false}>
              {messages.map((message) => (
                <MessageBubble
                  key={message.id}
                  message={message}
                  onRate={(rating, comment) => void rate(message.id, rating, comment)}
                />
              ))}
            </AnimatePresence>

            {!isSending && followUps.length > 0 && (
              <div className="flex flex-col gap-1.5">
                <p className="text-xs text-fg-muted">{t('chat.followUps')}</p>
                {followUps.map((followUp) => (
                  <button
                    key={followUp}
                    type="button"
                    onClick={() => handleSend(followUp)}
                    className="rounded-lg border border-accent/30 px-3 py-2 text-left text-xs text-accent transition-colors hover:bg-accent/5"
                  >
                    {followUp}
                  </button>
                ))}
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      <div className="border-t border-line px-4 py-3 shrink-0">
        {(attachedDocuments.length > 0 || uploadMutation.isPending) && (
          <div className="mb-2 flex flex-wrap items-center gap-1.5">
            {attachedDocuments.map((document) => (
              <span
                key={document.id}
                className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs ${
                  document.status === 'FAILED'
                    ? 'border-danger/20 bg-danger/10 text-danger'
                    : 'border-accent/20 bg-accent/10 text-accent'
                }`}
              >
                {document.status === 'PROCESSING' && <Spinner size="sm" />}
                {document.title}
                <button
                  type="button"
                  onClick={() => toggleAttachedDocument(document.id)}
                  aria-label={t('chat.removeDoc')}
                  className="ml-0.5 hover:opacity-70"
                >
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              </span>
            ))}
            {uploadMutation.isPending && (
              <span className="inline-flex items-center gap-1.5 rounded-full border border-line px-2 py-0.5 text-xs text-fg-muted">
                <Spinner size="sm" />
                {t('chat.uploadingFile')}
              </span>
            )}
          </div>
        )}

        <div className="flex items-end gap-2 rounded-2xl border border-line bg-bg px-3 py-2 transition-all focus-within:border-accent focus-within:ring-1 focus-within:ring-accent">
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.doc,.docx,.txt,.rtf,.odt"
            onChange={handleFileSelected}
            className="hidden"
          />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={isSending || uploadMutation.isPending}
            aria-label={t('chat.attachFile')}
            title={t('chat.attachFile')}
            className="grid h-8 w-8 shrink-0 place-items-center rounded-lg text-fg-muted transition-colors hover:bg-surface hover:text-accent disabled:opacity-30"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
            </svg>
          </button>

          <textarea
            ref={textareaRef}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onInput={() => autosizeTextarea(textareaRef.current, TEXTAREA_MAX_HEIGHT)}
            onKeyDown={handleKeyDown}
            rows={1}
            placeholder={t('chat.askPlaceholder')}
            aria-label={t('chat.messageText')}
            className="min-h-[32px] max-h-36 flex-1 resize-none bg-transparent py-1 text-sm leading-6 text-fg outline-none placeholder-fg-muted"
          />

          <button
            type="button"
            onClick={() => (isSending ? stop() : handleSend())}
            disabled={!isSending && (!draft.trim() || hasIndexingAttachment)}
            title={hasIndexingAttachment ? t('chat.waitIndexing') : undefined}
            aria-label={isSending ? t('aiWidget.stop') : t('chat.send')}
            className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-accent-solid text-accent-fg transition-all hover:bg-accent-solid-hover disabled:cursor-not-allowed disabled:opacity-40"
          >
            {isSending ? (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                <rect x="5" y="5" width="14" height="14" rx="2" />
              </svg>
            ) : (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="22" y1="2" x2="11" y2="13" />
                <polygon points="22 2 15 22 11 13 2 9 22 2" />
              </svg>
            )}
          </button>
        </div>
        <p className="mt-2 text-center text-[11px] text-fg-muted opacity-70">
          {t('chat.disclaimer')}
        </p>
      </div>
    </motion.aside>
  )
}

interface IconButtonProps {
  onClick: () => void
  label: string
  children: React.ReactNode
}

function IconButton({ onClick, label, children }: IconButtonProps): JSX.Element {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="grid h-8 w-8 place-items-center rounded-lg text-fg-muted transition-colors hover:bg-bg hover:text-fg"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        {children}
      </svg>
    </button>
  )
}
