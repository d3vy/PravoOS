import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Spinner } from '../ui/Spinner'
import { Button } from '../ui/Button'
import type { CaseMessageResponse, MessageAuthorRole } from '../../types'

interface CaseMessageThreadProps {
  queryKey: (string | undefined)[]
  viewerRole: MessageAuthorRole
  listMessages: () => Promise<CaseMessageResponse[]>
  sendMessage: (body: string) => Promise<CaseMessageResponse>
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function authorLabel(role: MessageAuthorRole): string {
  return role === 'CLIENT' ? 'Клиент' : 'Юрист'
}

export function CaseMessageThread({
  queryKey,
  viewerRole,
  listMessages,
  sendMessage,
}: CaseMessageThreadProps): JSX.Element {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  const { data: messages = [], isLoading } = useQuery<CaseMessageResponse[]>({
    queryKey,
    queryFn: listMessages,
  })

  const sendMutation = useMutation({
    mutationFn: sendMessage,
    onSuccess: () => {
      setDraft('')
      queryClient.invalidateQueries({ queryKey })
    },
  })

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  const handleSubmit = (event: React.FormEvent): void => {
    event.preventDefault()
    const trimmed = draft.trim()
    if (!trimmed || sendMutation.isPending) return
    sendMutation.mutate(trimmed)
  }

  return (
    <section>
      <h2 className="text-lg font-semibold text-light-text dark:text-dark-text mb-3">Переписка</h2>

      <div className="card-elevated rounded-xl p-4">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner />
          </div>
        ) : (
          <div className="max-h-96 overflow-y-auto flex flex-col gap-3 mb-4">
            {messages.length === 0 ? (
              <p className="text-light-secondary dark:text-dark-secondary text-sm text-center py-6">
                Сообщений пока нет. Напишите первым.
              </p>
            ) : (
              messages.map((message) => {
                const mine = message.authorRole === viewerRole
                return (
                  <div key={message.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                    <div
                      className={`max-w-[80%] rounded-2xl px-4 py-2.5 ${
                        mine
                          ? 'bg-light-accent dark:bg-dark-accent text-white dark:text-dark-bg'
                          : 'bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-light-text dark:text-dark-text'
                      }`}
                    >
                      <div className="flex items-center gap-2 mb-0.5">
                        <span className="text-xs font-semibold opacity-80">{authorLabel(message.authorRole)}</span>
                        <span className="text-xs opacity-60">{formatTimestamp(message.createdAt)}</span>
                      </div>
                      <p className="text-sm whitespace-pre-line break-words">{message.body}</p>
                    </div>
                  </div>
                )
              })
            )}
            <div ref={bottomRef} />
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-2">
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
                handleSubmit(event)
              }
            }}
            rows={3}
            maxLength={5000}
            placeholder="Написать сообщение…"
            className="input-base w-full resize-none"
          />
          <div className="flex items-center justify-between gap-3">
            {sendMutation.isError ? (
              <p className="text-sm text-red-600 dark:text-red-400">Не удалось отправить. Попробуйте снова.</p>
            ) : (
              <span className="text-xs text-light-secondary dark:text-dark-secondary">Ctrl/⌘ + Enter — отправить</span>
            )}
            <Button
              type="submit"
              size="sm"
              loading={sendMutation.isPending}
              disabled={!draft.trim()}
              className="shrink-0"
            >
              Отправить
            </Button>
          </div>
        </form>
      </div>
    </section>
  )
}
