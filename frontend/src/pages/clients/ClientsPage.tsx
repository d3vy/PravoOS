import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { clientsApi } from '../../api/clients'
import { DEFAULT_PAGE_SIZE, type Page } from '../../api/pagination'
import type { ClientResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { ClientForm } from '../../components/clients/ClientForm'

export default function ClientsPage(): JSX.Element {
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      setShowForm(false)
      setFormError(null)
    },
    onError: () => setFormError('Не удалось создать клиента. Проверьте данные и попробуйте снова.'),
  })

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Клиенты</h1>
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              Карточки клиентов: контакты, реквизиты и связанные дела
            </p>
          </div>
          <Button variant="primary" onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Отмена' : 'Новый клиент'}
          </Button>
        </div>

        <AnimatePresence>
          {showForm && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="mb-8 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border overflow-hidden"
            >
              <ClientForm
                submitLabel="Создать клиента"
                isSubmitting={createMutation.isPending}
                error={formError}
                onSubmit={(data) => createMutation.mutate(data)}
              />
            </motion.div>
          )}
        </AnimatePresence>

        {isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner size="lg" />
          </div>
        ) : clients.length === 0 ? (
          <EmptyState
            title="Пока нет клиентов"
            description="Создайте первого клиента, чтобы привязывать к нему дела и счета."
            action={{ label: 'Новый клиент', onClick: () => setShowForm(true) }}
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
                  className="block h-full p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 transition-colors"
                >
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h3 className="font-medium text-light-text dark:text-dark-text line-clamp-2 min-w-0 [overflow-wrap:anywhere]">{client.name}</h3>
                    <span className="shrink-0 text-xs px-2 py-0.5 rounded-full bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary">
                      {client.typeName}
                    </span>
                  </div>
                  {client.phone && (
                    <p className="text-sm text-light-secondary dark:text-dark-secondary">{client.phone}</p>
                  )}
                  {client.email && (
                    <p className="text-sm text-light-secondary dark:text-dark-secondary truncate">{client.email}</p>
                  )}
                  <p className="text-xs text-light-secondary dark:text-dark-secondary mt-3">
                    {client.caseCount > 0 ? `Дел: ${client.caseCount}` : 'Нет дел'}
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
