import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { AnimatePresence, motion } from 'framer-motion'
import i18n from '../../i18n'
import { clientsApi } from '../../api/clients'
import { organizationsApi } from '../../api/organizations'
import { MAX_PAGE_SIZE, type Page } from '../../api/pagination'
import type { ClientResponse, Organization } from '../../types'
import { Button } from '../../components/ui/Button'
import { SkeletonList } from '../../components/ui/Skeleton'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { Modal } from '../../components/ui/Modal'
import { DataTable, type DataTableColumn, type SortRule } from '../../components/ui/DataTable'
import { TableToolbar } from '../../components/ui/TableToolbar'
import { SavedViewBar } from '../../components/ui/SavedViewBar'
import { ClientForm } from '../../components/clients/ClientForm'
import { useToast } from '../../hooks/useToast'
import { useDensity } from '../../hooks/useDensity'
import { useSavedViews, type SavedView } from '../../hooks/useSavedViews'
import { useTablePreferences } from '../../hooks/useTablePreferences'

const TABLE_KEY = 'clients'
const DEFAULT_COLUMN_IDS = ['name', 'type', 'phone', 'email', 'caseCount']

interface ClientsViewConfig {
  search: string
  sort: SortRule[]
  groupBy: string | null
  visibleColumnIds: string[]
}

export default function ClientsPage(): JSX.Element {
  const { t } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [activeViewId, setActiveViewId] = useState<string | null>(null)
  const [showBulkDeleteDialog, setShowBulkDeleteDialog] = useState(false)
  const [bulkDeleteError, setBulkDeleteError] = useState<string | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [density, toggleDensity] = useDensity()
  const { preferences, setSort, setGroupBy, toggleColumn, applyPreferences } = useTablePreferences(TABLE_KEY, {
    visibleColumnIds: DEFAULT_COLUMN_IDS,
    sort: [],
    groupBy: null,
  })
  const savedViews = useSavedViews<ClientsViewConfig>('CLIENTS')

  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setShowForm(true)
      searchParams.delete('new')
      setSearchParams(searchParams, { replace: true })
    }
  }, [searchParams, setSearchParams])

  const { data: clientsPage, isLoading } = useQuery<Page<ClientResponse>>({
    queryKey: ['clients', page, MAX_PAGE_SIZE],
    queryFn: () => clientsApi.list(page, MAX_PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
  const clientsOnPage = clientsPage?.items ?? []
  const total = clientsPage?.total ?? 0

  const { data: organizations = [] } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: organizationsApi.list,
  })

  const normalizedSearch = search.trim().toLowerCase()
  const clients = useMemo(() => {
    if (!normalizedSearch) return clientsOnPage
    return clientsOnPage.filter((client) =>
      [client.name, client.email, client.phone, client.inn].some((field) =>
        field?.toLowerCase().includes(normalizedSearch)
      )
    )
  }, [clientsOnPage, normalizedSearch])

  useEffect(() => {
    setSelectedIds(new Set())
  }, [page])

  const createMutation = useMutation({
    mutationFn: clientsApi.create,
    onSuccess: (client) => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      setShowForm(false)
      setFormError(null)
      toast.success(t('clients.createSuccess', { name: client.name }))
    },
    onError: () => setFormError(t('clients.createError')),
    meta: { suppressErrorToast: true },
  })

  const bulkDeleteMutation = useMutation({
    mutationFn: async ({ ids, cascade }: { ids: string[]; cascade: boolean }) => {
      const results = await Promise.allSettled(ids.map((id) => clientsApi.delete(id, cascade)))
      const failed = results.filter((result) => result.status === 'rejected').length
      return { succeeded: ids.length - failed, failed }
    },
    onSuccess: ({ succeeded, failed }) => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setSelectedIds(new Set())
      setShowBulkDeleteDialog(false)
      if (failed > 0) {
        setBulkDeleteError(t('clients.bulkDeleteError'))
      } else {
        setBulkDeleteError(null)
        toast.success(t('clients.bulkDeleteSuccess', { count: succeeded }))
      }
    },
    onError: () => setBulkDeleteError(t('clients.bulkDeleteError')),
  })

  const toggleSelected = (clientId: string): void => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(clientId)) next.delete(clientId)
      else next.add(clientId)
      return next
    })
  }

  const allOnPageSelected = clients.length > 0 && clients.every((c) => selectedIds.has(c.id))

  const toggleSelectAll = (): void => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (allOnPageSelected) clients.forEach((c) => next.delete(c.id))
      else clients.forEach((c) => next.add(c.id))
      return next
    })
  }

  const columns = useMemo<DataTableColumn<ClientResponse>[]>(
    () => [
      {
        id: 'name',
        header: t('clients.columnName'),
        alwaysVisible: true,
        sortable: true,
        width: 'minmax(0, 2fr)',
        value: (row) => row.name,
      },
      {
        id: 'type',
        header: t('clients.columnType'),
        sortable: true,
        groupable: true,
        width: '9rem',
        value: (row) => row.typeName,
      },
      { id: 'phone', header: t('clients.columnPhone'), width: '11rem', value: (row) => row.phone },
      { id: 'email', header: t('clients.columnEmail'), value: (row) => row.email },
      { id: 'inn', header: t('clients.columnInn'), width: '10rem', value: (row) => row.inn },
      {
        id: 'caseCount',
        header: t('clients.columnCases'),
        sortable: true,
        align: 'right',
        width: '7rem',
        value: (row) => row.caseCount,
      },
      {
        id: 'createdAt',
        header: t('clients.columnCreated'),
        sortable: true,
        width: '9rem',
        value: (row) => row.createdAt,
        render: (row) =>
          new Date(row.createdAt).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'),
      },
    ],
    [t]
  )

  const canShareViews = organizations.length > 0
  const shareOrgId = organizations.length === 1 ? organizations[0].id : null

  const currentConfig: ClientsViewConfig = {
    search,
    sort: preferences.sort,
    groupBy: preferences.groupBy,
    visibleColumnIds: preferences.visibleColumnIds,
  }

  const applySavedView = (savedView: SavedView<ClientsViewConfig>): void => {
    const config = savedView.config
    if (!config) return
    setSearch(config.search ?? '')
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

  return (
    <div className="bg-bg">
      <div className="page-container py-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-semibold text-fg mb-1">{t('clients.title')}</h1>
            <p className="text-sm text-fg-muted">
              {t('clients.subtitle')}
            </p>
          </div>
          <Button variant="primary" onClick={() => setShowForm(true)}>
            {t('clients.newClient')}
          </Button>
        </div>

        <Modal open={showForm} onClose={() => setShowForm(false)} title={t('clients.newClient')}>
          <ClientForm
            submitLabel={t('clients.createSubmit')}
            isSubmitting={createMutation.isPending}
            error={formError}
            onSubmit={(data) => createMutation.mutate(data)}
          />
        </Modal>

        <div className="mb-4">
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t('clients.searchPlaceholder')}
            className="w-full px-3 py-2.5 rounded-lg border border-line bg-surface text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent"
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <SavedViewBar
            views={savedViews.views}
            activeViewId={activeViewId}
            canShare={canShareViews}
            onApply={applySavedView}
            onSave={handleSaveView}
            onDelete={handleDeleteView}
          />
          <TableToolbar
            columns={columns}
            visibleColumnIds={preferences.visibleColumnIds}
            onToggleColumn={toggleColumn}
            groupBy={preferences.groupBy}
            onGroupByChange={setGroupBy}
            density={density}
            onDensityToggle={toggleDensity}
          />
        </div>

        {isLoading ? (
          <SkeletonList count={8} />
        ) : (
          <DataTable
            rows={clients}
            columns={columns}
            rowId={(row) => row.id}
            rowHref={(row) => `/clients/${row.id}`}
            onRowClick={(row) => navigate(`/clients/${row.id}`)}
            sort={preferences.sort}
            onSortChange={setSort}
            visibleColumnIds={preferences.visibleColumnIds}
            groupBy={preferences.groupBy}
            selectedIds={selectedIds}
            onToggleRow={toggleSelected}
            onToggleAll={toggleSelectAll}
            selectionLabel={(row) => t('clients.selectClientAria', { name: row.name })}
            density={density}
            emptyState={
              normalizedSearch ? (
                <EmptyState description={t('clients.notFound')} />
              ) : (
                <EmptyState
                  title={t('clients.emptyTitle')}
                  description={t('clients.emptyDescription')}
                  action={{ label: t('clients.newClient'), onClick: () => setShowForm(true) }}
                />
              )
            }
          />
        )}

        {!isLoading && (
          <Pagination page={page} pageSize={MAX_PAGE_SIZE} total={total} onPageChange={setPage} />
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
                  {t('clients.selectedCount', { count: selectedIds.size })}
                </span>
                <span className="h-5 w-px bg-line" />
                <button
                  type="button"
                  onClick={() => {
                    setBulkDeleteError(null)
                    setShowBulkDeleteDialog(true)
                  }}
                  className="px-3 py-1.5 rounded-lg border border-line bg-bg text-danger text-sm font-medium hover:border-danger/60 transition-colors"
                >
                  {t('clients.bulkDelete')}
                </button>
                <button
                  type="button"
                  onClick={() => setSelectedIds(new Set())}
                  className="text-sm text-fg-muted hover:text-fg transition-colors"
                >
                  {t('clients.deselect')}
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {showBulkDeleteDialog && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div className="w-full max-w-md rounded-xl bg-bg border border-line p-6">
              <h3 className="text-lg font-semibold text-fg mb-2">{t('clients.bulkDeleteTitle')}</h3>
              <p className="text-sm text-fg-muted mb-6">
                {t('clients.bulkDeleteDescription', { count: selectedIds.size })}
              </p>

              {bulkDeleteError && <p className="text-sm text-danger mb-4">{bulkDeleteError}</p>}

              <div className="flex flex-col gap-2">
                <Button
                  variant="secondary"
                  loading={bulkDeleteMutation.isPending}
                  onClick={() => bulkDeleteMutation.mutate({ ids: Array.from(selectedIds), cascade: false })}
                >
                  {t('clients.bulkDeleteKeepCases')}
                </Button>
                <Button
                  variant="primary"
                  loading={bulkDeleteMutation.isPending}
                  onClick={() => bulkDeleteMutation.mutate({ ids: Array.from(selectedIds), cascade: true })}
                >
                  {t('clients.bulkDeleteAllCases')}
                </Button>
                <button
                  onClick={() => {
                    setShowBulkDeleteDialog(false)
                    setBulkDeleteError(null)
                  }}
                  className="mt-1 text-sm text-fg-muted hover:text-fg"
                >
                  {t('clients.cancel')}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
