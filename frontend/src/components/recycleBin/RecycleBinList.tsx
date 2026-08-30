import { useState } from 'react'
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import type {
  DeletionRole,
  RecycleBinArea,
  RecycleBinEntry,
  RecycleBinFilterParams,
} from '../../types'
import type { Page } from '../../api/pagination'
import { DEFAULT_PAGE_SIZE } from '../../api/pagination'
import { PageHeader } from '../ui/PageHeader'
import { Pagination } from '../ui/Pagination'
import { Spinner } from '../ui/Spinner'
import { EmptyState } from '../ui/EmptyState'
import { Button } from '../ui/Button'
import { useConfirmStore } from '../../store/confirmStore'
import { useToastStore } from '../../store/toastStore'

const AREAS: RecycleBinArea[] = [
  'CASES',
  'CLIENTS',
  'DOCUMENTS',
  'INVOICES',
  'CHAT',
  'TEMPLATES',
  'WORKFLOWS',
  'MAILBOXES',
  'VIEWS',
  'REVIEW',
  'TIME',
]
const ROLES: DeletionRole[] = ['LAWYER', 'CLIENT', 'ADMIN', 'SYSTEM']

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

export interface RecycleBinListProps {
  queryKey: string
  list: (
    filter: RecycleBinFilterParams & { orgId?: string },
    page: number
  ) => Promise<Page<RecycleBinEntry>>
  items?: (entryId: string) => Promise<RecycleBinEntry[]>
  restore: (entryId: string) => Promise<void>
  purge?: (entryId: string) => Promise<void>
  showOrgFilter?: boolean
  title: string
  description: string
}

export function RecycleBinList({
  queryKey,
  list,
  items,
  restore,
  purge,
  showOrgFilter = false,
  title,
  description,
}: RecycleBinListProps): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const pushToast = useToastStore((state) => state.push)

  const [area, setArea] = useState<RecycleBinArea | ''>('')
  const [deletedByRole, setDeletedByRole] = useState<DeletionRole | ''>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [q, setQ] = useState('')
  const [orgId, setOrgId] = useState('')
  const [page, setPage] = useState(0)
  const [expandedId, setExpandedId] = useState<string | null>(null)

  const filter: RecycleBinFilterParams & { orgId?: string } = {
    ...(area ? { area } : {}),
    ...(deletedByRole ? { deletedByRole } : {}),
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
    ...(q.trim() ? { q: q.trim() } : {}),
    ...(orgId.trim() ? { orgId: orgId.trim() } : {}),
  }

  const resetPageAnd = (apply: () => void): void => {
    apply()
    setPage(0)
    setExpandedId(null)
  }

  const { data, isLoading } = useQuery<Page<RecycleBinEntry>>({
    queryKey: [queryKey, filter, page],
    queryFn: () => list(filter, page),
    placeholderData: keepPreviousData,
  })
  const entries = data?.items ?? []
  const total = data?.total ?? 0

  const { data: nestedItems, isLoading: nestedLoading } = useQuery<RecycleBinEntry[]>({
    queryKey: [queryKey, 'items', expandedId],
    queryFn: () => (items ? items(expandedId as string) : Promise.resolve([])),
    enabled: expandedId !== null && items !== undefined,
  })

  const invalidate = (): void => {
    void queryClient.invalidateQueries({ queryKey: [queryKey] })
  }

  const restoreMutation = useMutation({
    mutationFn: restore,
    onSuccess: () => {
      invalidate()
      pushToast({ variant: 'success', message: t('recycleBin.restored') })
    },
    onError: () => {
      pushToast({ variant: 'error', message: t('recycleBin.restoreFailed') })
    },
  })

  const purgeMutation = useMutation({
    mutationFn: (entryId: string) => (purge ? purge(entryId) : Promise.resolve()),
    onSuccess: () => {
      invalidate()
      setExpandedId(null)
    },
  })

  const handleRestore = async (entry: RecycleBinEntry): Promise<void> => {
    const confirmed = await useConfirmStore.getState().ask({
      title: t('recycleBin.restoreConfirmTitle'),
      description: t('recycleBin.restoreConfirmDescription', { title: entry.title }),
      confirmLabel: t('recycleBin.restore'),
    })
    if (!confirmed) return
    restoreMutation.mutate(entry.id)
  }

  const handlePurge = async (entry: RecycleBinEntry): Promise<void> => {
    const confirmed = await useConfirmStore.getState().ask({
      title: t('admin.recycleBinPurgeConfirmTitle'),
      description: t('admin.recycleBinPurgeConfirmDescription', { title: entry.title }),
      confirmLabel: t('admin.recycleBinPurge'),
      danger: true,
    })
    if (!confirmed) return
    purgeMutation.mutate(entry.id)
  }

  const purgeLabel = (entry: RecycleBinEntry): string =>
    entry.daysUntilPurge <= 0
      ? t('recycleBin.purgeToday')
      : t('recycleBin.purgeIn', { count: entry.daysUntilPurge })

  const inputClass =
    'px-3 py-2 text-sm rounded-lg border border-line bg-surface text-fg placeholder-fg-muted focus:outline-none focus:ring-1 focus:ring-accent'

  return (
    <div className="space-y-6">
      <PageHeader title={title} description={description} />

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => resetPageAnd(() => setArea(''))}
          className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${
            area === '' ? 'border-accent bg-accent/10 text-accent' : 'border-line text-fg-muted hover:text-fg'
          }`}
        >
          {t('common.all')}
        </button>
        {AREAS.map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => resetPageAnd(() => setArea(value))}
            className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${
              area === value ? 'border-accent bg-accent/10 text-accent' : 'border-line text-fg-muted hover:text-fg'
            }`}
          >
            {t(`recycleBin.area.${value}`)}
          </button>
        ))}
      </div>

      <div className={`grid gap-3 ${showOrgFilter ? 'sm:grid-cols-3 lg:grid-cols-5' : 'sm:grid-cols-2 lg:grid-cols-4'}`}>
        <select
          value={deletedByRole}
          onChange={(event) =>
            resetPageAnd(() => setDeletedByRole(event.target.value as DeletionRole | ''))
          }
          className={inputClass}
        >
          <option value="">{t('recycleBin.filters.deletedByRole')}</option>
          {ROLES.map((value) => (
            <option key={value} value={value}>
              {t(`recycleBin.role.${value}`)}
            </option>
          ))}
        </select>
        <input
          type="date"
          value={from}
          onChange={(event) => resetPageAnd(() => setFrom(event.target.value))}
          aria-label={t('recycleBin.filters.from')}
          className={inputClass}
        />
        <input
          type="date"
          value={to}
          onChange={(event) => resetPageAnd(() => setTo(event.target.value))}
          aria-label={t('recycleBin.filters.to')}
          className={inputClass}
        />
        <input
          type="search"
          value={q}
          onChange={(event) => resetPageAnd(() => setQ(event.target.value))}
          placeholder={t('recycleBin.filters.search')}
          className={inputClass}
        />
        {showOrgFilter && (
          <input
            type="text"
            value={orgId}
            onChange={(event) => resetPageAnd(() => setOrgId(event.target.value))}
            placeholder="Org ID"
            className={inputClass}
          />
        )}
      </div>

      <div className="rounded-xl border border-line bg-surface overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-12">
            <Spinner />
          </div>
        ) : entries.length === 0 ? (
          <EmptyState description={t('recycleBin.empty')} illustration="none" />
        ) : (
          <ul className="divide-y divide-line">
            {entries.map((entry) => {
              const expanded = expandedId === entry.id
              const canExpand = items !== undefined && entry.cascadeRoot && entry.nestedCount > 0
              return (
                <li key={entry.id} className="px-4 py-3">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-fg">{entry.title}</p>
                      <p className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-fg-muted">
                        <span>{t(`recycleBin.area.${entry.area}`)}</span>
                        <span>{t(`recycleBin.role.${entry.deletedByRole}`)}</span>
                        <span>
                          {t('recycleBin.deletedAt')}: {formatMoment(entry.deletedAt)}
                        </span>
                        <span>{purgeLabel(entry)}</span>
                        {!canExpand && entry.cascadeRoot && entry.nestedCount > 0 && (
                          <span>{t('recycleBin.nestedItems', { count: entry.nestedCount })}</span>
                        )}
                      </p>
                    </div>
                    <div className="flex shrink-0 flex-wrap items-center gap-2">
                      {canExpand && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setExpandedId(expanded ? null : entry.id)}
                        >
                          {expanded
                            ? t('recycleBin.hideItems')
                            : t('recycleBin.nestedItems', { count: entry.nestedCount })}
                        </Button>
                      )}
                      <Button
                        variant="secondary"
                        size="sm"
                        loading={restoreMutation.isPending && restoreMutation.variables === entry.id}
                        onClick={() => void handleRestore(entry)}
                      >
                        {t('recycleBin.restore')}
                      </Button>
                      {purge && (
                        <Button
                          variant="danger"
                          size="sm"
                          loading={purgeMutation.isPending && purgeMutation.variables === entry.id}
                          onClick={() => void handlePurge(entry)}
                        >
                          {t('admin.recycleBinPurge')}
                        </Button>
                      )}
                    </div>
                  </div>

                  {expanded && (
                    <div className="mt-3 rounded-lg border border-line bg-bg p-3">
                      {nestedLoading ? (
                        <div className="flex justify-center py-4">
                          <Spinner size="sm" />
                        </div>
                      ) : (
                        <ul className="space-y-1.5">
                          {(nestedItems ?? []).map((nested) => (
                            <li key={nested.id} className="text-xs text-fg-muted">
                              <span className="text-fg">{nested.title}</span>{' '}
                              <span>({t(`recycleBin.area.${nested.area}`)})</span>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </div>

      <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={total} onPageChange={setPage} />
    </div>
  )
}
