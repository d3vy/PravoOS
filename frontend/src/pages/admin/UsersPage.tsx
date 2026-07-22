import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { adminApi, DEFAULT_PAGE_SIZE, type Page } from '../../api/admin'
import i18n from '../../i18n'
import type { LawyerProfileResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable'
import { TableToolbar } from '../../components/ui/TableToolbar'
import { useDensity } from '../../hooks/useDensity'
import { useTablePreferences } from '../../hooks/useTablePreferences'

export default function UsersPage(): JSX.Element {
  const { t } = useTranslation()
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()
  const [density, toggleDensity] = useDensity()
  const { preferences, setSort, setGroupBy, toggleColumn } = useTablePreferences('admin-lawyers', {
    visibleColumnIds: ['fullName', 'email', 'specialization', 'phone'],
    sort: [],
    groupBy: null,
  })

  const { data, isLoading } = useQuery<Page<LawyerProfileResponse>>({
    queryKey: ['admin-lawyers', page],
    queryFn: () => adminApi.getLawyers(page),
    placeholderData: keepPreviousData,
  })
  const lawyers = data?.items ?? []
  const total = data?.total ?? 0

  const deleteMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      const results = await Promise.allSettled(ids.map((id) => adminApi.deleteLawyer(id)))
      const failed = results.filter((r) => r.status === 'rejected').length
      if (failed > 0) {
        throw new Error(
          failed === ids.length
            ? i18n.t('adminUsers.deleteAllFailed')
            : i18n.t('adminUsers.deletePartial', { done: ids.length - failed, total: ids.length }),
        )
      }
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : i18n.t('adminUsers.deleteGenericError')
      setDeleteError(message)
      setTimeout(() => setDeleteError(null), 6000)
    },
    onSuccess: () => {
      exitSelectionMode()
    },
    onSettled: () => {
      setConfirmOpen(false)
      queryClient.invalidateQueries({ queryKey: ['admin-lawyers'] })
    },
  })

  const exitSelectionMode = (): void => {
    setSelectedIds(new Set())
  }

  const toggleSelected = (userId: string): void => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(userId)) {
        next.delete(userId)
      } else {
        next.add(userId)
      }
      return next
    })
  }

  const selectedCount = selectedIds.size
  const selectedNames = lawyers
    .filter((lawyer) => selectedIds.has(lawyer.userId))
    .map((lawyer) => lawyer.fullName ?? lawyer.email)

  const allOnPageSelected = lawyers.length > 0 && lawyers.every((lawyer) => selectedIds.has(lawyer.userId))

  const toggleSelectAll = (): void => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (allOnPageSelected) lawyers.forEach((lawyer) => next.delete(lawyer.userId))
      else lawyers.forEach((lawyer) => next.add(lawyer.userId))
      return next
    })
  }

  const columns = useMemo<DataTableColumn<LawyerProfileResponse>[]>(
    () => [
      {
        id: 'fullName',
        header: t('adminUsers.columnName'),
        alwaysVisible: true,
        sortable: true,
        width: 'minmax(0, 1.6fr)',
        value: (row) => row.fullName,
      },
      { id: 'email', header: t('adminUsers.columnEmail'), sortable: true, value: (row) => row.email },
      {
        id: 'specialization',
        header: t('adminUsers.fieldSpecialization'),
        sortable: true,
        groupable: true,
        value: (row) => row.specialization,
      },
      { id: 'phone', header: t('adminUsers.fieldPhone'), width: '11rem', value: (row) => row.phone },
      {
        id: 'telegramLinked',
        header: t('adminUsers.columnTelegram'),
        sortable: true,
        groupable: true,
        width: '8rem',
        value: (row) => row.telegramLinked,
        groupLabel: (row) => (row.telegramLinked ? t('common.yes') : t('common.no')),
      },
    ],
    [t]
  )

  return (
    <div className="p-6 lg:p-8">
      <div className="mb-8 flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold text-fg mb-1">{t('adminUsers.title')}</h1>
          <p className="text-sm text-fg-muted">
            {selectedCount > 0 ? t('adminUsers.selectPrompt') : t('adminUsers.subtitle')}
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <TableToolbar
            columns={columns}
            visibleColumnIds={preferences.visibleColumnIds}
            onToggleColumn={toggleColumn}
            groupBy={preferences.groupBy}
            onGroupByChange={setGroupBy}
            density={density}
            onDensityToggle={toggleDensity}
          />
          {selectedCount > 0 && (
            <>
              <Button variant="ghost" size="sm" onClick={exitSelectionMode}>
                {t('adminUsers.cancel')}
              </Button>
              <Button
                variant="danger"
                size="sm"
                loading={deleteMutation.isPending}
                onClick={() => setConfirmOpen(true)}
              >
                {t('adminUsers.delete')} ({selectedCount})
              </Button>
            </>
          )}
        </div>
      </div>

      <AnimatePresence>
        {deleteError && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-danger"
          >
            {deleteError}
          </motion.div>
        )}
      </AnimatePresence>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : (
        <DataTable
          rows={lawyers}
          columns={columns}
          rowId={(row) => row.userId}
          sort={preferences.sort}
          onSortChange={setSort}
          visibleColumnIds={preferences.visibleColumnIds}
          groupBy={preferences.groupBy}
          selectedIds={selectedIds}
          onToggleRow={toggleSelected}
          onToggleAll={toggleSelectAll}
          selectionLabel={(row) => row.fullName ?? row.email}
          density={density}
          emptyState={<EmptyState description={t('adminUsers.empty')} />}
        />
      )}

      <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={total} onPageChange={setPage} />

      <AnimatePresence>
        {confirmOpen && (
          <ConfirmDeleteModal
            count={selectedCount}
            names={selectedNames}
            loading={deleteMutation.isPending}
            onCancel={() => setConfirmOpen(false)}
            onConfirm={() => deleteMutation.mutate([...selectedIds])}
          />
        )}
      </AnimatePresence>
    </div>
  )
}

function ConfirmDeleteModal({
  count,
  names,
  loading,
  onCancel,
  onConfirm,
}: {
  count: number
  names: string[]
  loading: boolean
  onCancel: () => void
  onConfirm: () => void
}): JSX.Element {
  const { t } = useTranslation()
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onCancel}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.96, y: 8 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.96, y: 8 }}
        transition={{ duration: 0.15 }}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-xl border border-line bg-bg p-6 shadow-xl"
      >
        <h2 className="text-lg font-semibold text-fg mb-2">
          {count === 1 ? t('adminUsers.confirmTitleOne') : t('adminUsers.confirmTitleMany', { count })}
        </h2>
        <ul className="mb-4 max-h-40 overflow-y-auto rounded-lg border border-line divide-y divide-line text-sm">
          {names.map((name, i) => (
            <li key={`${name}-${i}`} className="px-3 py-2 text-fg truncate">
              {name}
            </li>
          ))}
        </ul>
        <p className="text-sm text-fg-muted mb-6">
          {t('adminUsers.confirmWarning')}
        </p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" size="sm" onClick={onCancel} disabled={loading}>
            {t('adminUsers.cancel')}
          </Button>
          <Button variant="danger" size="sm" loading={loading} onClick={onConfirm}>
            {t('adminUsers.delete')}
          </Button>
        </div>
      </motion.div>
    </motion.div>
  )
}
