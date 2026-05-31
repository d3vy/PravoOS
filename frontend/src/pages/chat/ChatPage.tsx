import {
  useState,
  useRef,
  useEffect,
  type KeyboardEvent,
} from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { chatApi } from '../../api/chat'
import type { MessageResponse, ConversationResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'
import { Navbar } from '../../components/layout/Navbar'
import { ScalesIcon } from '../../components/ui/Logo'
import { RatingButtons } from '../../components/ui/RatingButtons'

interface LocalMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: string[]
  rating?: number | null
  isStreaming?: boolean
}

const LOCAL_ID_PREFIXES = ['user-', 'assistant-', 'loading-', 'error-']

function isPersistedId(id: string): boolean {
  return !LOCAL_ID_PREFIXES.some((prefix) => id.startsWith(prefix))
}

const SUGGESTIONS = [
  'Какой срок исковой давности по договору поставки?',
  'Условия расторжения трудового договора по инициативе работодателя',
  'Требования к форме доверенности',
]

export default function ChatPage(): JSX.Element {
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<LocalMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const skipNextHistorySyncRef = useRef(false)
  const queryClient = useQueryClient()

  const { data: conversations = [], isLoading: conversationsLoading } = useQuery<ConversationResponse[]>({
    queryKey: ['conversations'],
    queryFn: chatApi.getConversations,
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

  const sendMessageMutation = useMutation({
    mutationFn: chatApi.sendMessage,
    onSuccess: (data) => {
      setMessages((prev) =>
        prev.map((m) =>
          m.isStreaming
            ? {
                id: `assistant-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                role: 'ASSISTANT',
                content: data.answer,
                sources: data.sources,
              }
            : m
        )
      )
      if (data.conversationId !== activeConversationId) {
        skipNextHistorySyncRef.current = true
        setActiveConversationId(data.conversationId)
      }
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
    },
    onError: () => {
      setMessages((prev) => [
        ...prev.filter((m) => !m.isStreaming),
        {
          id: `error-${Date.now()}`,
          role: 'ASSISTANT',
          content: 'Произошла ошибка при обработке запроса. Попробуйте ещё раз.',
        },
      ])
    },
    onSettled: () => {
      setIsSending(false)
    },
  })

  const rateMutation = useMutation({
    mutationFn: ({ messageId, rating }: { messageId: string; rating: number }) =>
      chatApi.rateMessage(messageId, { rating }),
    onMutate: ({ messageId, rating }) => {
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, rating } : m)))
    },
  })

  const handleSend = (): void => {
    const message = inputValue.trim()
    if (!message || isSending) return

    setInputValue('')
    setIsSending(true)

    const baseId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const userMessage: LocalMessage = {
      id: `user-${baseId}`,
      role: 'USER',
      content: message,
    }
    const loadingMessage: LocalMessage = {
      id: `loading-${baseId}`,
      role: 'ASSISTANT',
      content: '',
      isStreaming: true,
    }

    setMessages((prev) => [...prev, userMessage, loadingMessage])

    sendMessageMutation.mutate({
      conversationId: activeConversationId ?? undefined,
      message,
    })

    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
    }
  }

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
  }

  const selectConversation = (id: string): void => {
    setActiveConversationId(id)
    setSidebarOpen(false)
  }

  return (
    <div className="h-screen bg-light-bg dark:bg-dark-bg flex flex-col">
      <Navbar />

      <div className="flex flex-1 overflow-hidden min-h-0">
        {/* Mobile sidebar toggle */}
        {!sidebarOpen && (
          <button
            onClick={() => setSidebarOpen(true)}
            className="md:hidden fixed top-[72px] left-3 z-30 w-10 h-10 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary shadow-card dark:shadow-card-dark flex items-center justify-center"
            aria-label="Открыть список диалогов"
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
            md:translate-x-0
            fixed md:relative z-20 md:z-auto
            w-72 md:w-64 h-full
            flex flex-col
            bg-light-surface dark:bg-dark-surface
            border-r border-light-border dark:border-dark-border
            transition-transform duration-200 md:transition-none
          `}
        >
          <div className="p-4 border-b border-light-border dark:border-dark-border">
            <button
              onClick={startNewChat}
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-light-accent dark:bg-dark-accent text-white text-sm font-medium hover:bg-light-accent-hover dark:hover:bg-dark-accent-hover transition-colors"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              Новый чат
            </button>
          </div>

          <div className="flex-1 overflow-y-auto scrollbar-thin p-2">
            {conversationsLoading ? (
              <div className="flex justify-center py-8">
                <Spinner size="sm" />
              </div>
            ) : conversations.length === 0 ? (
              <p className="text-xs text-center text-light-secondary dark:text-dark-secondary py-8 px-4">
                Нет диалогов. Начните новый чат.
              </p>
            ) : (
              <div className="flex flex-col gap-0.5">
                {conversations.map((conv) => (
                  <button
                    key={conv.id}
                    onClick={() => selectConversation(conv.id)}
                    className={`
                      w-full text-left px-3 py-2.5 rounded-lg text-sm transition-colors
                      ${activeConversationId === conv.id
                        ? 'bg-light-accent/10 dark:bg-dark-accent/10 text-light-accent dark:text-dark-accent'
                        : 'text-light-text dark:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg'
                      }
                    `}
                  >
                    <span className="block truncate font-medium">{conv.title}</span>
                    <span className="block text-xs text-light-secondary dark:text-dark-secondary mt-0.5">
                      {new Date(conv.createdAt).toLocaleDateString('ru-RU')}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </aside>

        {/* Overlay for mobile sidebar */}
        {sidebarOpen && (
          <div
            className="md:hidden fixed inset-0 z-10 bg-black/40 backdrop-blur-sm"
            onClick={() => setSidebarOpen(false)}
          />
        )}

        {/* Main chat area */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* Messages */}
          <div className="flex-1 overflow-y-auto scrollbar-thin px-4 py-6">
            {messagesLoading ? (
              <div className="flex justify-center py-12">
                <Spinner size="md" />
              </div>
            ) : messages.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-center max-w-md mx-auto">
                <div className="w-14 h-14 rounded-2xl bg-light-accent/8 dark:bg-dark-accent/15 flex items-center justify-center text-light-gold dark:text-dark-gold mb-5">
                  <ScalesIcon className="w-7 h-7" />
                </div>
                <h2 className="font-display text-2xl font-semibold text-light-text dark:text-dark-text mb-3">
                  Задайте вопрос по правовой базе
                </h2>
                <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed">
                  AI-ассистент ответит на основе загруженных документов и укажет источники.
                  Задавайте вопросы на естественном языке.
                </p>
                <div className="mt-8 grid grid-cols-1 gap-2 w-full max-w-sm">
                  {SUGGESTIONS.map((suggestion) => (
                    <button
                      key={suggestion}
                      onClick={() => {
                        setInputValue(suggestion)
                        textareaRef.current?.focus()
                      }}
                      className="text-left text-sm px-4 py-2.5 rounded-lg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:border-light-accent/40 dark:hover:border-dark-accent/40 hover:bg-light-surface dark:hover:bg-dark-surface transition-colors"
                    >
                      {suggestion}
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
                      onRate={(rating) => rateMutation.mutate({ messageId: message.id, rating })}
                    />
                  ))}
                </AnimatePresence>
                <div ref={messagesEndRef} />
              </div>
            )}
          </div>

          {/* Input area */}
          <div className="border-t border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface p-4">
            <div className="max-w-3xl mx-auto">
              <div className="flex gap-3 items-end rounded-xl border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg focus-within:border-light-accent dark:focus-within:border-dark-accent focus-within:ring-1 focus-within:ring-light-accent dark:focus-within:ring-dark-accent transition-all px-4 py-3">
                <textarea
                  ref={textareaRef}
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyDown={handleKeyDown}
                  onInput={handleTextareaInput}
                  placeholder="Задайте вопрос... (Enter — отправить)"
                  rows={1}
                  disabled={isSending}
                  aria-label="Текст сообщения"
                  className="flex-1 bg-transparent text-light-text dark:text-dark-text placeholder-light-secondary dark:placeholder-dark-secondary resize-none outline-none text-sm leading-relaxed min-h-[24px] max-h-40 disabled:opacity-50"
                />
                <button
                  onClick={handleSend}
                  disabled={!inputValue.trim() || isSending}
                  className="shrink-0 w-9 h-9 rounded-lg bg-light-accent dark:bg-dark-accent text-white flex items-center justify-center hover:bg-light-accent-hover dark:hover:bg-dark-accent-hover disabled:opacity-40 disabled:cursor-not-allowed transition-all"
                  aria-label="Отправить"
                >
                  {isSending ? (
                    <Spinner size="sm" className="border-white/30 border-t-white" />
                  ) : (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <line x1="22" y1="2" x2="11" y2="13" />
                      <polygon points="22 2 15 22 11 13 2 9 22 2" />
                    </svg>
                  )}
                </button>
              </div>
              <p className="text-xs text-center text-light-secondary dark:text-dark-secondary mt-2 opacity-70">
                Ответы генерируются на основе загруженных документов. Проверяйте источники.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function MessageBubble({ message, onRate }: { message: LocalMessage; onRate: (rating: number) => void }): JSX.Element {
  const isUser = message.role === 'USER'
  const canRate = !isUser && !message.isStreaming && isPersistedId(message.id)

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, ease: 'easeOut' }}
      className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}
    >
      <div
        className={`
          w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold shrink-0 mt-0.5
          ${isUser
            ? 'bg-light-accent dark:bg-dark-accent text-white'
            : 'bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-light-gold dark:text-dark-gold'
          }
        `}
      >
        {isUser ? 'Вы' : <ScalesIcon className="w-4 h-4" />}
      </div>

      <div className={`flex flex-col gap-2 max-w-[80%] ${isUser ? 'items-end' : 'items-start'}`}>
        <div
          className={`
            px-4 py-3 rounded-2xl text-sm leading-relaxed
            ${isUser
              ? 'bg-light-accent dark:bg-dark-accent text-white rounded-tr-sm'
              : 'bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-light-text dark:text-dark-text rounded-tl-sm'
            }
          `}
        >
          {message.isStreaming ? (
            <TypingDots />
          ) : (
            <p className="whitespace-pre-wrap break-words">{message.content}</p>
          )}
        </div>

        {!message.isStreaming && message.sources && message.sources.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5 max-w-full">
            <span className="text-xs text-light-secondary dark:text-dark-secondary">Источники:</span>
            {message.sources.map((source, idx) => (
              <span
                key={idx}
                className="text-xs px-2 py-0.5 rounded-full bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary truncate max-w-[200px]"
                title={source}
              >
                {source}
              </span>
            ))}
          </div>
        )}

        {canRate && (
          <RatingButtons rating={message.rating} onRate={onRate} />
        )}
      </div>
    </motion.div>
  )
}

function TypingDots(): JSX.Element {
  return (
    <span className="inline-flex items-center gap-1 py-0.5">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="w-2 h-2 rounded-full bg-current opacity-60 animate-bounce"
          style={{ animationDelay: `${i * 150}ms` }}
        />
      ))}
    </span>
  )
}
