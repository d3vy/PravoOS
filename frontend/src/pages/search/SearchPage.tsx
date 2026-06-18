import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { searchApi } from '../../api/search'
import type { GlobalSearchResponse } from '../../types'
import { Navbar } from '../../components/layout/Navbar'
import { Spinner } from '../../components/ui/Spinner'
import { CaseStatusBadge } from '../../components/ui/Badge'

const MIN_QUERY_LENGTH = 2

export default function SearchPage(): JSX.Element {
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState(searchParams.get('q') ?? '')
  const [debouncedSearch, setDebouncedSearch] = useState(search.trim())

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
    queryKey: ['global-search', debouncedSearch],
    queryFn: () => searchApi.global(debouncedSearch),
    enabled,
  })

  const totalHits = data ? data.cases.length + data.conversations.length + data.documents.length : 0

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
      <div className="page-container py-8 max-w-3xl">
        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Поиск</h1>
        <p className="text-sm text-light-secondary dark:text-dark-secondary mb-6">
          Дела, беседы с AI и документы
        </p>

        <input
          type="search"
          autoFocus
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Что ищем?"
          className="w-full px-4 py-3 rounded-xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
        />

        <div className="mt-6">
          {!enabled ? (
            <p className="text-sm text-light-secondary dark:text-dark-secondary text-center py-12">
              Введите минимум {MIN_QUERY_LENGTH} символа для поиска.
            </p>
          ) : isFetching ? (
            <div className="flex justify-center py-12">
              <Spinner size="lg" />
            </div>
          ) : totalHits === 0 ? (
            <p className="text-sm text-light-secondary dark:text-dark-secondary text-center py-12">
              Ничего не найдено по запросу «{debouncedSearch}».
            </p>
          ) : (
            <div className="flex flex-col gap-8">
              {data!.cases.length > 0 && (
                <ResultGroup title="Дела" count={data!.cases.length}>
                  {data!.cases.map((hit) => (
                    <Link
                      key={hit.id}
                      to={`/cases/${hit.id}`}
                      className="flex items-center gap-3 p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 transition-colors"
                    >
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-light-text dark:text-dark-text truncate">{hit.title}</p>
                        {hit.clientName && (
                          <p className="text-xs text-light-accent dark:text-dark-accent truncate">{hit.clientName}</p>
                        )}
                      </div>
                      <CaseStatusBadge status={hit.status} />
                    </Link>
                  ))}
                </ResultGroup>
              )}

              {data!.conversations.length > 0 && (
                <ResultGroup title="Беседы" count={data!.conversations.length}>
                  {data!.conversations.map((hit) => (
                    <Link
                      key={hit.id}
                      to={`/chat?conversation=${hit.id}`}
                      className="block p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 transition-colors"
                    >
                      <p className="text-sm text-light-text dark:text-dark-text truncate">{hit.title}</p>
                    </Link>
                  ))}
                </ResultGroup>
              )}

              {data!.documents.length > 0 && (
                <ResultGroup title="Документы" count={data!.documents.length}>
                  {data!.documents.map((hit) => (
                    <Link
                      key={hit.id}
                      to={hit.caseId ? `/cases/${hit.caseId}` : '#'}
                      className="flex items-center gap-3 p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 transition-colors"
                    >
                      <span className="text-xs font-bold uppercase text-light-secondary dark:text-dark-secondary w-9 shrink-0">
                        {hit.fileName.split('.').pop()}
                      </span>
                      <p className="flex-1 min-w-0 text-sm text-light-text dark:text-dark-text truncate">{hit.title}</p>
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
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">
        {title} <span className="font-normal text-light-secondary dark:text-dark-secondary">({count})</span>
      </h2>
      <div className="flex flex-col gap-2">{children}</div>
    </section>
  )
}
