import { useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import i18n from '../../i18n'
import { chatApi } from '../../api/chat'
import { useConfirmStore } from '../../store/confirmStore'
import type { DocumentResponse } from '../../types'
import { useAiChatStore } from '../../store/aiChatStore'
import { useScrollToBottom } from '../../hooks/useScrollToBottom'
import { autosizeTextarea, resetTextareaHeight, lastFollowUps } from '../../lib/chat/chatSession'
import { Spinner } from '../../components/ui/Spinner'
import { PravoIcon } from '../../components/ui/Logo'
import { MessageBubble } from '../../components/chat/ChatMessageBubble'

const TEXTAREA_MAX_HEIGHT = 160
const SUGGESTION_KEYS = ['chat.suggestion1', 'chat.suggestion2', 'chat.suggestion3']

function locale(): string {
  return i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
}

export default function AiPage(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [attachPickerOpen, setAttachPickerOpen] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)

  const messages = useAiChatStore((state) => state.messages)
  const isSending = useAiChatStore((state) => state.isSending)
  const historyLoading = useAiChatStore((state) => state.historyLoading)
  const conversationId = useAiChatStore((state) => state.conversationId)
  const draft = useAiChatStore((state) => state.draft)
  const setDraft = useAiChatStore((state) => state.setDraft)
  const attachedDocumentIds = useAiChatStore((state) => state.attachedDocumentIds)
  const toggleAttachedDocument = useAiChatStore((state) => state.toggleAttachedDocument)
  const send = useAiChatStore((state) => state.send)
  const stop = useAiChatStore((state) => state.stop)
  const rate = useAiChatStore((state) => state.rate)
  const startNewChat = useAiChatStore((state) => state.startNewChat)
  const selectConversation = useAiChatStore((state) => state.selectConversation)

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const attachPickerRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useScrollToBottom(messagesEndRef, messages)

  useEffect(() => {
    const requestedConversation = searchParams.get('conversation')
    const ask = searchParams.get('ask')
    if (requestedConversation && requestedConversation !== useAiChatStore.getState().conversationId) {
      void useAiChatStore.getState().selectConversation(requestedConversation)
    }
    if (ask) {
      useAiChatStore.getState().setDraft(ask)
      requestAnimationFrame(() => textareaRef.current?.focus())
    }
    if (requestedConversation || ask) {
      setSearchParams({}, { replace: true })
    }
  }, [searchParams, setSearchParams])

  useEffect(() => {
    if (!attachPickerOpen) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (attachPickerRef.current && !attachPickerRef.current.contains(event.target as Node)) {
        setAttachPickerOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [attachPickerOpen])

  const {
    data: conversationPages,
    isLoading: conversationsLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['conversations', searchQuery],
    queryFn: ({ pageParam }) =>
      chatApi.getConversations(searchQuery || undefined, undefined, undefined, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage, loadedPages) => {
      const loaded = loadedPages.reduce((count, current) => count + current.items.length, 0)
      return loaded < lastPage.total ? loadedPages.length : undefined
    },
  })
  const conversations = conversationPages?.pages.flatMap((current) => current.items) ?? []

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
      setUploadError(null)
      toggleAttachedDocument(uploaded.id)
      queryClient.invalidateQueries({ queryKey: ['chat', 'attachments'] })
    },
    onError: () => setUploadError(t('chat.uploadError')),
  })

  const handleSend = (text?: string): void => {
    const message = text ?? draft
    if (!message.trim() || isSending || hasIndexingAttachment) return
    setAttachPickerOpen(false)
    resetTextareaHeight(textareaRef.current)
    send(message)
    queryClient.invalidateQueries({ queryKey: ['conversations'] })
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>): void => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
  }

  const handleFileSelected = (event: ChangeEvent<HTMLInputElement>): void => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setAttachPickerOpen(false)
    uploadMutation.mutate(file)
  }

  const handleSelectConversation = (id: string): void => {
    void selectConversation(id)
    setSidebarOpen(false)
  }

  const handleDeleteConversation = async (conversation: {
    id: string
    title: string
  }): Promise<void> => {
    const confirmed = await useConfirmStore.getState().ask({
      title: t('chat.deleteConfirmTitle'),
      description: t('chat.deleteConfirmDescription', { title: conversation.title }),
      confirmLabel: t('common.delete'),
      danger: true,
    })
    if (!confirmed) return
    await chatApi.deleteConversation(conversation.id)
    if (conversationId === conversation.id) {
      startNewChat()
    }
    await queryClient.invalidateQueries({ queryKey: ['conversations'] })
  }

  const handleNewChat = (): void => {
    startNewChat()
    setSidebarOpen(false)
  }

  const followUps = lastFollowUps(messages)
  const lastAssistant = [...messages].reverse().find((m) => m.role === 'ASSISTANT' && !m.isStreaming)
  const liveAnnouncement = isSending ? t('chat.a11yGenerating') : lastAssistant?.content ?? ''

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
              onClick={handleNewChat}
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-accent-solid text-accent-fg text-sm font-medium hover:bg-accent-solid-hover transition-colors"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              {t('chat.newChat')}
            </button>

            <div className="relative">
              <svg className="absolute left-2.5 top-1/2 -translate-y-1/2 text-fg-muted w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
              <input
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder={t('chat.searchPlaceholder')}
                className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg border border-line bg-bg text-fg placeholder-fg-muted focus:outline-none focus:ring-1 focus:ring-accent"
              />
            </div>
          </div>

          <div className="flex-1 overflow-y-auto scrollbar-thin p-2">
            {conversationsLoading ? (
              <div className="flex justify-center py-8">
                <Spinner size="sm" />
              </div>
            ) : conversations.length === 0 ? (
              <p className="text-xs text-center text-fg-muted py-8 px-4">
                {searchQuery ? t('chat.nothingFound') : t('chat.noConversations')}
              </p>
            ) : (
              <div className="flex flex-col gap-0.5">
                {conversations.map((conversation) => (
                  <div
                    key={conversation.id}
                    className={`group flex items-center gap-1 rounded-lg transition-colors ${
                      conversationId === conversation.id
                        ? 'bg-accent/10 text-accent'
                        : 'text-fg hover:bg-bg'
                    }`}
                  >
                    <button
                      onClick={() => handleSelectConversation(conversation.id)}
                      className="flex-1 min-w-0 text-left px-3 py-2.5 text-sm"
                    >
                      <span className="block truncate font-medium">{conversation.title}</span>
                      <span className="block text-xs text-fg-muted mt-0.5">
                        {new Date(conversation.updatedAt).toLocaleDateString(locale())}
                      </span>
                    </button>
                    <button
                      onClick={() => void handleDeleteConversation(conversation)}
                      aria-label={t('chat.deleteConversation')}
                      title={t('chat.deleteConversation')}
                      className="shrink-0 mr-1.5 p-1.5 rounded-md text-fg-muted opacity-0 group-hover:opacity-100 focus:opacity-100 hover:text-danger hover:bg-danger/10 transition-opacity"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                        />
                      </svg>
                    </button>
                  </div>
                ))}
                {hasNextPage && (
                  <button
                    onClick={() => void fetchNextPage()}
                    disabled={isFetchingNextPage}
                    className="mt-1 w-full px-3 py-2 rounded-lg text-xs font-medium text-fg-muted hover:bg-bg disabled:opacity-50 transition-colors"
                  >
                    {isFetchingNextPage ? t('common.loading') : t('chat.loadMoreConversations')}
                  </button>
                )}
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

        <div className="flex-1 flex flex-col min-w-0">
          <div className="flex-1 overflow-y-auto scrollbar-thin px-4 py-6">
            {historyLoading ? (
              <div className="flex justify-center py-12">
                <Spinner size="md" />
              </div>
            ) : messages.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-center max-w-md mx-auto">
                <div className="w-14 h-14 rounded-2xl bg-surface border border-line flex items-center justify-center text-fg-muted mb-5">
                  <PravoIcon className="w-7 h-7" />
                </div>
                <h2 className="text-2xl font-semibold text-fg mb-3">{t('chat.emptyTitle')}</h2>
                <p className="text-sm text-fg-muted leading-relaxed">{t('chat.emptySubtitle')}</p>
                <div className="mt-8 grid grid-cols-1 gap-2 w-full max-w-sm">
                  {SUGGESTION_KEYS.map((suggestionKey) => (
                    <button
                      key={suggestionKey}
                      onClick={() => {
                        setDraft(t(suggestionKey))
                        textareaRef.current?.focus()
                      }}
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
                      onRate={(rating, comment) => void rate(message.id, rating, comment)}
                    />
                  ))}
                </AnimatePresence>

                {!isSending && followUps.length > 0 && (
                  <motion.div
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex flex-col gap-2 max-w-[80%]"
                  >
                    <p className="text-xs text-fg-muted ml-11">{t('chat.followUps')}</p>
                    <div className="flex flex-col gap-1.5 ml-11">
                      {followUps.map((followUp) => (
                        <button
                          key={followUp}
                          onClick={() => handleSend(followUp)}
                          className="text-left text-xs px-3 py-2 rounded-lg border border-accent/30 text-accent hover:bg-accent/5 transition-colors"
                        >
                          {followUp}
                        </button>
                      ))}
                    </div>
                  </motion.div>
                )}

                <div ref={messagesEndRef} />
              </div>
            )}
          </div>

          <div className="border-t border-line bg-surface p-4">
            <div className="max-w-3xl mx-auto">
              {(attachedDocuments.length > 0 || uploadMutation.isPending || uploadError) && (
                <div className="flex flex-wrap items-center gap-1.5 mb-2">
                  {attachedDocuments.map((document) => (
                    <span
                      key={document.id}
                      className={`inline-flex items-center gap-1.5 text-xs px-2 py-0.5 rounded-full border ${
                        document.status === 'FAILED'
                          ? 'bg-red-500/10 text-danger border-red-500/20'
                          : 'bg-accent/10 text-accent border-accent/20'
                      }`}
                    >
                      {document.status === 'PROCESSING' && <Spinner size="sm" />}
                      {document.title}
                      {document.status === 'PROCESSING' && (
                        <span className="opacity-70">{t('chat.indexing')}</span>
                      )}
                      {document.status === 'FAILED' && (
                        <span className="opacity-70">{t('chat.processFailed')}</span>
                      )}
                      <button
                        onClick={() => toggleAttachedDocument(document.id)}
                        className="hover:opacity-70 ml-0.5"
                        aria-label={t('chat.removeDoc')}
                      >
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                          <line x1="18" y1="6" x2="6" y2="18" />
                          <line x1="6" y1="6" x2="18" y2="18" />
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
                  {uploadError && <span className="text-xs text-danger">{uploadError}</span>}
                </div>
              )}

              <div className="flex gap-3 items-end rounded-xl border border-line bg-bg focus-within:border-accent focus-within:ring-1 focus-within:ring-accent transition-all px-4 py-3">
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
                    onClick={() => setAttachPickerOpen((current) => !current)}
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
                          <line x1="12" y1="5" x2="12" y2="19" />
                          <line x1="5" y1="12" x2="19" y2="12" />
                        </svg>
                        {t('chat.uploadFromComputer')}
                      </button>

                      {attachments.length > 0 && (
                        <>
                          <p className="text-xs font-medium text-fg-muted px-3 pt-3 pb-2 border-t border-line">
                            {t('chat.previouslyUploaded')}
                          </p>
                          {attachments.map((document) => (
                            <button
                              key={document.id}
                              onClick={() => {
                                toggleAttachedDocument(document.id)
                                setAttachPickerOpen(false)
                              }}
                              className={`w-full text-left px-3 py-2 text-sm transition-colors flex items-center gap-2 ${
                                attachedDocumentIds.includes(document.id)
                                  ? 'text-accent bg-accent/5'
                                  : 'text-fg hover:bg-bg'
                              }`}
                            >
                              <span className="text-xs font-bold uppercase text-fg-muted w-7 shrink-0">
                                {document.fileName.split('.').pop()}
                              </span>
                              <span className="min-w-0 truncate">{document.title}</span>
                              {attachedDocumentIds.includes(document.id) && (
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
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={handleKeyDown}
                  onInput={() => autosizeTextarea(textareaRef.current, TEXTAREA_MAX_HEIGHT)}
                  placeholder={t('chat.askPlaceholder')}
                  rows={1}
                  aria-label={t('chat.messageText')}
                  className="flex-1 bg-transparent text-fg placeholder-fg-muted resize-none outline-none text-[15px] leading-6 py-1.5 min-h-[36px] max-h-40"
                />
                <button
                  onClick={() => (isSending ? stop() : handleSend())}
                  disabled={!isSending && (!draft.trim() || hasIndexingAttachment)}
                  title={hasIndexingAttachment ? t('chat.waitIndexing') : undefined}
                  className="shrink-0 w-9 h-9 rounded-lg bg-accent-solid text-accent-fg flex items-center justify-center hover:bg-accent-solid-hover disabled:opacity-40 disabled:cursor-not-allowed transition-all"
                  aria-label={isSending ? t('aiWidget.stop') : t('chat.send')}
                >
                  {isSending ? (
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                      <rect x="5" y="5" width="14" height="14" rx="2" />
                    </svg>
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
