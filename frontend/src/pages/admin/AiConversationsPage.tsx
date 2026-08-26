import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi, DEFAULT_PAGE_SIZE, type Page } from '../../api/admin'
import i18n from '../../i18n'
import type { AdminConversationResponse, MessageResponse } from '../../types'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { Spinner } from '../../components/ui/Spinner'
import { EmptyState } from '../../components/ui/EmptyState'

function locale(): string {
  return i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
}

function formatMoment(value: string): string {
  return new Date(value).toLocaleString(locale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function AiConversationsPage(): JSX.Element {
  const { t } = useTranslation()
  const [orgId, setOrgId] = useState('')
  const [lawyerId, setLawyerId] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const filters = { orgId: orgId.trim(), lawyerId: lawyerId.trim(), q: query.trim() }

  const { data, isLoading } = useQuery<Page<AdminConversationResponse>>({
    queryKey: ['admin-conversations', filters.orgId, filters.lawyerId, filters.q, page],
    queryFn: () => adminApi.getConversations(filters, page),
    placeholderData: keepPreviousData,
  })
  const conversations = data?.items ?? []
  const total = data?.total ?? 0

  const { data: transcript, isLoading: transcriptLoading } = useQuery<Page<MessageResponse>>({
    queryKey: ['admin-conversation-messages', selectedId],
    queryFn: () => adminApi.getConversationMessages(selectedId as string),
    enabled: selectedId !== null,
  })

  const scopeLabel = (conversation: AdminConversationResponse): string => {
    if (conversation.caseId) return t('admin.conversationsScopeCase')
    if (conversation.documentId) return t('admin.conversationsScopeDocument')
    return t('admin.conversationsScopeGeneral')
  }

  const resetPageAnd = (apply: () => void): void => {
    apply()
    setPage(0)
    setSelectedId(null)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('admin.aiConversationsTitle')}
        description={t('admin.aiConversationsSubtitle')}
      />

      <div className="grid gap-3 sm:grid-cols-3">
        <input
          type="text"
          value={orgId}
          onChange={(event) => resetPageAnd(() => setOrgId(event.target.value))}
          placeholder={t('admin.conversationsOrgFilter')}
          className="px-3 py-2 text-sm rounded-lg border border-line bg-surface text-fg placeholder-fg-muted focus:outline-none focus:ring-1 focus:ring-accent"
        />
        <input
          type="text"
          value={lawyerId}
          onChange={(event) => resetPageAnd(() => setLawyerId(event.target.value))}
          placeholder={t('admin.conversationsLawyerFilter')}
          className="px-3 py-2 text-sm rounded-lg border border-line bg-surface text-fg placeholder-fg-muted focus:outline-none focus:ring-1 focus:ring-accent"
        />
        <input
          type="search"
          value={query}
          onChange={(event) => resetPageAnd(() => setQuery(event.target.value))}
          placeholder={t('admin.conversationsSearch')}
          className="px-3 py-2 text-sm rounded-lg border border-line bg-surface text-fg placeholder-fg-muted focus:outline-none focus:ring-1 focus:ring-accent"
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
        <div className="rounded-xl border border-line bg-surface overflow-hidden">
          {isLoading ? (
            <div className="flex justify-center py-12">
              <Spinner />
            </div>
          ) : conversations.length === 0 ? (
            <EmptyState description={t('admin.conversationsEmpty')} illustration="none" />
          ) : (
            <ul className="divide-y divide-line">
              {conversations.map((conversation) => (
                <li key={conversation.id}>
                  <button
                    onClick={() => setSelectedId(conversation.id)}
                    className={`w-full text-left px-4 py-3 transition-colors ${
                      selectedId === conversation.id ? 'bg-accent/10' : 'hover:bg-bg'
                    }`}
                  >
                    <span className="block truncate text-sm font-medium text-fg">
                      {conversation.title}
                    </span>
                    <span className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-fg-muted">
                      <span>
                        {t('admin.conversationsLawyer')}: {conversation.lawyerId}
                      </span>
                      <span>
                        {t('admin.conversationsOrg')}:{' '}
                        {conversation.orgId ?? t('admin.conversationsNoOrg')}
                      </span>
                      <span>{scopeLabel(conversation)}</span>
                      <span>
                        {t('admin.conversationsUpdated')}: {formatMoment(conversation.updatedAt)}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="rounded-xl border border-line bg-surface p-4 min-h-[320px]">
          {selectedId === null ? (
            <p className="text-sm text-fg-muted">{t('admin.conversationsSelectHint')}</p>
          ) : transcriptLoading ? (
            <div className="flex justify-center py-12">
              <Spinner />
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {(transcript?.items ?? []).map((message) => (
                <div
                  key={message.id}
                  className={`rounded-lg px-3 py-2 text-sm whitespace-pre-wrap ${
                    message.role === 'USER' ? 'bg-bg text-fg' : 'bg-accent/10 text-fg'
                  }`}
                >
                  <span className="block text-xs text-fg-muted mb-1">
                    {formatMoment(message.createdAt)}
                  </span>
                  {message.content}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={total} onPageChange={setPage} />
    </div>
  )
}
