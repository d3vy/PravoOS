import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { clientsApi } from '../../api/clients'
import { DEFAULT_PAGE_SIZE, type Page } from '../../api/pagination'
import type { ClientResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { SkeletonCardGrid } from '../../components/ui/Skeleton'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { Modal } from '../../components/ui/Modal'
import { ClientForm } from '../../components/clients/ClientForm'
import { useToast } from '../../hooks/useToast'

export default function ClientsPage(): JSX.Element {
  const { t } = useTranslation()
  const toast = useToast()
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()

  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setShowForm(true)
      searchParams.delete('new')
      setSearchParams(searchParams, { replace: true })
    }
  }, [searchParams, setSearchParams])

  const { data: clientsPage, isLoading } = useQuery<Page<ClientResponse>>({
    queryKey: ['clients', page],
    queryFn: () => clientsApi.list(page),
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

        {isLoading ? (
          <SkeletonCardGrid />
        ) : clients.length === 0 ? (
          <EmptyState
            title={t('clients.emptyTitle')}
            description={t('clients.emptyDescription')}
            action={{ label: t('clients.newClient'), onClick: () => setShowForm(true) }}
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {clients.map((client, index) => (
              <motion.div
                key={client.id}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2, delay: index * 0.03 }}
              >
                <Link
                  to={`/clients/${client.id}`}
                  className="block h-full p-5 rounded-xl bg-surface border border-line hover:border-accent/50 transition-colors"
                >
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h3 className="font-medium text-fg line-clamp-2 min-w-0 [overflow-wrap:anywhere]">{client.name}</h3>
                    <span className="shrink-0 text-xs px-2 py-0.5 rounded-full bg-bg border border-line text-fg-muted">
                      {client.typeName}
                    </span>
                  </div>
                  {client.phone && (
                    <p className="text-sm text-fg-muted">{client.phone}</p>
                  )}
                  {client.email && (
                    <p className="text-sm text-fg-muted truncate">{client.email}</p>
                  )}
                  <p className="text-xs text-fg-muted mt-3">
                    {client.caseCount > 0 ? t('clients.caseCount', { count: client.caseCount }) : t('clients.noCases')}
                  </p>
                </Link>
              </motion.div>
            ))}
          </div>
        )}

        {!isLoading && (
          <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={total} onPageChange={setPage} />
        )}
      </div>
    </div>
  )
}
