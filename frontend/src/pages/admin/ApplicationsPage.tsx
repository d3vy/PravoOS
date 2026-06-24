import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import axios from 'axios'
import { adminApi, DEFAULT_PAGE_SIZE, type Page } from '../../api/admin'
import type { ApplicationResponse } from '../../types'
import { ApplicationStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'

type Tab = 'all' | 'pending'

export default function ApplicationsPage(): JSX.Element {
  const [activeTab, setActiveTab] = useState<Tab>('all')
  const [page, setPage] = useState(0)
  const [processingId, setProcessingId] = useState<string | null>(null)
  const [approveError, setApproveError] = useState<{ id: string; message: string } | null>(null)
  const queryClient = useQueryClient()

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
          : 'Ошибка при одобрении заявки'
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
          : 'Ошибка при одобрении заявки'
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

  return (
    <div className="p-6 lg:p-8">
      <div className="mb-8">
        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Заявки</h1>
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Управление заявками на доступ к платформе
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 p-1 bg-light-bg dark:bg-dark-bg rounded-lg w-fit mb-6 border border-light-border dark:border-dark-border">
        <button
          onClick={() => changeTab('all')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            activeTab === 'all'
              ? 'bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text shadow-sm'
              : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
          }`}
        >
          Все
        </button>
        <button
          onClick={() => changeTab('pending')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors flex items-center gap-2 ${
            activeTab === 'pending'
              ? 'bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text shadow-sm'
              : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
          }`}
        >
          Ожидают
          {pendingCount > 0 && (
            <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-400 text-xs font-semibold flex items-center justify-center">
              {pendingCount}
            </span>
          )}
        </button>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : displayedApplications.length === 0 ? (
        <div className="text-center py-16">
          <p className="text-light-secondary dark:text-dark-secondary">
            {activeTab === 'pending' ? 'Нет заявок, ожидающих рассмотрения' : 'Заявок пока нет'}
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <AnimatePresence>
            {displayedApplications.map((app, index) => (
              <ApplicationCard
                key={app.id}
                application={app}
                index={index}
                onApprove={() => approveMutation.mutate(app.id)}
                onApproveForce={() => forceApproveMutation.mutate(app.id)}
                onReject={() => rejectMutation.mutate(app.id)}
                isProcessing={processingId === app.id}
                approveErrorMessage={approveError?.id === app.id ? approveError.message : null}
              />
            ))}
          </AnimatePresence>
        </div>
      )}

      <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={displayedTotal} onPageChange={setPage} />
    </div>
  )
}

interface ApplicationCardProps {
  application: ApplicationResponse
  index: number
  onApprove: () => void
  onApproveForce: () => void
  onReject: () => void
  isProcessing: boolean
  approveErrorMessage: string | null
}

function ApplicationCard({
  application,
  index,
  onApprove,
  onApproveForce,
  onReject,
  isProcessing,
  approveErrorMessage,
}: ApplicationCardProps): JSX.Element {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.98 }}
      transition={{ duration: 0.2, delay: index * 0.04 }}
      className="bg-light-surface dark:bg-dark-surface rounded-xl border border-light-border dark:border-dark-border p-5"
    >
      <div className="flex flex-col sm:flex-row sm:items-start gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-3 mb-1">
            <h3 className="font-semibold text-light-text dark:text-dark-text">
              {application.fullName}
            </h3>
            <ApplicationStatusBadge status={application.status} />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-x-6 gap-y-1 mt-3">
            <div>
              <span className="text-xs text-light-secondary dark:text-dark-secondary">Email</span>
              <div className="flex items-center gap-1.5">
                <p className="text-sm text-light-text dark:text-dark-text truncate">{application.email}</p>
                {application.emailVerified ? (
                  <span className="text-xs text-emerald-600 dark:text-emerald-400 shrink-0">✓</span>
                ) : (
                  <span className="text-xs text-amber-500 dark:text-amber-400 shrink-0" title="Email не подтверждён">!</span>
                )}
              </div>
            </div>
            <InfoField label="Телефон" value={application.phone} />
            <InfoField label="Номер адвоката" value={application.barNumber} />
            <InfoField label="Специализация" value={application.specialization} />
          </div>

          <p className="text-xs text-light-secondary dark:text-dark-secondary mt-3">
            Подана: {new Date(application.submittedAt).toLocaleString('ru-RU')}
            {application.reviewedAt && (
              <> · Рассмотрена: {new Date(application.reviewedAt).toLocaleString('ru-RU')}</>
            )}
          </p>

          <AnimatePresence>
            {approveErrorMessage && (
              <motion.p
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
                className="mt-2 text-xs text-amber-600 dark:text-amber-400"
              >
                {approveErrorMessage}
              </motion.p>
            )}
          </AnimatePresence>
        </div>

        {application.status === 'PENDING' && (
          <div className="flex flex-col items-stretch gap-2 shrink-0">
            <div className="flex gap-2">
              <Button
                variant="primary"
                size="sm"
                onClick={onApprove}
                loading={isProcessing}
                disabled={isProcessing}
              >
                Одобрить
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={onReject}
                loading={isProcessing}
                disabled={isProcessing}
              >
                Отклонить
              </Button>
            </div>
            {!application.emailVerified && (
              <Button
                variant="secondary"
                size="sm"
                onClick={onApproveForce}
                loading={isProcessing}
                disabled={isProcessing}
                title="Одобрить без подтверждения email — почта будет помечена подтверждённой"
              >
                Одобрить без подтверждения почты
              </Button>
            )}
          </div>
        )}
      </div>
    </motion.div>
  )
}

function InfoField({ label, value }: { label: string; value: string }): JSX.Element {
  return (
    <div>
      <span className="text-xs text-light-secondary dark:text-dark-secondary">{label}</span>
      <p className="text-sm text-light-text dark:text-dark-text truncate">{value}</p>
    </div>
  )
}
