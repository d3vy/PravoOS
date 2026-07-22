import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import axios from 'axios'
import i18n from '../../i18n'
import { adminApi, DEFAULT_PAGE_SIZE, type Page } from '../../api/admin'
import type { ApplicationResponse } from '../../types'
import { ApplicationStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable'
import { TableToolbar } from '../../components/ui/TableToolbar'
import { useDensity } from '../../hooks/useDensity'
import { useTablePreferences } from '../../hooks/useTablePreferences'

type Tab = 'all' | 'pending'

export default function ApplicationsPage(): JSX.Element {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<Tab>('all')
  const [page, setPage] = useState(0)
  const [processingId, setProcessingId] = useState<string | null>(null)
  const [approveError, setApproveError] = useState<{ id: string; message: string } | null>(null)
  const queryClient = useQueryClient()
  const [density, toggleDensity] = useDensity()
  const { preferences, setSort, setGroupBy, toggleColumn } = useTablePreferences('admin-applications', {
    visibleColumnIds: ['fullName', 'email', 'specialization', 'status', 'submittedAt'],
    sort: [],
    groupBy: null,
  })

  const { data: allData, isLoading: allLoading } = useQuery<Page<ApplicationResponse>>({
    queryKey: ['applications', 'all', page],
    queryFn: () => adminApi.getAllApplications(page),
    placeholderData: keepPreviousData,
  })

  const { data: pendingData, isLoading: pendingLoading } = useQuery<Page<ApplicationResponse>>({
    queryKey: ['applications', 'pending', page],
    queryFn: () => adminApi.getPendingApplications(page),
    placeholderData: keepPreviousData,
  })

  const allApplications = allData?.items ?? []
  const pendingApplications = pendingData?.items ?? []
  const allTotal = allData?.total ?? 0
  const pendingTotal = pendingData?.total ?? 0

  const changeTab = (tab: Tab): void => {
    setActiveTab(tab)
    setPage(0)
  }

  const approveMutation = useMutation({
    mutationFn: (id: string) => adminApi.approveApplication(id),
    onMutate: async (id) => {
      setProcessingId(id)
      await queryClient.cancelQueries({ queryKey: ['applications'] })
      const updateStatus = (apps: ApplicationResponse[]): ApplicationResponse[] =>
        apps.map((a) => (a.id === id ? { ...a, status: 'APPROVED' as const } : a))
      queryClient.setQueriesData<Page<ApplicationResponse>>({ queryKey: ['applications', 'all'] }, (old) =>
        old ? { ...old, items: updateStatus(old.items) } : old
      )
      queryClient.setQueriesData<Page<ApplicationResponse>>({ queryKey: ['applications', 'pending'] }, (old) =>
        old ? { ...old, items: old.items.filter((a) => a.id !== id), total: Math.max(0, old.total - 1) } : old
      )
    },
    onError: (error, id) => {
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : t('adminApplications.approveError')
      setApproveError({ id, message })
      setTimeout(() => setApproveError(null), 6000)
    },
    onSettled: () => {
      setProcessingId(null)
      queryClient.invalidateQueries({ queryKey: ['applications'] })
    },
  })

  const forceApproveMutation = useMutation({
    mutationFn: (id: string) => adminApi.approveApplicationForce(id),
    onMutate: async (id) => {
      setProcessingId(id)
      await queryClient.cancelQueries({ queryKey: ['applications'] })
      const updateStatus = (apps: ApplicationResponse[]): ApplicationResponse[] =>
        apps.map((a) => (a.id === id ? { ...a, status: 'APPROVED' as const, emailVerified: true } : a))
      queryClient.setQueriesData<Page<ApplicationResponse>>({ queryKey: ['applications', 'all'] }, (old) =>
        old ? { ...old, items: updateStatus(old.items) } : old
      )
      queryClient.setQueriesData<Page<ApplicationResponse>>({ queryKey: ['applications', 'pending'] }, (old) =>
        old ? { ...old, items: old.items.filter((a) => a.id !== id), total: Math.max(0, old.total - 1) } : old
      )
    },
    onError: (error, id) => {
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : t('adminApplications.approveError')
      setApproveError({ id, message })
      setTimeout(() => setApproveError(null), 6000)
    },
    onSettled: () => {
      setProcessingId(null)
      queryClient.invalidateQueries({ queryKey: ['applications'] })
    },
  })

  const rejectMutation = useMutation({
    mutationFn: (id: string) => adminApi.rejectApplication(id),
    onMutate: async (id) => {
      setProcessingId(id)
      await queryClient.cancelQueries({ queryKey: ['applications'] })
      const updateStatus = (apps: ApplicationResponse[]): ApplicationResponse[] =>
        apps.map((a) => (a.id === id ? { ...a, status: 'REJECTED' as const } : a))
      queryClient.setQueriesData<Page<ApplicationResponse>>({ queryKey: ['applications', 'all'] }, (old) =>
        old ? { ...old, items: updateStatus(old.items) } : old
      )
      queryClient.setQueriesData<Page<ApplicationResponse>>({ queryKey: ['applications', 'pending'] }, (old) =>
        old ? { ...old, items: old.items.filter((a) => a.id !== id), total: Math.max(0, old.total - 1) } : old
      )
    },
    onSettled: () => {
      setProcessingId(null)
      queryClient.invalidateQueries({ queryKey: ['applications'] })
    },
  })

  const displayedApplications = activeTab === 'pending' ? pendingApplications : allApplications
  const isLoading = activeTab === 'pending' ? pendingLoading : allLoading
  const displayedTotal = activeTab === 'pending' ? pendingTotal : allTotal
  const pendingCount = pendingTotal

  const dateLocale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'

  const columns = useMemo<DataTableColumn<ApplicationResponse>[]>(
    () => [
      {
        id: 'fullName',
        header: t('adminApplications.columnName'),
        alwaysVisible: true,
        sortable: true,
        width: 'minmax(0, 1.5fr)',
        value: (row) => row.fullName,
      },
      {
        id: 'email',
        header: t('adminApplications.email'),
        sortable: true,
        value: (row) => row.email,
        render: (row) => (
          <span className="flex items-center gap-1.5 min-w-0">
            <span className="truncate">{row.email}</span>
            {row.emailVerified ? (
              <span className="text-xs text-success shrink-0">✓</span>
            ) : (
              <span
                className="text-xs text-warning shrink-0"
                title={t('adminApplications.emailNotVerified')}
              >
                !
              </span>
            )}
          </span>
        ),
      },
      { id: 'phone', header: t('adminApplications.phone'), width: '11rem', value: (row) => row.phone },
      {
        id: 'specialization',
        header: t('adminApplications.specialization'),
        sortable: true,
        groupable: true,
        value: (row) => row.specialization,
      },
      {
        id: 'status',
        header: t('adminApplications.columnStatus'),
        sortable: true,
        groupable: true,
        width: '9rem',
        value: (row) => row.status,
        render: (row) => <ApplicationStatusBadge status={row.status} />,
      },
      {
        id: 'submittedAt',
        header: t('adminApplications.columnSubmitted'),
        sortable: true,
        width: '11rem',
        value: (row) => row.submittedAt,
        render: (row) => new Date(row.submittedAt).toLocaleString(dateLocale),
      },
      {
        id: 'reviewedAt',
        header: t('adminApplications.columnReviewed'),
        sortable: true,
        width: '11rem',
        value: (row) => row.reviewedAt,
        render: (row) => (row.reviewedAt ? new Date(row.reviewedAt).toLocaleString(dateLocale) : '—'),
      },
    ],
    [t, dateLocale]
  )

  return (
    <div className="p-6 lg:p-8">
      <div className="mb-8">
        <h1 className="text-3xl font-semibold text-fg mb-1">{t('adminApplications.title')}</h1>
        <p className="text-sm text-fg-muted">
          {t('adminApplications.subtitle')}
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 p-1 bg-bg rounded-lg w-fit mb-6 border border-line">
        <button
          onClick={() => changeTab('all')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            activeTab === 'all'
              ? 'bg-surface text-fg shadow-sm'
              : 'text-fg-muted hover:text-fg'
          }`}
        >
          {t('adminApplications.tabAll')}
        </button>
        <button
          onClick={() => changeTab('pending')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors flex items-center gap-2 ${
            activeTab === 'pending'
              ? 'bg-surface text-fg shadow-sm'
              : 'text-fg-muted hover:text-fg'
          }`}
        >
          {t('adminApplications.tabPending')}
          {pendingCount > 0 && (
            <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-400 text-xs font-semibold flex items-center justify-center">
              {pendingCount}
            </span>
          )}
        </button>
      </div>

      <div className="flex justify-end mb-3">
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

      <AnimatePresence>
        {approveError && (
          <motion.p
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="mb-3 text-sm text-warning"
          >
            {approveError.message}
          </motion.p>
        )}
      </AnimatePresence>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : (
        <DataTable
          rows={displayedApplications}
          columns={columns}
          rowId={(row) => row.id}
          sort={preferences.sort}
          onSortChange={setSort}
          visibleColumnIds={preferences.visibleColumnIds}
          groupBy={preferences.groupBy}
          density={density}
          rowActions={(application) =>
            application.status === 'PENDING' ? (
              <>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => approveMutation.mutate(application.id)}
                  loading={processingId === application.id}
                  disabled={processingId === application.id}
                >
                  {t('adminApplications.approve')}
                </Button>
                {!application.emailVerified && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => forceApproveMutation.mutate(application.id)}
                    disabled={processingId === application.id}
                    title={t('adminApplications.approveForceTitle')}
                  >
                    {t('adminApplications.approveForce')}
                  </Button>
                )}
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => rejectMutation.mutate(application.id)}
                  disabled={processingId === application.id}
                >
                  {t('adminApplications.reject')}
                </Button>
              </>
            ) : null
          }
          emptyState={
            <EmptyState
              description={
                activeTab === 'pending' ? t('adminApplications.emptyPending') : t('adminApplications.emptyAll')
              }
            />
          }
        />
      )}

      <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={displayedTotal} onPageChange={setPage} />
    </div>
  )
}
