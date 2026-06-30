import { useEffect, useState } from 'react'
import { DateField } from '../../components/cases/DateField'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { casesApi } from '../../api/cases'
import { clientsApi } from '../../api/clients'
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, type Page } from '../../api/pagination'
import type { CaseResponse, CaseStatus, ClientResponse } from '../../types'
import { Navbar } from '../../components/layout/Navbar'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'
import { CaseStatusBadge, CASE_STATUS_CONFIG, CASE_STATUS_ORDER } from '../../components/ui/Badge'
import { CaseStatusSelect } from '../../components/cases/CaseStatusSelect'

type ViewMode = 'list' | 'board'

export default function CasesPage(): JSX.Element {
  const [showForm, setShowForm] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [clientId, setClientId] = useState('')
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
  const queryClient = useQueryClient()

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 300)
    return () => clearTimeout(timer)
  }, [search])

  const serverStatus = view === 'list' && statusFilter !== 'ALL' ? statusFilter : undefined

  useEffect(() => {
    setPage(0)
  }, [view, serverStatus, debouncedSearch])

  const isBoard = view === 'board'
  const { data: casesPage, isLoading } = useQuery<Page<CaseResponse>>({
    queryKey: ['cases', view, serverStatus ?? 'all', debouncedSearch, page],
    queryFn: () =>
      casesApi.list(serverStatus, debouncedSearch || undefined, isBoard ? 0 : page, isBoard ? MAX_PAGE_SIZE : DEFAULT_PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
  const cases = casesPage?.items ?? []
  const total = casesPage?.total ?? 0

  const { data: clients = [] } = useQuery<ClientResponse[]>({
    queryKey: ['clients'],
    queryFn: clientsApi.getAll,
  })

  const createMutation = useMutation({
    mutationFn: casesApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setShowForm(false)
      setTitle('')
      setDescription('')
      setClientId('')
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
      filingDeadline: filingDeadline || undefined,
      nextHearingDate: nextHearingDate || undefined,
      expiresAt: expiresAt || undefined,
      arbitrCaseNumber: arbitrCaseNumber.trim() || undefined,
    })
  }

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
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

        {view === 'list' && (
          <div className="flex flex-wrap gap-2 mb-6">
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
                className="h-full p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 transition-colors flex flex-col"
              >
                <Link to={`/cases/${caseItem.id}`} className="flex-1">
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h3 className="font-medium text-light-text dark:text-dark-text line-clamp-2 min-w-0 [overflow-wrap:anywhere]">{caseItem.title}</h3>
                    <CaseStatusBadge status={caseItem.status} />
                  </div>
                  {caseItem.clientName && (
                    <p className="text-xs text-light-accent dark:text-dark-accent mb-2 truncate">{caseItem.clientName}</p>
                  )}
                  {caseItem.description && (
                    <p className="text-sm text-light-secondary dark:text-dark-secondary line-clamp-2 mb-3">
                      {caseItem.description}
                    </p>
                  )}
                </Link>
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
