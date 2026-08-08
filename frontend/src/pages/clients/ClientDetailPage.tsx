import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { clientsApi } from '../../api/clients'
import type { ClientDetailResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { ClientForm } from '../../components/clients/ClientForm'
import { ClientContactsSection } from '../../components/clients/ClientContactsSection'
import { ClientEmailSection } from '../../components/clients/ClientEmailSection'
import { ClientPortalSection } from '../../components/clients/ClientPortalSection'
import { useRecentEntitiesStore } from '../../store/recentEntitiesStore'
import { PageHeader } from '../../components/ui/PageHeader'

export default function ClientDetailPage(): JSX.Element {
  const { t } = useTranslation()
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

  const recordRecentEntity = useRecentEntitiesStore((state) => state.record)
  useEffect(() => {
    if (!data) return
    recordRecentEntity({
      type: 'client',
      id: data.client.id,
      label: data.client.name,
      subtitle: null,
    })
  }, [data, recordRecentEntity])

  const updateMutation = useMutation({
    mutationFn: (payload: Parameters<typeof clientsApi.update>[1]) => clientsApi.update(clientId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['client', clientId] })
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      setIsEditing(false)
      setEditError(null)
    },
    onError: () => setEditError(t('clientDetail.updateError')),
  })

  const deleteMutation = useMutation({
    mutationFn: (cascade: boolean) => clientsApi.delete(clientId, cascade),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      navigate('/clients')
    },
    onError: () => setDeleteError(t('clientDetail.deleteError')),
  })

  if (isLoading) {
    return (
      <div className="bg-bg">
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="bg-bg">
        <div className="page-container py-16 text-center">
          <p className="text-fg-muted mb-4">{t('clientDetail.notFound')}</p>
          <Link to="/clients" className="text-accent text-sm">
            {t('clientDetail.backToClients')}
          </Link>
        </div>
      </div>
    )
  }

  const { client, cases } = data

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-4xl">
        <PageHeader
          breadcrumbs={[
            { label: t('nav.clients'), to: '/clients' },
            { label: client.name },
          ]}
          title={client.name}
          titleSuffix={
            <span className="text-xs px-2 py-0.5 rounded-full bg-surface border border-line text-fg-muted">
              {client.typeName}
            </span>
          }
          description={t('clientDetail.addedOn', { date: new Date(client.createdAt).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US') })}
          actions={
            !isEditing && (
              <>
                <Button variant="secondary" size="sm" onClick={() => setIsEditing(true)}>
                  {t('clientDetail.edit')}
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setShowDeleteDialog(true)}>
                  {t('clientDetail.delete')}
                </Button>
              </>
            )
          }
        />

        {isEditing ? (
          <section className="mb-10 p-6 rounded-2xl bg-surface border border-line">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-semibold text-fg">{t('clientDetail.editingTitle')}</h2>
              <button
                onClick={() => {
                  setIsEditing(false)
                  setEditError(null)
                }}
                className="text-xs text-fg-muted hover:text-fg"
              >
                {t('clientDetail.cancel')}
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
              excludeClientId={client.id}
              submitLabel={t('clientDetail.save')}
              isSubmitting={updateMutation.isPending}
              error={editError}
              onSubmit={(payload) => updateMutation.mutate(payload)}
            />
          </section>
        ) : (
          <section className="mb-10 p-6 rounded-2xl bg-surface border border-line">
            <dl className="grid gap-4 sm:grid-cols-2">
              <DetailRow label={t('clientDetail.phone')} value={client.phone} />
              <DetailRow label="Email" value={client.email} />
              <DetailRow label={t('clientDetail.inn')} value={client.inn} />
            </dl>
            {client.notes && (
              <div className="mt-4 pt-4 border-t border-line">
                <dt className="text-xs font-semibold text-fg-muted mb-1">{t('clientDetail.notes')}</dt>
                <dd className="text-sm text-fg whitespace-pre-wrap">{client.notes}</dd>
              </div>
            )}
          </section>
        )}

        <ClientPortalSection clientId={clientId} email={client.email} />

        <ClientContactsSection clientId={clientId} />

        <ClientEmailSection clientId={clientId} />

        <section>
          <h2 className="text-sm font-semibold text-fg mb-3">
            {t('clientDetail.clientCases')}{' '}
            {cases.length > 0 && (
              <span className="font-normal text-fg-muted">({cases.length})</span>
            )}
          </h2>
          {cases.length === 0 ? (
            <p className="text-sm text-fg-muted">
              {t('clientDetail.noCases')}
            </p>
          ) : (
            <div className="flex flex-col gap-2">
              {cases.map((caseItem) => (
                <Link
                  key={caseItem.id}
                  to={`/cases/${caseItem.id}`}
                  className="block p-4 rounded-lg bg-surface border border-line hover:border-accent/50 transition-colors"
                >
                  <p className="text-sm font-medium text-fg line-clamp-1">
                    {caseItem.title}
                  </p>
                  <p className="text-xs text-fg-muted mt-1">
                    {new Date(caseItem.createdAt).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')}
                  </p>
                </Link>
              ))}
            </div>
          )}
        </section>
      </div>

      {showDeleteDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-xl bg-bg border border-line p-6">
            <h3 className="text-lg font-semibold text-fg mb-2">{t('clientDetail.deleteTitle')}</h3>
            <p className="text-sm text-fg-muted mb-6">
              {cases.length > 0
                ? t('clientDetail.deleteWithCases', { count: cases.length })
                : t('clientDetail.deleteNoCases')}
            </p>

            {deleteError && <p className="text-sm text-danger mb-4">{deleteError}</p>}

            <div className="flex flex-col gap-2">
              {cases.length > 0 && (
                <>
                  <Button
                    variant="secondary"
                    loading={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(false)}
                  >
                    {t('clientDetail.deleteKeepCases')}
                  </Button>
                  <Button
                    variant="primary"
                    loading={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(true)}
                  >
                    {t('clientDetail.deleteAllCases')}
                  </Button>
                </>
              )}
              {cases.length === 0 && (
                <Button
                  variant="primary"
                  loading={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(false)}
                >
                  {t('clientDetail.deleteClient')}
                </Button>
              )}
              <button
                onClick={() => {
                  setShowDeleteDialog(false)
                  setDeleteError(null)
                }}
                className="mt-1 text-sm text-fg-muted hover:text-fg"
              >
                {t('clientDetail.cancel')}
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
      <dt className="text-xs font-semibold text-fg-muted mb-1">{label}</dt>
      <dd className="text-sm text-fg">{value ?? '—'}</dd>
    </div>
  )
}
