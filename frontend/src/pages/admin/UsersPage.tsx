import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { adminApi, DEFAULT_PAGE_SIZE, type Page } from '../../api/admin'
import type { LawyerProfileResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'

export default function UsersPage(): JSX.Element {
  const [selectionMode, setSelectionMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

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
            ? 'Не удалось удалить выбранных юристов.'
            : `Удалено ${ids.length - failed} из ${ids.length}. Часть юристов удалить не удалось.`,
        )
      }
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : 'Ошибка при удалении юриста'
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
    setSelectionMode(false)
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

  return (
    <div className="p-6 lg:p-8">
      <div className="mb-8 flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Юристы</h1>
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            {selectionMode ? 'Выберите юристов для удаления' : 'Активные пользователи платформы'}
          </p>
        </div>

        {lawyers.length > 0 && (
          <div className="flex items-center gap-2 shrink-0">
            {selectionMode ? (
              <>
                <Button variant="ghost" size="sm" onClick={exitSelectionMode}>
                  Отмена
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  disabled={selectedCount === 0}
                  loading={deleteMutation.isPending}
                  onClick={() => setConfirmOpen(true)}
                >
                  Удалить{selectedCount > 0 ? ` (${selectedCount})` : ''}
                </Button>
              </>
            ) : (
              <Button variant="secondary" size="sm" onClick={() => setSelectionMode(true)}>
                Выбрать
              </Button>
            )}
          </div>
        )}
      </div>

      <AnimatePresence>
        {deleteError && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-400"
          >
            {deleteError}
          </motion.div>
        )}
      </AnimatePresence>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : lawyers.length === 0 ? (
        <div className="text-center py-16 rounded-xl border border-dashed border-light-border dark:border-dark-border">
          <p className="text-light-secondary dark:text-dark-secondary text-sm">
            Нет активных юристов
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {lawyers.map((lawyer, index) => (
            <LawyerCard
              key={lawyer.userId}
              lawyer={lawyer}
              index={index}
              selectionMode={selectionMode}
              selected={selectedIds.has(lawyer.userId)}
              onToggle={() => toggleSelected(lawyer.userId)}
            />
          ))}
        </div>
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

function LawyerCard({
  lawyer,
  index,
  selectionMode,
  selected,
  onToggle,
}: {
  lawyer: LawyerProfileResponse
  index: number
  selectionMode: boolean
  selected: boolean
  onToggle: () => void
}): JSX.Element {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, delay: index * 0.04 }}
      onClick={selectionMode ? onToggle : undefined}
      className={`bg-light-surface dark:bg-dark-surface rounded-xl border p-5 transition-colors ${
        selectionMode ? 'cursor-pointer' : ''
      } ${
        selected
          ? 'border-light-accent dark:border-dark-accent ring-1 ring-light-accent dark:ring-dark-accent'
          : 'border-light-border dark:border-dark-border'
      }`}
    >
      <div className="flex flex-col sm:flex-row sm:items-center gap-4">
        {selectionMode && (
          <div
            className={`w-5 h-5 rounded-md border flex items-center justify-center shrink-0 transition-colors ${
              selected
                ? 'bg-light-accent dark:bg-dark-accent border-light-accent dark:border-dark-accent'
                : 'border-light-border dark:border-dark-border'
            }`}
            aria-hidden="true"
          >
            {selected && (
              <svg className="w-3 h-3 text-white dark:text-dark-bg" viewBox="0 0 20 20" fill="currentColor">
                <path
                  fillRule="evenodd"
                  d="M16.704 5.29a1 1 0 010 1.42l-7.5 7.5a1 1 0 01-1.42 0l-3.5-3.5a1 1 0 011.42-1.42l2.79 2.79 6.79-6.79a1 1 0 011.42 0z"
                  clipRule="evenodd"
                />
              </svg>
            )}
          </div>
        )}

        <div className="w-10 h-10 rounded-full bg-light-accent/10 dark:bg-dark-accent/10 flex items-center justify-center shrink-0">
          <span className="text-sm font-semibold text-light-accent dark:text-dark-accent">
            {(lawyer.fullName ?? lawyer.email).charAt(0).toUpperCase()}
          </span>
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-semibold text-light-text dark:text-dark-text">
            {lawyer.fullName ?? '—'}
          </p>
          <p className="text-sm text-light-secondary dark:text-dark-secondary">{lawyer.email}</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-1 text-sm">
          <InfoField label="Специализация" value={lawyer.specialization} />
          <InfoField label="Телефон" value={lawyer.phone} />
        </div>
      </div>
    </motion.div>
  )
}

function InfoField({ label, value }: { label: string; value: string | null }): JSX.Element {
  return (
    <div>
      <span className="text-xs text-light-secondary dark:text-dark-secondary">{label}</span>
      <p className="text-light-text dark:text-dark-text truncate">{value ?? '—'}</p>
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
        className="w-full max-w-md rounded-xl border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg p-6 shadow-xl"
      >
        <h2 className="text-lg font-semibold text-light-text dark:text-dark-text mb-2">
          Удалить {count === 1 ? 'юриста' : `юристов (${count})`}?
        </h2>
        <ul className="mb-4 max-h-40 overflow-y-auto rounded-lg border border-light-border dark:border-dark-border divide-y divide-light-border dark:divide-dark-border text-sm">
          {names.map((name, i) => (
            <li key={`${name}-${i}`} className="px-3 py-2 text-light-text dark:text-dark-text truncate">
              {name}
            </li>
          ))}
        </ul>
        <p className="text-sm text-light-secondary dark:text-dark-secondary mb-6">
          Учётные записи, персональные данные, а также дела, черновики и чаты юриста будут удалены
          без возможности восстановления.
        </p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" size="sm" onClick={onCancel} disabled={loading}>
            Отмена
          </Button>
          <Button variant="danger" size="sm" loading={loading} onClick={onConfirm}>
            Удалить
          </Button>
        </div>
      </motion.div>
    </motion.div>
  )
}
