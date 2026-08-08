import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { DateField } from '../../components/cases/DateField'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { casesApi } from '../../api/cases'
import { clientsApi } from '../../api/clients'
import { organizationsApi } from '../../api/organizations'
import { savedViewsApi } from '../../api/savedViews'
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, type Page } from '../../api/pagination'
import type { CaseResponse, CaseStatus, ClientResponse, Organization, OrganizationMember } from '../../types'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { SkeletonCardGrid } from '../../components/ui/Skeleton'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { Modal } from '../../components/ui/Modal'
import { DataTable, type DataTableColumn, type SortRule } from '../../components/ui/DataTable'
import { TableToolbar } from '../../components/ui/TableToolbar'
import { SavedViewBar } from '../../components/ui/SavedViewBar'
import { CaseStatusBadge, caseStatusLabel, CASE_STATUS_ORDER } from '../../components/ui/Badge'
import { CaseStatusSelect } from '../../components/cases/CaseStatusSelect'
import { useToast } from '../../hooks/useToast'
import { useDensity } from '../../hooks/useDensity'
import { useSavedViews, type SavedView } from '../../hooks/useSavedViews'
import { useTablePreferences } from '../../hooks/useTablePreferences'
import { PageHeader } from '../../components/ui/PageHeader'

type ViewMode = 'table' | 'cards' | 'board'

type StatusFilter = CaseStatus | 'ALL'

interface CasesViewConfig {
  view: ViewMode
  status: StatusFilter
  search: string
  orgFilter: string
  sort: SortRule[]
  groupBy: string | null
  visibleColumnIds: string[]
}

interface LegacySavedCaseView {
  id: string
  name: string
  status: StatusFilter
  search: string
  orgFilter: string
}

const LEGACY_VIEWS_KEY = 'pravoos.cases.views'
const TABLE_KEY = 'cases'
const DEFAULT_COLUMN_IDS = ['title', 'client', 'status', 'filingDeadline', 'createdAt']

function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')
}

export default function CasesPage(): JSX.Element {
  const { t } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const [showForm, setShowForm] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [clientId, setClientId] = useState('')
  const [orgId, setOrgId] = useState('')
  const [orgFilter, setOrgFilter] = useState('')
  const [filingDeadline, setFilingDeadline] = useState('')
  const [nextHearingDate, setNextHearingDate] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [courtCaseNumber, setCourtCaseNumber] = useState('')
  const [defaultHourlyRate, setDefaultHourlyRate] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [view, setView] = useState<ViewMode>('table')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [page, setPage] = useState(0)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [activeViewId, setActiveViewId] = useState<string | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [density, toggleDensity] = useDensity()
  const { preferences, setSort, setGroupBy, toggleColumn, applyPreferences } = useTablePreferences(TABLE_KEY, {
    visibleColumnIds: DEFAULT_COLUMN_IDS,
    sort: [],
    groupBy: null,
  })
  const savedViews = useSavedViews<CasesViewConfig>('CASES')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 300)
    return () => clearTimeout(timer)
  }, [search])

  const isBoard = view === 'board'
  const isTable = view === 'table'
  const pageSize = isTable ? MAX_PAGE_SIZE : DEFAULT_PAGE_SIZE
  const serverStatus = !isBoard && statusFilter !== 'ALL' ? statusFilter : undefined

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

  const { data: casesPage, isLoading } = useQuery<Page<CaseResponse>>({
    queryKey: ['cases', view, serverStatus ?? 'all', debouncedSearch, orgFilter || 'all-orgs', page, pageSize],
    queryFn: () =>
      casesApi.list(
        serverStatus,
        debouncedSearch || undefined,
        isBoard ? 0 : page,
        isBoard ? MAX_PAGE_SIZE : pageSize,
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
  const orgNameById = useMemo(
    () => new Map(organizations.map((org) => [org.id, org.name])),
    [organizations]
  )

  const { data: orgFilterMembers = [] } = useQuery<OrganizationMember[]>({
    queryKey: ['org-members', orgFilter],
    queryFn: () => organizationsApi.members(orgFilter),
    enabled: Boolean(orgFilter),
  })

  useEffect(() => {
    const raw = localStorage.getItem(LEGACY_VIEWS_KEY)
    if (!raw) return
    localStorage.removeItem(LEGACY_VIEWS_KEY)
    let legacyViews: LegacySavedCaseView[] = []
    try {
      legacyViews = JSON.parse(raw) as LegacySavedCaseView[]
    } catch {
      return
    }
    legacyViews.forEach((legacy) => {
      savedViewsApi
        .create({
          scope: 'CASES',
          name: legacy.name.slice(0, 80),
          config: JSON.stringify({
            view: 'table',
            status: legacy.status,
            search: legacy.search,
            orgFilter: legacy.orgFilter,
            sort: [],
            groupBy: null,
            visibleColumnIds: DEFAULT_COLUMN_IDS,
          } satisfies CasesViewConfig),
          sharedWithTeam: false,
        })
        .catch(() => undefined)
        .finally(() => queryClient.invalidateQueries({ queryKey: ['saved-views', 'CASES'] }))
    })
  }, [queryClient])

  const createMutation = useMutation({
    mutationFn: casesApi.create,
    onSuccess: (createdCase) => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setShowForm(false)
      setTitle('')
      setDescription('')
      setClientId('')
      setOrgId('')
      setFilingDeadline('')
      setNextHearingDate('')
      setExpiresAt('')
      setCourtCaseNumber('')
      setDefaultHourlyRate('')
      setFormError(null)
      toast.success(t('cases.createSuccess', { title: createdCase.title }))
    },
    onError: () => setFormError(t('cases.createError')),
    meta: { suppressErrorToast: true },
  })

  const statusMutation = useMutation({
    mutationFn: ({ caseId, status }: { caseId: string; status: CaseStatus }) =>
      casesApi.updateStatus(caseId, status),
    onMutate: async ({ caseId, status }) => {
      await queryClient.cancelQueries({ queryKey: ['cases'] })
      const previous = queryClient.getQueriesData<Page<CaseResponse>>({ queryKey: ['cases'] })
      queryClient.setQueriesData<Page<CaseResponse>>({ queryKey: ['cases'] }, (old) =>
        old
          ? { ...old, items: old.items.map((c) => (c.id === caseId ? { ...c, status } : c)) }
          : old
      )
      return { previous }
    },
    onError: (_err, _vars, context) => {
      context?.previous.forEach(([key, data]) => queryClient.setQueryData(key, data))
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['cases'] }),
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

  const bulkChangeOrgMutation = useMutation({
    mutationFn: async ({ ids, newOrgId }: { ids: string[]; newOrgId: string | null }) => {
      const results = await Promise.allSettled(ids.map((id) => casesApi.changeOrg(id, newOrgId)))
      const failed = results.filter((result) => result.status === 'rejected').length
      if (failed > 0) throw new Error('bulk-change-org-partial-failure')
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setSelectedIds(new Set())
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      toast.error(t('cases.bulkChangeOrgError'))
    },
  })

  const bulkTransferOwnerMutation = useMutation({
    mutationFn: async ({ ids, newOwnerId }: { ids: string[]; newOwnerId: string }) => {
      const results = await Promise.allSettled(ids.map((id) => casesApi.transferOwner(id, newOwnerId)))
      const failed = results.filter((result) => result.status === 'rejected').length
      if (failed > 0) throw new Error('bulk-transfer-owner-partial-failure')
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setSelectedIds(new Set())
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      toast.error(t('cases.bulkTransferOwnerError'))
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

  const columns = useMemo<DataTableColumn<CaseResponse>[]>(
    () => [
      {
        id: 'title',
        header: t('cases.columnTitle'),
        alwaysVisible: true,
        sortable: true,
        width: 'minmax(0, 2.2fr)',
        value: (row) => row.title,
      },
      {
        id: 'client',
        header: t('cases.columnClient'),
        sortable: true,
        groupable: true,
        value: (row) => row.clientName,
        groupLabel: (row) => row.clientName ?? t('cases.noClient'),
      },
      {
        id: 'status',
        header: t('cases.columnStatus'),
        sortable: true,
        groupable: true,
        width: '11rem',
        value: (row) => CASE_STATUS_ORDER.indexOf(row.status),
        groupLabel: (row) => caseStatusLabel(row.status),
        render: (row) => (
          <CaseStatusSelect
            value={row.status}
            disabled={statusMutation.isPending}
            onChange={(status) => statusMutation.mutate({ caseId: row.id, status })}
          />
        ),
      },
      {
        id: 'org',
        header: t('cases.columnOrg'),
        groupable: true,
        value: (row) => (row.orgId ? orgNameById.get(row.orgId) ?? t('cases.orgFallback') : t('cases.personalCase')),
      },
      {
        id: 'filingDeadline',
        header: t('cases.columnDeadline'),
        sortable: true,
        groupable: true,
        width: '9rem',
        value: (row) => row.filingDeadline,
        render: (row) => formatDate(row.filingDeadline),
        groupLabel: (row) => formatDate(row.filingDeadline),
      },
      {
        id: 'nextHearingDate',
        header: t('cases.columnHearing'),
        sortable: true,
        width: '9rem',
        value: (row) => row.nextHearingDate,
        render: (row) => formatDate(row.nextHearingDate),
      },
      {
        id: 'courtCaseNumber',
        header: t('cases.columnCourtNumber'),
        sortable: true,
        width: '10rem',
        value: (row) => row.courtCaseNumber,
      },
      {
        id: 'createdAt',
        header: t('cases.columnCreated'),
        sortable: true,
        width: '9rem',
        value: (row) => row.createdAt,
        render: (row) => formatDate(row.createdAt),
      },
    ],
    [t, orgNameById, statusMutation]
  )

  const canShareViews = organizations.length > 0
  const shareOrgId = orgFilter || (organizations.length === 1 ? organizations[0].id : null)

  const currentConfig: CasesViewConfig = {
    view,
    status: statusFilter,
    search: debouncedSearch,
    orgFilter,
    sort: preferences.sort,
    groupBy: preferences.groupBy,
    visibleColumnIds: preferences.visibleColumnIds,
  }

  const applySavedView = (savedView: SavedView<CasesViewConfig>): void => {
    const config = savedView.config
    if (!config) return
    setView(config.view ?? 'table')
    setStatusFilter(config.status ?? 'ALL')
    setSearch(config.search ?? '')
    setDebouncedSearch(config.search ?? '')
    setOrgFilter(config.orgFilter ?? '')
    applyPreferences({
      sort: config.sort ?? [],
      groupBy: config.groupBy ?? null,
      visibleColumnIds: config.visibleColumnIds ?? DEFAULT_COLUMN_IDS,
    })
    setActiveViewId(savedView.id)
  }

  const handleSaveView = (name: string, sharedWithTeam: boolean): void => {
    if (sharedWithTeam && !shareOrgId) {
      toast.error(t('savedViews.selectOrgFirst'))
      return
    }
    savedViews.saveView({ name, config: currentConfig, sharedWithTeam, orgId: shareOrgId })
    toast.success(t('savedViews.saved', { name }))
  }

  const handleDeleteView = (viewId: string): void => {
    savedViews.deleteView(viewId)
    setActiveViewId((current) => (current === viewId ? null : current))
  }

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    if (!title.trim()) {
      setFormError(t('cases.titleRequired'))
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
      courtCaseNumber: courtCaseNumber.trim() || undefined,
      defaultHourlyRate: defaultHourlyRate.trim() ? Number(defaultHourlyRate) : undefined,
    })
  }

  const rowActions = (row: CaseResponse): JSX.Element => (
    <>
      <Link
        to={`/cases/${row.id}?tab=time`}
        className="px-2 py-1 rounded-md text-xs font-medium border border-line text-fg-muted hover:text-fg transition-colors"
      >
        {t('cases.quickTime')}
      </Link>
      <Link
        to={`/cases/${row.id}?tab=tasks`}
        className="px-2 py-1 rounded-md text-xs font-medium border border-line text-fg-muted hover:text-fg transition-colors"
      >
        {t('cases.quickTasks')}
      </Link>
      <Link
        to={`/cases/${row.id}`}
        className="px-2 py-1 rounded-md text-xs font-medium border border-line text-fg-muted hover:text-fg transition-colors"
      >
        {t('cases.quickOpen')}
      </Link>
    </>
  )

  return (
    <div className="bg-bg">
      <div className="page-container py-8">
        <PageHeader
          title={t('cases.title')}
          description={t('cases.subtitle')}
          className="mb-6"
          actions={
            <>
              <div className="flex p-1 rounded-lg bg-surface border border-line">
                {(['table', 'cards', 'board'] as ViewMode[]).map((mode) => (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => setView(mode)}
                    className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                      view === mode
                        ? 'bg-bg text-fg shadow-sm'
                        : 'text-fg-muted hover:text-fg'
                    }`}
                  >
                    {mode === 'table' ? t('cases.viewTable') : mode === 'cards' ? t('cases.viewList') : t('cases.viewBoard')}
                  </button>
                ))}
              </div>
              <Button variant="primary" onClick={() => setShowForm(true)}>
                {t('cases.newCase')}
              </Button>
            </>
          }
        />

        <Modal open={showForm} onClose={() => setShowForm(false)} title={t('cases.newCase')} size="lg">
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Input
              label={t('cases.titleLabel')}
              placeholder={t('cases.titlePlaceholder')}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={500}
            />
            <div>
              <label className="block text-sm font-medium text-fg mb-1.5">
                {t('cases.descriptionOptional')} <span className="text-fg-muted font-normal">{t('cases.optionalHint')}</span>
              </label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                maxLength={5000}
                placeholder={t('cases.descriptionPlaceholder')}
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent resize-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-fg mb-1.5">
                {t('cases.clientLabel')} <span className="text-fg-muted font-normal">{t('cases.optionalHint')}</span>
              </label>
              <select
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              >
                <option value="">{t('cases.noClient')}</option>
                {clients.map((client) => (
                  <option key={client.id} value={client.id}>
                    {client.name} ({client.typeName})
                  </option>
                ))}
              </select>
            </div>
            {organizations.length > 0 && (
              <div>
                <label className="block text-sm font-medium text-fg mb-1.5">
                  {t('cases.orgLabel')} <span className="text-fg-muted font-normal">{t('cases.optionalHint')}</span>
                </label>
                <select
                  value={orgId}
                  onChange={(e) => setOrgId(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                >
                  <option value="">{t('cases.personalCase')}</option>
                  {organizations.map((org) => (
                    <option key={org.id} value={org.id}>
                      {org.name}
                    </option>
                  ))}
                </select>
                <p className="mt-1 text-xs text-fg-muted">
                  {t('cases.orgHint')}
                </p>
              </div>
            )}
            <div className="grid gap-4 sm:grid-cols-3">
              <DateField label={t('cases.filingDeadline')} value={filingDeadline} onChange={setFilingDeadline} />
              <DateField label={t('cases.hearing')} value={nextHearingDate} onChange={setNextHearingDate} />
              <DateField label={t('cases.expiresAt')} value={expiresAt} onChange={setExpiresAt} />
            </div>
            <Input
              label={t('cases.courtNumberLabel')}
              value={courtCaseNumber}
              onChange={(e) => setCourtCaseNumber(e.target.value)}
              maxLength={50}
              placeholder={t('cases.courtNumberPlaceholder')}
            />
            <div>
              <Input
                label={t('cases.hourlyRateLabel')}
                type="number"
                min="0"
                step="0.01"
                value={defaultHourlyRate}
                onChange={(e) => setDefaultHourlyRate(e.target.value)}
              />
              <p className="mt-1 text-xs text-fg-muted">{t('cases.hourlyRateHint')}</p>
            </div>
            {formError && <p className="text-sm text-danger">{formError}</p>}
            <div>
              <Button type="submit" variant="primary" loading={createMutation.isPending}>
                {t('cases.createCase')}
              </Button>
            </div>
          </form>
        </Modal>

        <div className="mb-4">
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t('cases.searchPlaceholder')}
            className="w-full px-3 py-2.5 rounded-lg border border-line bg-surface text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent"
          />
        </div>

        {organizations.length > 0 && (
          <div className="mb-4">
            <select
              value={orgFilter}
              onChange={(e) => setOrgFilter(e.target.value)}
              className="w-full sm:w-72 px-3 py-2.5 rounded-lg border border-line bg-surface text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
            >
              <option value="">{t('cases.allCases')}</option>
              {organizations.map((org) => (
                <option key={org.id} value={org.id}>
                  {org.name}
                </option>
              ))}
            </select>
          </div>
        )}

        {!isBoard && (
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <SavedViewBar
              views={savedViews.views}
              activeViewId={activeViewId}
              canShare={canShareViews}
              onApply={applySavedView}
              onSave={handleSaveView}
              onDelete={handleDeleteView}
            />
            {isTable && (
              <TableToolbar
                columns={columns}
                visibleColumnIds={preferences.visibleColumnIds}
                onToggleColumn={toggleColumn}
                groupBy={preferences.groupBy}
                onGroupByChange={setGroupBy}
                density={density}
                onDensityToggle={toggleDensity}
              />
            )}
          </div>
        )}

        {!isBoard && (
          <div className="flex flex-wrap gap-2 mb-4">
            <FilterChip label={t('common.all')} active={statusFilter === 'ALL'} onClick={() => setStatusFilter('ALL')} />
            {CASE_STATUS_ORDER.map((status) => (
              <FilterChip
                key={status}
                label={caseStatusLabel(status)}
                active={statusFilter === status}
                onClick={() => setStatusFilter(status)}
              />
            ))}
          </div>
        )}

        {view === 'cards' && cases.length > 0 && (
          <div className="mb-4">
            <label className="inline-flex items-center gap-2 text-xs text-fg-muted cursor-pointer select-none">
              <input
                type="checkbox"
                checked={allOnPageSelected}
                onChange={toggleSelectAll}
                className="h-4 w-4 rounded accent-accent cursor-pointer"
              />
              {selectedIds.size > 0 ? t('cases.selectedCount', { count: selectedIds.size }) : t('cases.selectAllOnPage')}
            </label>
          </div>
        )}

        {isLoading ? (
          <SkeletonCardGrid count={isBoard ? 5 : 6} />
        ) : view === 'board' ? (
          <BoardView
            cases={cases}
            onMove={(caseId, status) => statusMutation.mutate({ caseId, status })}
          />
        ) : isTable ? (
          <DataTable
            rows={cases}
            columns={columns}
            rowId={(row) => row.id}
            rowHref={(row) => `/cases/${row.id}`}
            onRowClick={(row) => navigate(`/cases/${row.id}`)}
            rowActions={rowActions}
            sort={preferences.sort}
            onSortChange={setSort}
            visibleColumnIds={preferences.visibleColumnIds}
            groupBy={preferences.groupBy}
            selectedIds={selectedIds}
            onToggleRow={toggleSelected}
            onToggleAll={toggleSelectAll}
            selectionLabel={(row) => t('cases.selectCaseAria', { title: row.title })}
            density={density}
            emptyState={
              debouncedSearch ? (
                <EmptyState illustration="cases" description={t('cases.notFound')} />
              ) : statusFilter === 'ALL' ? (
                <EmptyState illustration="cases"
                  title={t('cases.emptyTitle')}
                  description={t('cases.emptyDescription')}
                  action={{ label: t('cases.createCase'), onClick: () => setShowForm(true) }}
                />
              ) : (
                <EmptyState illustration="cases" description={t('cases.noStatusCases')} />
              )
            }
          />
        ) : cases.length === 0 ? (
          debouncedSearch ? (
            <EmptyState illustration="cases" description={t('cases.notFound')} />
          ) : statusFilter === 'ALL' ? (
            <EmptyState illustration="cases"
              title={t('cases.emptyTitle')}
              description={t('cases.emptyDescription')}
              action={{ label: t('cases.createCase'), onClick: () => setShowForm(true) }}
            />
          ) : (
            <EmptyState illustration="cases" description={t('cases.noStatusCases')} />
          )
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {cases.map((caseItem, index) => (
              <motion.div
                key={caseItem.id}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2, delay: index * 0.03 }}
                className={`h-full p-5 rounded-2xl bg-surface border transition-colors flex flex-col ${
                  selectedIds.has(caseItem.id)
                    ? 'border-accent ring-1 ring-accent/40'
                    : 'border-line hover:border-accent/50'
                }`}
              >
                <div className="flex items-start gap-3 flex-1 min-w-0">
                  <input
                    type="checkbox"
                    checked={selectedIds.has(caseItem.id)}
                    onChange={() => toggleSelected(caseItem.id)}
                    aria-label={t('cases.selectCaseAria', { title: caseItem.title })}
                    className="mt-1 h-4 w-4 shrink-0 rounded accent-accent cursor-pointer"
                  />
                  <Link to={`/cases/${caseItem.id}`} className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h3 className="font-medium text-fg line-clamp-2 min-w-0 [overflow-wrap:anywhere]">{caseItem.title}</h3>
                    <CaseStatusBadge status={caseItem.status} />
                  </div>
                  {caseItem.orgId && (
                    <span className="inline-block mb-2 px-2 py-0.5 rounded-full text-[11px] font-medium bg-bg border border-line text-fg-muted">
                      {orgNameById.get(caseItem.orgId) ?? t('cases.orgFallback')}
                    </span>
                  )}
                  {caseItem.clientName && (
                    <p className="text-xs text-accent mb-2 truncate">{caseItem.clientName}</p>
                  )}
                  {caseItem.description && (
                    <p className="text-sm text-fg-muted line-clamp-2 mb-3">
                      {caseItem.description}
                    </p>
                  )}
                </Link>
                </div>
                <div className="flex items-center justify-between gap-2 mt-3 pt-3 border-t border-line">
                  <CaseStatusSelect
                    value={caseItem.status}
                    disabled={statusMutation.isPending}
                    onChange={(status) => statusMutation.mutate({ caseId: caseItem.id, status })}
                  />
                  <span className="text-xs text-fg-muted">
                    {formatDate(caseItem.createdAt)}
                  </span>
                </div>
              </motion.div>
            ))}
          </div>
        )}

        {!isBoard && !isLoading && (
          <Pagination page={page} pageSize={pageSize} total={total} onPageChange={setPage} />
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
              <div className="pointer-events-auto flex items-center gap-3 rounded-xl border border-line bg-overlay shadow-card px-4 py-2.5">
                <span className="text-sm font-medium text-fg">
                  {t('cases.selectedCount', { count: selectedIds.size })}
                </span>
                <span className="h-5 w-px bg-line" />
                <select
                  value=""
                  disabled={bulkStatusMutation.isPending}
                  onChange={(e) => {
                    const status = e.target.value as CaseStatus
                    if (status) bulkStatusMutation.mutate({ ids: Array.from(selectedIds), status })
                  }}
                  className="px-3 py-1.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-50"
                >
                  <option value="">{t('cases.changeStatus')}</option>
                  {CASE_STATUS_ORDER.map((status) => (
                    <option key={status} value={status}>
                      {caseStatusLabel(status)}
                    </option>
                  ))}
                </select>
                {organizations.length > 0 && (
                  <select
                    value=""
                    disabled={bulkChangeOrgMutation.isPending}
                    onChange={(e) => {
                      const value = e.target.value
                      if (value === '') return
                      bulkChangeOrgMutation.mutate({
                        ids: Array.from(selectedIds),
                        newOrgId: value === 'personal' ? null : value,
                      })
                    }}
                    className="px-3 py-1.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-50"
                  >
                    <option value="">{t('cases.bulkChangeOrg')}</option>
                    <option value="personal">{t('cases.personalCase')}</option>
                    {organizations.map((org) => (
                      <option key={org.id} value={org.id}>
                        {org.name}
                      </option>
                    ))}
                  </select>
                )}
                {orgFilter && (
                  <select
                    value=""
                    disabled={bulkTransferOwnerMutation.isPending || orgFilterMembers.length === 0}
                    title={t('cases.bulkTransferOwnerHint')}
                    onChange={(e) => {
                      const newOwnerId = e.target.value
                      if (newOwnerId) {
                        bulkTransferOwnerMutation.mutate({ ids: Array.from(selectedIds), newOwnerId })
                      }
                    }}
                    className="px-3 py-1.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-50"
                  >
                    <option value="">{t('cases.bulkTransferOwner')}</option>
                    {orgFilterMembers.map((member) => (
                      <option key={member.userId} value={member.userId}>
                        {member.fullName || member.email || member.userId}
                      </option>
                    ))}
                  </select>
                )}
                <button
                  type="button"
                  onClick={() => setSelectedIds(new Set())}
                  className="text-sm text-fg-muted hover:text-fg transition-colors"
                >
                  {t('cases.deselect')}
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
          ? 'bg-fg text-bg border-transparent'
          : 'border-line text-fg-muted hover:text-fg'
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
  const { t } = useTranslation()
  const [dragOver, setDragOver] = useState<CaseStatus | null>(null)
  const [announcement, setAnnouncement] = useState('')

  const handleDrop = (status: CaseStatus, caseId: string): void => {
    setDragOver(null)
    const moved = cases.find((c) => c.id === caseId)
    if (moved && moved.status !== status) {
      onMove(caseId, status)
    }
  }

  const moveByKeyboard = (caseItem: CaseResponse, offset: number): void => {
    const targetIndex = CASE_STATUS_ORDER.indexOf(caseItem.status) + offset
    if (targetIndex < 0 || targetIndex >= CASE_STATUS_ORDER.length) {
      return
    }
    const target = CASE_STATUS_ORDER[targetIndex]
    onMove(caseItem.id, target)
    setAnnouncement(
      t('cases.kanbanMoved', { title: caseItem.title, status: caseStatusLabel(target) }),
    )
  }

  return (
    <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-5">
      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>
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
                ? 'border-accent bg-accent/5'
                : 'border-line bg-surface'
            }`}
          >
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-semibold text-fg" id={`kanban-column-${status}`}>
                {caseStatusLabel(status)}
              </span>
              <span className="text-xs text-fg-muted">{columnCases.length}</span>
            </div>
            <ul className="flex flex-col gap-2" aria-labelledby={`kanban-column-${status}`}>
              {columnCases.map((caseItem) => (
                <li
                  key={caseItem.id}
                  draggable
                  onDragStart={(e) => e.dataTransfer.setData('text/plain', caseItem.id)}
                  className="rounded-lg border border-line bg-bg p-3 cursor-grab active:cursor-grabbing"
                >
                  <Link
                    to={`/cases/${caseItem.id}`}
                    className="block"
                    aria-keyshortcuts="ArrowLeft ArrowRight"
                    title={t('cases.kanbanMoveHint')}
                    onKeyDown={(e) => {
                      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') {
                        return
                      }
                      e.preventDefault()
                      moveByKeyboard(caseItem, e.key === 'ArrowRight' ? 1 : -1)
                    }}
                  >
                    <p className="text-sm font-medium text-fg line-clamp-2">
                      {caseItem.title}
                    </p>
                    {caseItem.clientName && (
                      <p className="text-xs text-accent mt-1 truncate">
                        {caseItem.clientName}
                      </p>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </div>
  )
}
