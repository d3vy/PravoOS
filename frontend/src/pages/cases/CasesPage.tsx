import { useEffect, useState } from 'react'
import { DateField } from '../../components/cases/DateField'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { casesApi } from '../../api/cases'
import { clientsApi } from '../../api/clients'
import { organizationsApi } from '../../api/organizations'
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, type Page } from '../../api/pagination'
import type { CaseResponse, CaseStatus, ClientResponse, Organization } from '../../types'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'
import { CaseStatusBadge, CASE_STATUS_CONFIG, CASE_STATUS_ORDER } from '../../components/ui/Badge'
import { CaseStatusSelect } from '../../components/cases/CaseStatusSelect'

type ViewMode = 'list' | 'board'

type StatusFilter = CaseStatus | 'ALL'

interface SavedCaseView {
  id: string
  name: string
  status: StatusFilter
  search: string
  orgFilter: string
}

const SAVED_VIEWS_KEY = 'pravoos.cases.views'

function loadSavedViews(): SavedCaseView[] {
  try {
    const raw = localStorage.getItem(SAVED_VIEWS_KEY)
    return raw ? (JSON.parse(raw) as SavedCaseView[]) : []
  } catch {
    return []
  }
}

function persistSavedViews(views: SavedCaseView[]): void {
  localStorage.setItem(SAVED_VIEWS_KEY, JSON.stringify(views))
}

export default function CasesPage(): JSX.Element {
  const [showForm, setShowForm] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [clientId, setClientId] = useState('')
  const [orgId, setOrgId] = useState('')
  const [orgFilter, setOrgFilter] = useState('')
  const [filingDeadline, setFilingDeadline] = useState('')
  const [nextHearingDate, setNextHearingDate] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [arbitrCaseNumber, setArbitrCaseNumber] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [view, setView] = useState<ViewMode>('list')
  const [statusFilter, setStatusFilter] = useState<CaseStatus | 'ALL'>('ALL')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [page, setPage] = useState(0)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [savedViews, setSavedViews] = useState<SavedCaseView[]>(loadSavedViews)
  const [namingView, setNamingView] = useState(false)
  const [viewName, setViewName] = useState('')
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 300)
    return () => clearTimeout(timer)
  }, [search])

  const serverStatus = view === 'list' && statusFilter !== 'ALL' ? statusFilter : undefined

  useEffect(() => {
    setPage(0)
  }, [view, serverStatus, debouncedSearch, orgFilter])

  useEffect(() => {
    setSelectedIds(new Set())
  }, [view, serverStatus, debouncedSearch, orgFilter, page])

  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setShowForm(true)
      searchParams.delete('new')
      setSearchParams(searchParams, { replace: true })
    }
  }, [searchParams, setSearchParams])

  const isBoard = view === 'board'
  const { data: casesPage, isLoading } = useQuery<Page<CaseResponse>>({
    queryKey: ['cases', view, serverStatus ?? 'all', debouncedSearch, orgFilter || 'all-orgs', page],
    queryFn: () =>
      casesApi.list(
        serverStatus,
        debouncedSearch || undefined,
        isBoard ? 0 : page,
        isBoard ? MAX_PAGE_SIZE : DEFAULT_PAGE_SIZE,
        orgFilter || undefined
      ),
    placeholderData: keepPreviousData,
  })
  const cases = casesPage?.items ?? []
  const total = casesPage?.total ?? 0

  const { data: clients = [] } = useQuery<ClientResponse[]>({
    queryKey: ['clients'],
    queryFn: clientsApi.getAll,
  })

  const { data: organizations = [] } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: organizationsApi.list,
  })
  const orgNameById = new Map(organizations.map((org) => [org.id, org.name]))

  const createMutation = useMutation({
    mutationFn: casesApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setShowForm(false)
      setTitle('')
      setDescription('')
      setClientId('')
      setOrgId('')
      setFilingDeadline('')
      setNextHearingDate('')
      setExpiresAt('')
      setArbitrCaseNumber('')
      setFormError(null)
    },
    onError: () => setFormError('Не удалось создать дело. Попробуйте снова.'),
  })

  const statusMutation = useMutation({
    mutationFn: ({ caseId, status }: { caseId: string; status: CaseStatus }) =>
      casesApi.updateStatus(caseId, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cases'] }),
  })

  const bulkStatusMutation = useMutation({
    mutationFn: async ({ ids, status }: { ids: string[]; status: CaseStatus }) => {
      await Promise.all(ids.map((id) => casesApi.updateStatus(id, status)))
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setSelectedIds(new Set())
    },
  })

  const toggleSelected = (caseId: string): void => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(caseId)) next.delete(caseId)
      else next.add(caseId)
      return next
    })
  }

  const allOnPageSelected = cases.length > 0 && cases.every((c) => selectedIds.has(c.id))

  const toggleSelectAll = (): void => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (allOnPageSelected) cases.forEach((c) => next.delete(c.id))
      else cases.forEach((c) => next.add(c.id))
      return next
    })
  }

  const activeView = savedViews.find(
    (v) => v.status === statusFilter && v.search === debouncedSearch && v.orgFilter === orgFilter
  )

  const applyView = (savedView: SavedCaseView): void => {
    setView('list')
    setStatusFilter(savedView.status)
    setSearch(savedView.search)
    setOrgFilter(savedView.orgFilter)
  }

  const saveCurrentView = (): void => {
    const name = viewName.trim()
    if (!name) return
    const next = [
      ...savedViews,
      { id: crypto.randomUUID(), name, status: statusFilter, search: debouncedSearch, orgFilter },
    ]
    setSavedViews(next)
    persistSavedViews(next)
    setNamingView(false)
    setViewName('')
  }

  const deleteView = (id: string): void => {
    const next = savedViews.filter((v) => v.id !== id)
    setSavedViews(next)
    persistSavedViews(next)
  }

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    if (!title.trim()) {
      setFormError('Укажите название дела')
      return
    }
    createMutation.mutate({
      title: title.trim(),
      description: description.trim() || undefined,
      clientId: clientId || undefined,
      orgId: orgId || undefined,
      filingDeadline: filingDeadline || undefined,
      nextHearingDate: nextHearingDate || undefined,
      expiresAt: expiresAt || undefined,
      arbitrCaseNumber: arbitrCaseNumber.trim() || undefined,
    })
  }

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8">
        <div className="flex items-center justify-between mb-6 gap-4 flex-wrap">
          <div>
            <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Дела</h1>
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              Банкротные дела: статус, документы, AI-анализ и история заключений
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="flex p-1 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
              {(['list', 'board'] as ViewMode[]).map((mode) => (
                <button
                  key={mode}
                  type="button"
                  onClick={() => setView(mode)}
                  className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                    view === mode
                      ? 'bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text shadow-sm'
                      : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
                  }`}
                >
                  {mode === 'list' ? 'Список' : 'Доска'}
                </button>
              ))}
            </div>
            <Button variant="primary" onClick={() => setShowForm((v) => !v)}>
              {showForm ? 'Отмена' : 'Новое дело'}
            </Button>
          </div>
        </div>

        <AnimatePresence>
          {showForm && (
            <motion.form
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              onSubmit={handleSubmit}
              className="mb-8 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border overflow-hidden"
            >
              <div className="flex flex-col gap-4">
                <Input
                  label="Название дела"
                  placeholder="Например: Банкротство ООО «Рассвет», дело № А40-..."
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={500}
                />
                <div>
                  <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">
                    Описание <span className="text-light-secondary dark:text-dark-secondary font-normal">(опционально)</span>
                  </label>
                  <textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows={3}
                    maxLength={5000}
                    placeholder="Краткое описание дела, ключевые обстоятельства"
                    className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-none"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">
                    Клиент <span className="text-light-secondary dark:text-dark-secondary font-normal">(опционально)</span>
                  </label>
                  <select
                    value={clientId}
                    onChange={(e) => setClientId(e.target.value)}
                    className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
                  >
                    <option value="">Без клиента</option>
                    {clients.map((client) => (
                      <option key={client.id} value={client.id}>
                        {client.name} ({client.typeName})
                      </option>
                    ))}
                  </select>
                </div>
                {organizations.length > 0 && (
                  <div>
                    <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">
                      Организация <span className="text-light-secondary dark:text-dark-secondary font-normal">(опционально)</span>
                    </label>
                    <select
                      value={orgId}
                      onChange={(e) => setOrgId(e.target.value)}
                      className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
                    >
                      <option value="">Личное дело</option>
                      {organizations.map((org) => (
                        <option key={org.id} value={org.id}>
                          {org.name}
                        </option>
                      ))}
                    </select>
                    <p className="mt-1 text-xs text-light-secondary dark:text-dark-secondary">
                      Дело организации видят все её участники. Личное дело — только вы.
                    </p>
                  </div>
                )}
                <div className="grid gap-4 sm:grid-cols-3">
                  <DateField label="Срок подачи" value={filingDeadline} onChange={setFilingDeadline} />
                  <DateField label="Заседание" value={nextHearingDate} onChange={setNextHearingDate} />
                  <DateField label="Истечение срока" value={expiresAt} onChange={setExpiresAt} />
                </div>
                <Input
                  label="Номер дела в КАД.Арбитр (опционально)"
                  value={arbitrCaseNumber}
                  onChange={(e) => setArbitrCaseNumber(e.target.value)}
                  maxLength={50}
                  placeholder="А40-12345/2024"
                />
                {formError && <p className="text-sm text-red-600 dark:text-red-400">{formError}</p>}
                <div>
                  <Button type="submit" variant="primary" loading={createMutation.isPending}>
                    Создать дело
                  </Button>
                </div>
              </div>
            </motion.form>
          )}
        </AnimatePresence>

        <div className="mb-4">
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Поиск по названию, описанию или клиенту…"
            className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
          />
        </div>

        {organizations.length > 0 && (
          <div className="mb-4">
            <select
              value={orgFilter}
              onChange={(e) => setOrgFilter(e.target.value)}
              className="w-full sm:w-72 px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
            >
              <option value="">Все дела</option>
              {organizations.map((org) => (
                <option key={org.id} value={org.id}>
                  {org.name}
                </option>
              ))}
            </select>
          </div>
        )}

        {view === 'list' && (
          <div className="flex flex-wrap items-center gap-2 mb-4">
            {savedViews.map((savedView) => (
              <span
                key={savedView.id}
                className={`inline-flex items-center rounded-full text-xs font-medium border transition-colors ${
                  activeView?.id === savedView.id
                    ? 'bg-light-text dark:bg-dark-text text-light-bg dark:text-dark-bg border-transparent'
                    : 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
                }`}
              >
                <button type="button" onClick={() => applyView(savedView)} className="pl-3 pr-1.5 py-1.5">
                  {savedView.name}
                </button>
                <button
                  type="button"
                  onClick={() => deleteView(savedView.id)}
                  aria-label={`Удалить вид «${savedView.name}»`}
                  className="pr-2.5 pl-0.5 py-1.5 opacity-60 hover:opacity-100"
                >
                  ×
                </button>
              </span>
            ))}
            {namingView ? (
              <span className="inline-flex items-center gap-1.5">
                <input
                  autoFocus
                  value={viewName}
                  onChange={(e) => setViewName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') saveCurrentView()
                    if (e.key === 'Escape') {
                      setNamingView(false)
                      setViewName('')
                    }
                  }}
                  maxLength={40}
                  placeholder="Название вида"
                  className="px-3 py-1.5 rounded-full text-xs border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
                />
                <button
                  type="button"
                  onClick={saveCurrentView}
                  className="px-3 py-1.5 rounded-full text-xs font-medium bg-light-text dark:bg-dark-text text-light-bg dark:text-dark-bg"
                >
                  Сохранить
                </button>
              </span>
            ) : (
              <button
                type="button"
                onClick={() => setNamingView(true)}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium border border-dashed border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text transition-colors"
              >
                + Сохранить вид
              </button>
            )}
          </div>
        )}

        {view === 'list' && (
          <div className="flex flex-wrap gap-2 mb-4">
            <FilterChip label="Все" active={statusFilter === 'ALL'} onClick={() => setStatusFilter('ALL')} />
            {CASE_STATUS_ORDER.map((status) => (
              <FilterChip
                key={status}
                label={CASE_STATUS_CONFIG[status].label}
                active={statusFilter === status}
                onClick={() => setStatusFilter(status)}
              />
            ))}
          </div>
        )}

        {view === 'list' && cases.length > 0 && (
          <div className="mb-4">
            <label className="inline-flex items-center gap-2 text-xs text-light-secondary dark:text-dark-secondary cursor-pointer select-none">
              <input
                type="checkbox"
                checked={allOnPageSelected}
                onChange={toggleSelectAll}
                className="h-4 w-4 rounded accent-light-accent dark:accent-dark-accent cursor-pointer"
              />
              {selectedIds.size > 0 ? `Выбрано: ${selectedIds.size}` : 'Выбрать все на странице'}
            </label>
          </div>
        )}

        {isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner size="lg" />
          </div>
        ) : view === 'board' ? (
          <BoardView
            cases={cases}
            onMove={(caseId, status) => statusMutation.mutate({ caseId, status })}
          />
        ) : cases.length === 0 ? (
          <div className="text-center py-16 rounded-xl border border-dashed border-light-border dark:border-dark-border">
            <p className="text-light-secondary dark:text-dark-secondary text-sm">
              {debouncedSearch
                ? 'Ничего не найдено по запросу.'
                : statusFilter === 'ALL'
                  ? 'Пока нет дел. Создайте первое дело, чтобы загрузить документы и запустить AI-анализ.'
                  : 'Нет дел с этим статусом.'}
            </p>
          </div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {cases.map((caseItem, index) => (
              <motion.div
                key={caseItem.id}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2, delay: index * 0.03 }}
                className={`h-full p-5 rounded-xl bg-light-surface dark:bg-dark-surface border transition-colors flex flex-col ${
                  selectedIds.has(caseItem.id)
                    ? 'border-light-accent dark:border-dark-accent ring-1 ring-light-accent/40 dark:ring-dark-accent/40'
                    : 'border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50'
                }`}
              >
                <div className="flex items-start gap-3 flex-1 min-w-0">
                  <input
                    type="checkbox"
                    checked={selectedIds.has(caseItem.id)}
                    onChange={() => toggleSelected(caseItem.id)}
                    aria-label={`Выбрать дело «${caseItem.title}»`}
                    className="mt-1 h-4 w-4 shrink-0 rounded accent-light-accent dark:accent-dark-accent cursor-pointer"
                  />
                  <Link to={`/cases/${caseItem.id}`} className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h3 className="font-medium text-light-text dark:text-dark-text line-clamp-2 min-w-0 [overflow-wrap:anywhere]">{caseItem.title}</h3>
                    <CaseStatusBadge status={caseItem.status} />
                  </div>
                  {caseItem.orgId && (
                    <span className="inline-block mb-2 px-2 py-0.5 rounded-full text-[11px] font-medium bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary">
                      {orgNameById.get(caseItem.orgId) ?? 'Организация'}
                    </span>
                  )}
                  {caseItem.clientName && (
                    <p className="text-xs text-light-accent dark:text-dark-accent mb-2 truncate">{caseItem.clientName}</p>
                  )}
                  {caseItem.description && (
                    <p className="text-sm text-light-secondary dark:text-dark-secondary line-clamp-2 mb-3">
                      {caseItem.description}
                    </p>
                  )}
                </Link>
                </div>
                <div className="flex items-center justify-between gap-2 mt-3 pt-3 border-t border-light-border dark:border-dark-border">
                  <CaseStatusSelect
                    value={caseItem.status}
                    disabled={statusMutation.isPending}
                    onChange={(status) => statusMutation.mutate({ caseId: caseItem.id, status })}
                  />
                  <span className="text-xs text-light-secondary dark:text-dark-secondary">
                    {new Date(caseItem.createdAt).toLocaleDateString('ru-RU')}
                  </span>
                </div>
              </motion.div>
            ))}
          </div>
        )}

        {!isBoard && !isLoading && (
          <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={total} onPageChange={setPage} />
        )}

        <AnimatePresence>
          {selectedIds.size > 0 && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 20 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              className="fixed inset-x-0 bottom-6 z-40 flex justify-center px-4 pointer-events-none"
            >
              <div className="pointer-events-auto flex items-center gap-3 rounded-xl border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-surface shadow-card dark:shadow-card-dark px-4 py-2.5">
                <span className="text-sm font-medium text-light-text dark:text-dark-text">
                  Выбрано: {selectedIds.size}
                </span>
                <span className="h-5 w-px bg-light-border dark:bg-dark-border" />
                <select
                  value=""
                  disabled={bulkStatusMutation.isPending}
                  onChange={(e) => {
                    const status = e.target.value as CaseStatus
                    if (status) bulkStatusMutation.mutate({ ids: Array.from(selectedIds), status })
                  }}
                  className="px-3 py-1.5 rounded-lg border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent disabled:opacity-50"
                >
                  <option value="">Сменить статус…</option>
                  {CASE_STATUS_ORDER.map((status) => (
                    <option key={status} value={status}>
                      {CASE_STATUS_CONFIG[status].label}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => setSelectedIds(new Set())}
                  className="text-sm text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text transition-colors"
                >
                  Снять
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

function FilterChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }): JSX.Element {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${
        active
          ? 'bg-light-text dark:bg-dark-text text-light-bg dark:text-dark-bg border-transparent'
          : 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
      }`}
    >
      {label}
    </button>
  )
}

function BoardView({
  cases,
  onMove,
}: {
  cases: CaseResponse[]
  onMove: (caseId: string, status: CaseStatus) => void
}): JSX.Element {
  const [dragOver, setDragOver] = useState<CaseStatus | null>(null)

  const handleDrop = (status: CaseStatus, caseId: string): void => {
    setDragOver(null)
    const moved = cases.find((c) => c.id === caseId)
    if (moved && moved.status !== status) {
      onMove(caseId, status)
    }
  }

  return (
    <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-5">
      {CASE_STATUS_ORDER.map((status) => {
        const columnCases = cases.filter((c) => c.status === status)
        return (
          <div
            key={status}
            onDragOver={(e) => {
              e.preventDefault()
              setDragOver(status)
            }}
            onDragLeave={() => setDragOver((prev) => (prev === status ? null : prev))}
            onDrop={(e) => handleDrop(status, e.dataTransfer.getData('text/plain'))}
            className={`flex flex-col rounded-xl border p-3 min-h-[120px] transition-colors ${
              dragOver === status
                ? 'border-light-accent dark:border-dark-accent bg-light-accent/5 dark:bg-dark-accent/10'
                : 'border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface'
            }`}
          >
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-semibold text-light-text dark:text-dark-text">
                {CASE_STATUS_CONFIG[status].label}
              </span>
              <span className="text-xs text-light-secondary dark:text-dark-secondary">{columnCases.length}</span>
            </div>
            <div className="flex flex-col gap-2">
              {columnCases.map((caseItem) => (
                <div
                  key={caseItem.id}
                  draggable
                  onDragStart={(e) => e.dataTransfer.setData('text/plain', caseItem.id)}
                  className="rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg p-3 cursor-grab active:cursor-grabbing"
                >
                  <Link to={`/cases/${caseItem.id}`} className="block">
                    <p className="text-sm font-medium text-light-text dark:text-dark-text line-clamp-2">
                      {caseItem.title}
                    </p>
                    {caseItem.clientName && (
                      <p className="text-xs text-light-accent dark:text-dark-accent mt-1 truncate">
                        {caseItem.clientName}
                      </p>
                    )}
                  </Link>
                </div>
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}
