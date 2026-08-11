import {
  useState,
  useRef,
  useEffect,
  useCallback,
  type ChangeEvent,
  type KeyboardEvent,
} from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { chatApi, streamMessage } from '../../api/chat'
import type { MessageResponse, ConversationResponse, DocumentResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'
import { PravoIcon } from '../../components/ui/Logo'
import { MessageBubble, type LocalMessage } from '../../components/chat/ChatMessageBubble'

const SUGGESTION_KEYS = ['chat.suggestion1', 'chat.suggestion2', 'chat.suggestion3']

function locale(): string {
  return i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
}

export default function ChatPage(): JSX.Element {
  const { t } = useTranslation()
  const [searchParams, setSearchParams] = useSearchParams()
  const [activeConversationId, setActiveConversationId] = useState<string | null>(
    () => searchParams.get('conversation')
  )
  const [messages, setMessages] = useState<LocalMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [attachedDocIds, setAttachedDocIds] = useState<string[]>([])
  const [attachPickerOpen, setAttachPickerOpen] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const attachPickerRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const skipNextHistorySyncRef = useRef(false)
  const streamAbortRef = useRef<AbortController | null>(null)

  useEffect(() => () => streamAbortRef.current?.abort(), [])
  const queryClient = useQueryClient()

  useEffect(() => {
    const ask = searchParams.get('ask')
    if (ask) {
      setInputValue(ask)
      requestAnimationFrame(() => textareaRef.current?.focus())
    }
    if (searchParams.has('conversation') || searchParams.has('ask')) {
      setSearchParams({}, { replace: true })
    }
  }, [searchParams, setSearchParams])

  const { data: conversations = [], isLoading: conversationsLoading } = useQuery<ConversationResponse[]>({
    queryKey: ['conversations', searchQuery],
    queryFn: () => chatApi.getConversations(searchQuery || undefined),
  })

  const { data: attachments = [] } = useQuery<DocumentResponse[]>({
    queryKey: ['chat', 'attachments'],
    queryFn: chatApi.getAttachments,
    refetchInterval: (query) =>
      (query.state.data ?? []).some((doc) => doc.status === 'PROCESSING') ? 2000 : false,
  })

  const attachedDocs = attachedDocIds
    .map((id) => attachments.find((doc) => doc.id === id))
    .filter((doc): doc is DocumentResponse => doc !== undefined)
  const hasIndexingAttachment = attachedDocs.some((doc) => doc.status === 'PROCESSING')

  const uploadMutation = useMutation({
    mutationFn: chatApi.uploadAttachment,
    onSuccess: (uploaded) => {
      setUploadError(null)
      setAttachedDocIds((prev) => (prev.includes(uploaded.id) ? prev : [...prev, uploaded.id]))
      queryClient.invalidateQueries({ queryKey: ['chat', 'attachments'] })
    },
    onError: () => setUploadError(t('chat.uploadError')),
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
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    if (!attachPickerOpen) return
    const handleClickOutside = (e: MouseEvent): void => {
      if (attachPickerRef.current && !attachPickerRef.current.contains(e.target as Node)) {
        setAttachPickerOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [attachPickerOpen])

  const rateMutation = useMutation({
    mutationFn: ({ messageId, rating, comment }: { messageId: string; rating: number; comment?: string }) =>
      chatApi.rateMessage(messageId, { rating, comment }),
    onMutate: ({ messageId, rating }) => {
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, rating } : m)))
    },
  })

  const handleSend = useCallback((text?: string): void => {
    const message = (text ?? inputValue).trim()
    if (!message || isSending || hasIndexingAttachment) return

    setInputValue('')
    setIsSending(true)
    setAttachPickerOpen(false)

    const baseId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const streamingId = `loading-${baseId}`
    setMessages((prev) => [
      ...prev,
      { id: `user-${baseId}`, role: 'USER', content: message },
      { id: streamingId, role: 'ASSISTANT', content: '', isStreaming: true },
    ])

    const attachedDocumentIds = attachedDocIds.length > 0 ? attachedDocIds : undefined
    setAttachedDocIds([])
    if (textareaRef.current) textareaRef.current.style.height = 'auto'

    streamAbortRef.current?.abort()
    const abortController = new AbortController()
    streamAbortRef.current = abortController

    void streamMessage(
      { conversationId: activeConversationId ?? undefined, message, attachedDocumentIds },
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
          queryClient.invalidateQueries({ queryKey: ['conversations'] })
          setIsSending(false)
        },
        onError: (errorMessage) => {
          setMessages((prev) => [
            ...prev.filter((m) => m.id !== streamingId),
            { id: `error-${Date.now()}`, role: 'ASSISTANT', content: errorMessage },
          ])
          setIsSending(false)
        },
      },
      abortController.signal
    )
  }, [inputValue, isSending, hasIndexingAttachment, activeConversationId, attachedDocIds, queryClient])

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>): void => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleTextareaInput = (): void => {
    const ta = textareaRef.current
    if (!ta) return
    ta.style.height = 'auto'
    ta.style.height = Math.min(ta.scrollHeight, 160) + 'px'
  }

  const startNewChat = (): void => {
    setActiveConversationId(null)
    setMessages([])
    setSidebarOpen(false)
    setAttachedDocIds([])
  }

  const selectConversation = (id: string): void => {
    setActiveConversationId(id)
    setSidebarOpen(false)
  }

  const toggleDoc = (id: string): void => {
    setAttachedDocIds((prev) =>
      prev.includes(id) ? prev.filter((d) => d !== id) : [...prev, id]
    )
  }

  const handleFileSelected = (event: ChangeEvent<HTMLInputElement>): void => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setAttachPickerOpen(false)
    uploadMutation.mutate(file)
  }

  const lastSettledAssistant = (() => {
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].role === 'ASSISTANT' && !messages[i].isStreaming) {
        return messages[i]
      }
    }
    return null
  })()

  const lastAssistantFollowUps = lastSettledAssistant?.followUps ?? []

  const liveAnnouncement = isSending
    ? t('chat.a11yGenerating')
    : lastSettledAssistant?.content ?? ''

  return (
    <div className="h-[calc(100vh-64px)] bg-bg flex flex-col">
      <p className="sr-only" aria-live="polite" aria-atomic="true">
        {liveAnnouncement}
      </p>

      <div className="flex flex-1 overflow-hidden min-h-0">
        {!sidebarOpen && (
          <button
            onClick={() => setSidebarOpen(true)}
            className="md:hidden fixed top-[72px] left-3 z-30 w-10 h-10 rounded-lg bg-surface border border-line text-fg-muted shadow-card flex items-center justify-center"
            aria-label={t('chat.openConversations')}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
        )}

        {/* Sidebar */}
        <aside
          className={`
            ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}
            md:translate-x-0 fixed md:relative z-20 md:z-auto
            w-72 md:w-64 h-full flex flex-col
            bg-surface
            border-r border-line
            transition-transform duration-200 md:transition-none
          `}
        >
          <div className="p-4 border-b border-line flex flex-col gap-2">
            <button
              onClick={startNewChat}
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-accent-solid text-accent-fg text-sm font-medium hover:bg-accent-solid-hover transition-colors"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              {t('chat.newChat')}
            </button>

            {/* Search */}
            <div className="relative">
              <svg className="absolute left-2.5 top-1/2 -translate-y-1/2 text-fg-muted w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
              <input
                type="search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={t('chat.searchPlaceholder')}
                className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg border border-line bg-bg text-fg placeholder-fg-muted focus:outline-none focus:ring-1 focus:ring-accent"
              />
            </div>
          </div>

          <div className="flex-1 overflow-y-auto scrollbar-thin p-2">
            {conversationsLoading ? (
              <div className="flex justify-center py-8"><Spinner size="sm" /></div>
            ) : conversations.length === 0 ? (
              <p className="text-xs text-center text-fg-muted py-8 px-4">
                {searchQuery ? t('chat.nothingFound') : t('chat.noConversations')}
              </p>
            ) : (
              <div className="flex flex-col gap-0.5">
                {conversations.map((conv) => (
                  <button
                    key={conv.id}
                    onClick={() => selectConversation(conv.id)}
                    className={`w-full text-left px-3 py-2.5 rounded-lg text-sm transition-colors ${
                      activeConversationId === conv.id
                        ? 'bg-accent/10 text-accent'
                        : 'text-fg hover:bg-bg'
                    }`}
                  >
                    <span className="block truncate font-medium">{conv.title}</span>
                    <span className="block text-xs text-fg-muted mt-0.5">
                      {new Date(conv.createdAt).toLocaleDateString(locale())}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </aside>

        {sidebarOpen && (
          <div
            className="md:hidden fixed inset-0 z-10 bg-black/40 backdrop-blur-sm"
            onClick={() => setSidebarOpen(false)}
          />
        )}

        {/* Main chat area */}
        <div className="flex-1 flex flex-col min-w-0">
          <div className="flex-1 overflow-y-auto scrollbar-thin px-4 py-6">
            {messagesLoading ? (
              <div className="flex justify-center py-12"><Spinner size="md" /></div>
            ) : messages.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-center max-w-md mx-auto">
                <div className="w-14 h-14 rounded-2xl bg-surface border border-line flex items-center justify-center text-fg-muted mb-5">
                  <PravoIcon className="w-7 h-7" />
                </div>
                <h2 className="text-2xl font-semibold text-fg mb-3">
                  {t('chat.emptyTitle')}
                </h2>
                <p className="text-sm text-fg-muted leading-relaxed">
                  {t('chat.emptySubtitle')}
                </p>
                <div className="mt-8 grid grid-cols-1 gap-2 w-full max-w-sm">
                  {SUGGESTION_KEYS.map((suggestionKey) => (
                    <button
                      key={suggestionKey}
                      onClick={() => { setInputValue(t(suggestionKey)); textareaRef.current?.focus() }}
                      className="text-left text-sm px-4 py-2.5 rounded-lg border border-line text-fg-muted hover:text-fg hover:border-accent/40 hover:bg-surface transition-colors"
                    >
                      {t(suggestionKey)}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="max-w-3xl mx-auto flex flex-col gap-6">
                <AnimatePresence initial={false}>
                  {messages.map((message) => (
                    <MessageBubble
                      key={message.id}
                      message={message}
                      onRate={(rating, comment) =>
                        rateMutation.mutate({ messageId: message.id, rating, comment })}
                    />
                  ))}
                </AnimatePresence>

                {/* Follow-up suggestions */}
                {!isSending && lastAssistantFollowUps.length > 0 && (
                  <motion.div
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex flex-col gap-2 max-w-[80%]"
                  >
                    <p className="text-xs text-fg-muted ml-11">{t('chat.followUps')}</p>
                    <div className="flex flex-col gap-1.5 ml-11">
                      {lastAssistantFollowUps.map((q, i) => (
                        <button
                          key={i}
                          onClick={() => handleSend(q)}
                          className="text-left text-xs px-3 py-2 rounded-lg border border-accent/30 text-accent hover:bg-accent/5 transition-colors"
                        >
                          {q}
                        </button>
                      ))}
                    </div>
                  </motion.div>
                )}

                <div ref={messagesEndRef} />
              </div>
            )}
          </div>

          {/* Input area */}
          <div className="border-t border-line bg-surface p-4">
            <div className="max-w-3xl mx-auto">
              {/* Attached docs chips */}
              {(attachedDocs.length > 0 || uploadMutation.isPending || uploadError) && (
                <div className="flex flex-wrap items-center gap-1.5 mb-2">
                  {attachedDocs.map((doc) => (
                    <span
                      key={doc.id}
                      className={`inline-flex items-center gap-1.5 text-xs px-2 py-0.5 rounded-full border ${
                        doc.status === 'FAILED'
                          ? 'bg-red-500/10 text-danger border-red-500/20'
                          : 'bg-accent/10 text-accent border-accent/20'
                      }`}
                    >
                      {doc.status === 'PROCESSING' && <Spinner size="sm" />}
                      {doc.title}
                      {doc.status === 'PROCESSING' && <span className="opacity-70">{t('chat.indexing')}</span>}
                      {doc.status === 'FAILED' && <span className="opacity-70">{t('chat.processFailed')}</span>}
                      <button
                        onClick={() => toggleDoc(doc.id)}
                        className="hover:opacity-70 ml-0.5"
                        aria-label={t('chat.removeDoc')}
                      >
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                          <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                        </svg>
                      </button>
                    </span>
                  ))}
                  {uploadMutation.isPending && (
                    <span className="inline-flex items-center gap-1.5 text-xs px-2 py-0.5 rounded-full border border-line text-fg-muted">
                      <Spinner size="sm" />
                      {t('chat.uploadingFile')}
                    </span>
                  )}
                  {uploadError && (
                    <span className="text-xs text-danger">{uploadError}</span>
                  )}
                </div>
              )}

              <div className="flex gap-3 items-end rounded-xl border border-line bg-bg focus-within:border-accent focus-within:ring-1 focus-within:ring-accent transition-all px-4 py-3">
                {/* Attach button */}
                <div className="relative shrink-0" ref={attachPickerRef}>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".pdf,.doc,.docx,.txt,.rtf,.odt"
                    onChange={handleFileSelected}
                    className="hidden"
                  />
                  <button
                    type="button"
                    onClick={() => setAttachPickerOpen((v) => !v)}
                    disabled={isSending || uploadMutation.isPending}
                    title={t('chat.attachFile')}
                    className="w-7 h-7 rounded-md flex items-center justify-center text-fg-muted hover:text-accent hover:bg-surface transition-colors disabled:opacity-30"
                    aria-label={t('chat.attachFile')}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
                    </svg>
                  </button>

                  {attachPickerOpen && (
                    <div className="absolute bottom-full left-0 mb-2 w-72 max-h-72 overflow-y-auto rounded-xl border border-line bg-surface shadow-lg z-10">
                      <button
                        onClick={() => fileInputRef.current?.click()}
                        className="w-full text-left px-3 py-2.5 text-sm flex items-center gap-2 text-accent hover:bg-bg transition-colors"
                      >
                        <svg className="shrink-0 w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                        </svg>
                        {t('chat.uploadFromComputer')}
                      </button>

                      {attachments.length > 0 && (
                        <>
                          <p className="text-xs font-medium text-fg-muted px-3 pt-3 pb-2 border-t border-line">
                            {t('chat.previouslyUploaded')}
                          </p>
                          {attachments.map((doc) => (
                            <button
                              key={doc.id}
                              onClick={() => { toggleDoc(doc.id); setAttachPickerOpen(false) }}
                              className={`w-full text-left px-3 py-2 text-sm transition-colors flex items-center gap-2 ${
                                attachedDocIds.includes(doc.id)
                                  ? 'text-accent bg-accent/5'
                                  : 'text-fg hover:bg-bg'
                              }`}
                            >
                              <span className="text-xs font-bold uppercase text-fg-muted w-7 shrink-0">
                                {doc.fileName.split('.').pop()}
                              </span>
                              <span className="min-w-0 truncate">{doc.title}</span>
                              {attachedDocIds.includes(doc.id) && (
                                <svg className="ml-auto shrink-0 w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                                  <polyline points="20 6 9 17 4 12" />
                                </svg>
                              )}
                            </button>
                          ))}
                        </>
                      )}
                    </div>
                  )}
                </div>

                <textarea
                  ref={textareaRef}
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyDown={handleKeyDown}
                  onInput={handleTextareaInput}
                  placeholder={t('chat.askPlaceholder')}
                  rows={1}
                  disabled={isSending}
                  aria-label={t('chat.messageText')}
                  className="flex-1 bg-transparent text-fg placeholder-fg-muted resize-none outline-none text-[15px] leading-6 py-1.5 min-h-[36px] max-h-40 disabled:opacity-50"
                />
                <button
                  onClick={() => handleSend()}
                  disabled={!inputValue.trim() || isSending || hasIndexingAttachment}
                  title={hasIndexingAttachment ? t('chat.waitIndexing') : undefined}
                  className="shrink-0 w-9 h-9 rounded-lg bg-accent-solid text-accent-fg flex items-center justify-center hover:bg-accent-solid-hover disabled:opacity-40 disabled:cursor-not-allowed transition-all"
                  aria-label={t('chat.send')}
                >
                  {isSending ? (
                    <Spinner size="sm" className="border-accent-fg/30 border-t-accent-fg" />
                  ) : (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <line x1="22" y1="2" x2="11" y2="13" />
                      <polygon points="22 2 15 22 11 13 2 9 22 2" />
                    </svg>
                  )}
                </button>
              </div>
              <p className="text-xs text-center text-fg-muted mt-2 opacity-70">
                {t('chat.disclaimer')}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
