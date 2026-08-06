import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { invoicesApi } from '../../api/invoices'
import { organizationsApi } from '../../api/organizations'
import { MAX_PAGE_SIZE, type Page } from '../../api/pagination'
import type { InvoiceStatus, InvoiceSummary, Organization } from '../../types'
import { SkeletonList } from '../../components/ui/Skeleton'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { DataTable, type DataTableColumn, type SortRule } from '../../components/ui/DataTable'
import { TableToolbar } from '../../components/ui/TableToolbar'
import { SavedViewBar } from '../../components/ui/SavedViewBar'
import { InvoiceStatusBadge } from '../../components/invoices/InvoiceStatusBadge'
import { BillingProfileCard } from '../../components/invoices/BillingProfileCard'
import { formatMoney } from '../../utils/billing'
import { useDensity } from '../../hooks/useDensity'
import { useSavedViews, type SavedView } from '../../hooks/useSavedViews'
import { useTablePreferences } from '../../hooks/useTablePreferences'
import { useToast } from '../../hooks/useToast'
import { PageHeader } from '../../components/ui/PageHeader'

type StatusFilter = InvoiceStatus | 'ALL'

interface InvoicesViewConfig {
  status: StatusFilter
  sort: SortRule[]
  groupBy: string | null
  visibleColumnIds: string[]
}

const TABLE_KEY = 'invoices'
const DEFAULT_COLUMN_IDS = ['number', 'client', 'status', 'issueDate', 'total']
const STATUS_ORDER: InvoiceStatus[] = ['DRAFT', 'ISSUED', 'PAID', 'CANCELED']

export default function InvoicesPage(): JSX.Element {
  const { t, i18n } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [activeViewId, setActiveViewId] = useState<string | null>(null)
  const [density, toggleDensity] = useDensity()
  const { preferences, setSort, setGroupBy, toggleColumn, applyPreferences } = useTablePreferences(TABLE_KEY, {
    visibleColumnIds: DEFAULT_COLUMN_IDS,
    sort: [],
    groupBy: null,
  })
  const savedViews = useSavedViews<InvoicesViewConfig>('INVOICES')

  const { data, isLoading } = useQuery<Page<InvoiceSummary>>({
    queryKey: ['invoices', page, MAX_PAGE_SIZE],
    queryFn: () => invoicesApi.list(undefined, page, MAX_PAGE_SIZE),
    placeholderData: keepPreviousData,
  })

  const { data: organizations = [] } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: organizationsApi.list,
  })

  const total = data?.total ?? 0
  const invoices = useMemo(() => {
    const items = data?.items ?? []
    return statusFilter === 'ALL' ? items : items.filter((invoice) => invoice.status === statusFilter)
  }, [data, statusFilter])

  const columns = useMemo<DataTableColumn<InvoiceSummary>[]>(
    () => [
      {
        id: 'number',
        header: t('invoices.columnNumber'),
        alwaysVisible: true,
        sortable: true,
        width: 'minmax(0, 1.2fr)',
        value: (row) => row.number,
      },
      {
        id: 'client',
        header: t('invoices.columnClient'),
        sortable: true,
        groupable: true,
        value: (row) => row.clientName ?? t('invoices.clientDeleted'),
      },
      {
        id: 'status',
        header: t('invoices.columnStatus'),
        sortable: true,
        groupable: true,
        width: '9rem',
        value: (row) => STATUS_ORDER.indexOf(row.status),
        groupLabel: (row) => row.statusLabel,
        render: (row) => <InvoiceStatusBadge status={row.status} />,
      },
      {
        id: 'issueDate',
        header: t('invoices.columnIssued'),
        sortable: true,
        width: '9rem',
        value: (row) => row.issueDate,
        render: (row) => new Date(row.issueDate).toLocaleDateString(locale),
      },
      {
        id: 'dueDate',
        header: t('invoices.columnDue'),
        sortable: true,
        width: '9rem',
        value: (row) => row.dueDate,
        render: (row) => (row.dueDate ? new Date(row.dueDate).toLocaleDateString(locale) : '—'),
      },
      {
        id: 'total',
        header: t('invoices.columnTotal'),
        sortable: true,
        align: 'right',
        width: '10rem',
        value: (row) => row.total,
        render: (row) => formatMoney(row.total, row.currency),
      },
    ],
    [t, locale]
  )

  const shareOrgId = organizations.length === 1 ? organizations[0].id : null

  const applySavedView = (savedView: SavedView<InvoicesViewConfig>): void => {
    const config = savedView.config
    if (!config) return
    setStatusFilter(config.status ?? 'ALL')
    applyPreferences({
      sort: config.sort ?? [],
      groupBy: config.groupBy ?? null,
      visibleColumnIds: config.visibleColumnIds ?? DEFAULT_COLUMN_IDS,
    })
    setActiveViewId(savedView.id)
  }

  const handleSaveView = (name: string, sharedWithTeam: boolean): void => {
    savedViews.saveView({
      name,
      config: {
        status: statusFilter,
        sort: preferences.sort,
        groupBy: preferences.groupBy,
        visibleColumnIds: preferences.visibleColumnIds,
      },
      sharedWithTeam,
      orgId: shareOrgId,
    })
    toast.success(t('savedViews.saved', { name }))
  }

  return (
    <div className="bg-bg">
      <div className="page-container py-8">
        <PageHeader title={t('invoices.title')} description={t('invoices.subtitle')} />

        <BillingProfileCard />

        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <SavedViewBar
            views={savedViews.views}
            activeViewId={activeViewId}
            canShare={shareOrgId !== null}
            onApply={applySavedView}
            onSave={handleSaveView}
            onDelete={(viewId) => {
              savedViews.deleteView(viewId)
              setActiveViewId((current) => (current === viewId ? null : current))
            }}
          />
          <TableToolbar
            columns={columns}
            visibleColumnIds={preferences.visibleColumnIds}
            onToggleColumn={toggleColumn}
            groupBy={preferences.groupBy}
            onGroupByChange={setGroupBy}
            density={density}
            onDensityToggle={toggleDensity}
          >
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
              aria-label={t('invoices.columnStatus')}
              className="px-3 py-1.5 rounded-lg text-xs font-medium border border-line bg-surface text-fg-muted focus:outline-none focus:ring-2 focus:ring-accent"
            >
              <option value="ALL">{t('common.all')}</option>
              {STATUS_ORDER.map((status) => (
                <option key={status} value={status}>
                  {t(`invoices.status.${status}`)}
                </option>
              ))}
            </select>
          </TableToolbar>
        </div>

        {isLoading ? (
          <SkeletonList count={6} />
        ) : (
          <DataTable
            rows={invoices}
            columns={columns}
            rowId={(row) => row.id}
            rowHref={(row) => `/invoices/${row.id}`}
            onRowClick={(row) => navigate(`/invoices/${row.id}`)}
            sort={preferences.sort}
            onSortChange={setSort}
            visibleColumnIds={preferences.visibleColumnIds}
            groupBy={preferences.groupBy}
            density={density}
            emptyState={<EmptyState illustration="invoices" description={t('invoices.emptyHint')} />}
          />
        )}

        <Pagination page={page} total={total} pageSize={MAX_PAGE_SIZE} onPageChange={setPage} />
      </div>
    </div>
  )
}
