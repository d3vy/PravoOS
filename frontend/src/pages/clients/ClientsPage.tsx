import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { clientsApi } from '../../api/clients'
import { MAX_PAGE_SIZE, type Page } from '../../api/pagination'
import type { ClientResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { SkeletonList } from '../../components/ui/Skeleton'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { Modal } from '../../components/ui/Modal'
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable'
import { TableToolbar } from '../../components/ui/TableToolbar'
import { ClientForm } from '../../components/clients/ClientForm'
import { useToast } from '../../hooks/useToast'
import { useDensity } from '../../hooks/useDensity'
import { useTablePreferences } from '../../hooks/useTablePreferences'

const TABLE_KEY = 'clients'
const DEFAULT_COLUMN_IDS = ['name', 'type', 'phone', 'email', 'caseCount']

export default function ClientsPage(): JSX.Element {
  const { t } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [density, toggleDensity] = useDensity()
  const { preferences, setSort, setGroupBy, toggleColumn } = useTablePreferences(TABLE_KEY, {
    visibleColumnIds: DEFAULT_COLUMN_IDS,
    sort: [],
    groupBy: null,
  })

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
  const clients = clientsPage?.items ?? []
  const total = clientsPage?.total ?? 0

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

        <div className="flex justify-end mb-4">
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
            density={density}
            emptyState={
              <EmptyState
                title={t('clients.emptyTitle')}
                description={t('clients.emptyDescription')}
                action={{ label: t('clients.newClient'), onClick: () => setShowForm(true) }}
              />
            }
          />
        )}

        {!isLoading && (
          <Pagination page={page} pageSize={MAX_PAGE_SIZE} total={total} onPageChange={setPage} />
        )}
      </div>
    </div>
  )
}
