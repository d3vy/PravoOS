import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { citationsApi } from '../../api/citations'
import { Spinner } from '../ui/Spinner'
import { PravoIcon } from '../ui/Logo'
import { RatingButtons } from '../ui/RatingButtons'
import { CitationList, citationSummary } from '../ui/CitationList'

export interface LocalMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: string[]
  rating?: number | null
  followUps?: string[]
  isStreaming?: boolean
  autoCheckCitations?: boolean
}

const LOCAL_ID_PREFIXES = ['user-', 'assistant-', 'loading-', 'error-']

export function isPersistedId(id: string): boolean {
  return !LOCAL_ID_PREFIXES.some((prefix) => id.startsWith(prefix))
}

function CopyButton({ text }: { text: string }): JSX.Element {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const handleCopy = (): void => {
    void navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }
  return (
    <button
      onClick={handleCopy}
      title={t('chat.copy')}
      className="text-fg-muted hover:text-fg transition-colors"
    >
      {copied ? (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
          <polyline points="20 6 9 17 4 12" />
        </svg>
      ) : (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </svg>
      )}
    </button>
  )
}

export function MessageBubble({
  message,
  onRate,
}: {
  message: LocalMessage
  onRate: (rating: number, comment?: string) => void
}): JSX.Element {
  const { t } = useTranslation()
  const isUser = message.role === 'USER'
  const canRate = !isUser && !message.isStreaming && isPersistedId(message.id)
  const showCitations = !isUser && !message.isStreaming && Boolean(message.content)

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, ease: 'easeOut' }}
      className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}
    >
      <div
        className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold shrink-0 mt-0.5 ${
          isUser
            ? 'bg-accent-solid text-accent-fg'
            : 'bg-surface border border-line text-fg-muted'
        }`}
      >
        {isUser ? t('chat.you') : <PravoIcon className="w-4 h-4" />}
      </div>

      <div className={`flex flex-col gap-2 min-w-0 max-w-[80%] ${isUser ? 'items-end' : 'items-start'}`}>
        <div
          className={`px-4 py-3 rounded-2xl text-sm leading-relaxed ${
            isUser
              ? 'bg-accent-solid text-accent-fg rounded-tr-sm'
              : 'bg-surface border border-line text-fg rounded-tl-sm'
          }`}
        >
          {message.isStreaming && !message.content ? (
            <TypingDots />
          ) : (
            <p className="whitespace-pre-wrap [overflow-wrap:anywhere]">
              {message.content}
              {message.isStreaming && <span className="ml-0.5 inline-block w-1.5 h-4 -mb-0.5 bg-current opacity-60 animate-pulse" />}
            </p>
          )}
        </div>

        {!message.isStreaming && message.sources && message.sources.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5 max-w-full">
            <span className="text-xs text-fg-muted">{t('chat.sources')}</span>
            {message.sources.map((source, idx) => (
              <span
                key={idx}
                className="text-xs px-2 py-0.5 rounded-full bg-bg border border-line text-fg-muted truncate max-w-[200px]"
                title={source}
              >
                {source}
              </span>
            ))}
          </div>
        )}

        {showCitations && (
          <ChatCitations text={message.content} auto={message.autoCheckCitations} />
        )}

        {!isUser && !message.isStreaming && message.content && (
          <div className="flex items-center gap-3">
            <CopyButton text={message.content} />
            {canRate && <RatingButtons rating={message.rating} onRate={(rating) => onRate(rating)} />}
          </div>
        )}

        {canRate && message.rating === -1 && <FeedbackBox onSubmit={(comment) => onRate(-1, comment)} />}
      </div>
    </motion.div>
  )
}

function ChatCitations({ text, auto }: { text: string; auto?: boolean }): JSX.Element {
  const { t } = useTranslation()
  const checkMutation = useMutation({
    mutationFn: () => citationsApi.checkText(text),
  })
  const { mutate } = checkMutation
  const autoRanRef = useRef(false)

  useEffect(() => {
    if (auto && !autoRanRef.current) {
      autoRanRef.current = true
      mutate()
    }
  }, [auto, mutate])

  const result = checkMutation.data

  return (
    <div className="w-full flex flex-col gap-1.5">
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => checkMutation.mutate()}
          disabled={checkMutation.isPending}
          className="text-xs text-fg-muted hover:text-accent transition-colors disabled:opacity-50 inline-flex items-center gap-1.5"
        >
          {checkMutation.isPending ? (
            <Spinner size="sm" />
          ) : (
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 12l2 2 4-4" />
              <circle cx="12" cy="12" r="9" />
            </svg>
          )}
          {t('chat.checkCitations')}
        </button>
        {result && (
          <span className="text-xs text-fg-muted">
            {citationSummary(result)}
          </span>
        )}
      </div>
      {checkMutation.isError && (
        <p className="text-xs text-danger">{t('chat.citationError')}</p>
      )}
      {result && <CitationList result={result} />}
    </div>
  )
}

function FeedbackBox({ onSubmit }: { onSubmit: (comment: string) => void }): JSX.Element {
  const { t } = useTranslation()
  const [comment, setComment] = useState('')
  const [sent, setSent] = useState(false)

  if (sent) {
    return <p className="text-xs text-fg-muted">{t('chat.feedbackThanks')}</p>
  }

  return (
    <div className="w-full flex flex-col gap-1.5 max-w-md">
      <textarea
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        placeholder={t('chat.feedbackPlaceholder')}
        rows={2}
        className="w-full resize-none rounded-lg border border-line bg-bg px-3 py-2 text-xs text-fg placeholder-fg-muted focus:outline-none focus:ring-1 focus:ring-accent"
      />
      <button
        type="button"
        onClick={() => { onSubmit(comment.trim()); setSent(true) }}
        className="self-start text-xs px-3 py-1.5 rounded-lg bg-accent-solid text-accent-fg hover:bg-accent-solid-hover transition-colors"
      >
        {t('chat.sendFeedback')}
      </button>
    </div>
  )
}

export function TypingDots(): JSX.Element {
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
