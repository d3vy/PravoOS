import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { clientsApi } from '../../api/clients'
import type { ClientDetailResponse } from '../../types'
import { Navbar } from '../../components/layout/Navbar'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { ClientForm } from '../../components/clients/ClientForm'
import { ClientContactsSection } from '../../components/clients/ClientContactsSection'

export default function ClientDetailPage(): JSX.Element {
  const { clientId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [isEditing, setIsEditing] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const { data, isLoading } = useQuery<ClientDetailResponse>({
    queryKey: ['client', clientId],
    queryFn: () => clientsApi.get(clientId),
    enabled: clientId !== '',
  })

  const updateMutation = useMutation({
    mutationFn: (payload: Parameters<typeof clientsApi.update>[1]) => clientsApi.update(clientId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['client', clientId] })
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      setIsEditing(false)
      setEditError(null)
    },
    onError: () => setEditError('Не удалось сохранить изменения. Проверьте данные.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (cascade: boolean) => clientsApi.delete(clientId, cascade),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      navigate('/clients')
    },
    onError: () => setDeleteError('Не удалось удалить клиента. Попробуйте снова.'),
  })

  if (isLoading) {
    return (
      <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
        <Navbar />
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
        <Navbar />
        <div className="page-container py-16 text-center">
          <p className="text-light-secondary dark:text-dark-secondary mb-4">Клиент не найден</p>
          <Link to="/clients" className="text-light-accent dark:text-dark-accent text-sm">
            ← Ко всем клиентам
          </Link>
        </div>
      </div>
    )
  }

  const { client, cases } = data

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
      <div className="page-container py-8 max-w-4xl">
        <Link
          to="/clients"
          className="text-sm text-light-secondary dark:text-dark-secondary hover:text-light-accent dark:hover:text-dark-accent mb-4 inline-block"
        >
          ← Ко всем клиентам
        </Link>

        <div className="flex items-start justify-between gap-4 mb-8">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text">{client.name}</h1>
              <span className="text-xs px-2 py-0.5 rounded-full bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary">
                {client.typeName}
              </span>
            </div>
            <p className="text-xs text-light-secondary dark:text-dark-secondary">
              Добавлен {new Date(client.createdAt).toLocaleDateString('ru-RU')}
            </p>
          </div>
          {!isEditing && (
            <div className="flex gap-2 shrink-0">
              <Button variant="secondary" size="sm" onClick={() => setIsEditing(true)}>
                Редактировать
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setShowDeleteDialog(true)}>
                Удалить
              </Button>
            </div>
          )}
        </div>

        {isEditing ? (
          <section className="mb-10 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">Редактирование клиента</h2>
              <button
                onClick={() => {
                  setIsEditing(false)
                  setEditError(null)
                }}
                className="text-xs text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text"
              >
                Отмена
              </button>
            </div>
            <ClientForm
              initial={{
                name: client.name,
                type: client.type,
                phone: client.phone ?? undefined,
                email: client.email ?? undefined,
                inn: client.inn ?? undefined,
                notes: client.notes ?? undefined,
              }}
              submitLabel="Сохранить"
              isSubmitting={updateMutation.isPending}
              error={editError}
              onSubmit={(payload) => updateMutation.mutate(payload)}
            />
          </section>
        ) : (
          <section className="mb-10 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
            <dl className="grid gap-4 sm:grid-cols-2">
              <DetailRow label="Телефон" value={client.phone} />
              <DetailRow label="Email" value={client.email} />
              <DetailRow label="ИНН" value={client.inn} />
            </dl>
            {client.notes && (
              <div className="mt-4 pt-4 border-t border-light-border dark:border-dark-border">
                <dt className="text-xs font-semibold text-light-secondary dark:text-dark-secondary mb-1">Заметки</dt>
                <dd className="text-sm text-light-text dark:text-dark-text whitespace-pre-wrap">{client.notes}</dd>
              </div>
            )}
          </section>
        )}

        <ClientContactsSection clientId={clientId} />

        <section>
          <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">
            Дела клиента{' '}
            {cases.length > 0 && (
              <span className="font-normal text-light-secondary dark:text-dark-secondary">({cases.length})</span>
            )}
          </h2>
          {cases.length === 0 ? (
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              К этому клиенту пока не привязано ни одного дела.
            </p>
          ) : (
            <div className="flex flex-col gap-2">
              {cases.map((caseItem) => (
                <Link
                  key={caseItem.id}
                  to={`/cases/${caseItem.id}`}
                  className="block p-4 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 transition-colors"
                >
                  <p className="text-sm font-medium text-light-text dark:text-dark-text line-clamp-1">
                    {caseItem.title}
                  </p>
                  <p className="text-xs text-light-secondary dark:text-dark-secondary mt-1">
                    {new Date(caseItem.createdAt).toLocaleDateString('ru-RU')}
                  </p>
                </Link>
              ))}
            </div>
          )}
        </section>
      </div>

      {showDeleteDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-xl bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border p-6">
            <h3 className="text-lg font-semibold text-light-text dark:text-dark-text mb-2">Удалить клиента?</h3>
            <p className="text-sm text-light-secondary dark:text-dark-secondary mb-6">
              {cases.length > 0
                ? `У клиента ${cases.length} ${casesWord(cases.length)}. Выберите, что сделать с ними.`
                : 'Клиент будет удалён без возможности восстановления.'}
            </p>

            {deleteError && <p className="text-sm text-red-600 dark:text-red-400 mb-4">{deleteError}</p>}

            <div className="flex flex-col gap-2">
              {cases.length > 0 && (
                <>
                  <Button
                    variant="secondary"
                    loading={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(false)}
                  >
                    Удалить клиента, дела сохранить
                  </Button>
                  <Button
                    variant="primary"
                    loading={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(true)}
                  >
                    Удалить клиента и все дела
                  </Button>
                </>
              )}
              {cases.length === 0 && (
                <Button
                  variant="primary"
                  loading={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(false)}
                >
                  Удалить клиента
                </Button>
              )}
              <button
                onClick={() => {
                  setShowDeleteDialog(false)
                  setDeleteError(null)
                }}
                className="mt-1 text-sm text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text"
              >
                Отмена
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function DetailRow({ label, value }: { label: string; value: string | null }): JSX.Element {
  return (
    <div>
      <dt className="text-xs font-semibold text-light-secondary dark:text-dark-secondary mb-1">{label}</dt>
      <dd className="text-sm text-light-text dark:text-dark-text">{value ?? '—'}</dd>
    </div>
  )
}

function casesWord(count: number): string {
  const mod10 = count % 10
  const mod100 = count % 100
  if (mod10 === 1 && mod100 !== 11) return 'дело'
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return 'дела'
  return 'дел'
}
