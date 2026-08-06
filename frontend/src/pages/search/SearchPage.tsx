import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { searchApi } from '../../api/search'
import type { GlobalSearchResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'
import { CaseStatusBadge } from '../../components/ui/Badge'
import { PageHeader } from '../../components/ui/PageHeader'

const MIN_QUERY_LENGTH = 2

export default function SearchPage(): JSX.Element {
  const { t } = useTranslation()
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState(searchParams.get('q') ?? '')
  const [debouncedSearch, setDebouncedSearch] = useState(search.trim())
  const [searchContent, setSearchContent] = useState(true)

  useEffect(() => {
    const timer = setTimeout(() => {
      const trimmed = search.trim()
      setDebouncedSearch(trimmed)
      setSearchParams(trimmed ? { q: trimmed } : {}, { replace: true })
    }, 300)
    return () => clearTimeout(timer)
  }, [search, setSearchParams])

  const enabled = debouncedSearch.length >= MIN_QUERY_LENGTH

  const { data, isFetching } = useQuery<GlobalSearchResponse>({
    queryKey: ['global-search', debouncedSearch, searchContent],
    queryFn: () => searchApi.global(debouncedSearch, searchContent),
    enabled,
  })

  const totalHits = data ? data.cases.length + data.conversations.length + data.documents.length : 0

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-3xl">
        <PageHeader
          title={t('search.title')}
          description={t('search.subtitle')}
          className="mb-6"
        />

        <input
          type="search"
          autoFocus
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t('search.placeholder')}
          className="w-full px-4 py-3 rounded-xl border border-line bg-surface text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent"
        />

        <label className="mt-3 flex items-center gap-2 text-sm text-fg-muted cursor-pointer w-fit">
          <input
            type="checkbox"
            checked={searchContent}
            onChange={(e) => setSearchContent(e.target.checked)}
            className="h-4 w-4 accent-accent cursor-pointer"
          />
          {t('search.searchInFiles')}
        </label>

        <div className="mt-6">
          {!enabled ? (
            <p className="text-sm text-fg-muted text-center py-12">
              {t('search.minChars', { count: MIN_QUERY_LENGTH })}
            </p>
          ) : isFetching ? (
            <div className="flex justify-center py-12">
              <Spinner size="lg" />
            </div>
          ) : totalHits === 0 ? (
            <p className="text-sm text-fg-muted text-center py-12">
              {t('search.noResults', { query: debouncedSearch })}
            </p>
          ) : (
            <div className="flex flex-col gap-8">
              {data!.cases.length > 0 && (
                <ResultGroup title={t('search.groupCases')} count={data!.cases.length}>
                  {data!.cases.map((hit) => (
                    <Link
                      key={hit.id}
                      to={`/cases/${hit.id}`}
                      className="flex items-center gap-3 p-3 rounded-lg bg-surface border border-line hover:border-accent/50 transition-colors"
                    >
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-fg truncate">{hit.title}</p>
                        {hit.clientName && (
                          <p className="text-xs text-accent truncate">{hit.clientName}</p>
                        )}
                      </div>
                      <CaseStatusBadge status={hit.status} />
                    </Link>
                  ))}
                </ResultGroup>
              )}

              {data!.conversations.length > 0 && (
                <ResultGroup title={t('search.groupConversations')} count={data!.conversations.length}>
                  {data!.conversations.map((hit) => (
                    <Link
                      key={hit.id}
                      to={`/chat?conversation=${hit.id}`}
                      className="block p-3 rounded-lg bg-surface border border-line hover:border-accent/50 transition-colors"
                    >
                      <p className="text-sm text-fg truncate">{hit.title}</p>
                    </Link>
                  ))}
                </ResultGroup>
              )}

              {data!.documents.length > 0 && (
                <ResultGroup title={t('search.groupDocuments')} count={data!.documents.length}>
                  {data!.documents.map((hit) => (
                    <Link
                      key={hit.id}
                      to={hit.caseId ? `/cases/${hit.caseId}` : '#'}
                      className="flex items-start gap-3 p-3 rounded-lg bg-surface border border-line hover:border-accent/50 transition-colors"
                    >
                      <span className="text-xs font-bold uppercase text-fg-muted w-9 shrink-0 mt-0.5">
                        {hit.fileName.split('.').pop()}
                      </span>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-fg truncate">{hit.title}</p>
                        {hit.snippet && (
                          <p className="text-xs text-fg-muted mt-0.5 line-clamp-2">
                            {hit.snippet}
                          </p>
                        )}
                      </div>
                    </Link>
                  ))}
                </ResultGroup>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function ResultGroup({
  title,
  count,
  children,
}: {
  title: string
  count: number
  children: React.ReactNode
}): JSX.Element {
  return (
    <section>
      <h2 className="text-sm font-semibold text-fg mb-3">
        {title} <span className="font-normal text-fg-muted">({count})</span>
      </h2>
      <div className="flex flex-col gap-2">{children}</div>
    </section>
  )
}
