import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { clientsApi } from '../../api/clients'
import type { PortalInviteStatusResponse } from '../../types'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'

interface ClientPortalSectionProps {
  clientId: string
  email: string | null
}

export function ClientPortalSection({ clientId, email }: ClientPortalSectionProps): JSX.Element {
  const queryClient = useQueryClient()
  const [actionError, setActionError] = useState<string | null>(null)

  const { data, isLoading } = useQuery<PortalInviteStatusResponse>({
    queryKey: ['client-portal', clientId],
    queryFn: () => clientsApi.getPortalStatus(clientId),
    enabled: clientId !== '',
  })

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey: ['client-portal', clientId] })
  }

  const inviteMutation = useMutation({
    mutationFn: () => clientsApi.invitePortal(clientId),
    onSuccess: () => {
      setActionError(null)
      invalidate()
    },
    onError: () => setActionError('Не удалось отправить приглашение. Попробуйте снова.'),
  })

  const revokeMutation = useMutation({
    mutationFn: () => clientsApi.revokePortal(clientId),
    onSuccess: () => {
      setActionError(null)
      invalidate()
    },
    onError: () => setActionError('Не удалось отозвать доступ. Попробуйте снова.'),
  })

  const isBusy = inviteMutation.isPending || revokeMutation.isPending
  const hasEmail = email !== null && email.trim() !== ''

  return (
    <section className="mb-10 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <div className="flex items-center justify-between gap-4 mb-2">
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">Клиентский портал</h2>
        {!isLoading && data && <StatusBadge status={data.status} />}
      </div>

      {isLoading ? (
        <div className="py-2">
          <Spinner size="sm" />
        </div>
      ) : (
        <>
          <p className="text-sm text-light-secondary dark:text-dark-secondary mb-4">
            {renderDescription(data?.status ?? 'NONE', data?.expiresAt ?? null, hasEmail)}
          </p>

          {actionError && <p className="text-sm text-red-600 dark:text-red-400 mb-3">{actionError}</p>}

          <div className="flex flex-wrap gap-2">
            {data?.status === 'ACCEPTED' ? (
              <Button
                variant="ghost"
                size="sm"
                loading={revokeMutation.isPending}
                disabled={isBusy}
                onClick={() => {
                  if (window.confirm('Отозвать доступ клиента к порталу? Активные сессии будут завершены.')) {
                    revokeMutation.mutate()
                  }
                }}
              >
                Отозвать доступ
              </Button>
            ) : data?.status === 'PENDING' ? (
              <>
                <Button
                  variant="secondary"
                  size="sm"
                  loading={inviteMutation.isPending}
                  disabled={isBusy || !hasEmail}
                  onClick={() => inviteMutation.mutate()}
                >
                  Отправить повторно
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  loading={revokeMutation.isPending}
                  disabled={isBusy}
                  onClick={() => revokeMutation.mutate()}
                >
                  Отозвать
                </Button>
              </>
            ) : (
              <Button
                variant="primary"
                size="sm"
                loading={inviteMutation.isPending}
                disabled={isBusy || !hasEmail}
                onClick={() => inviteMutation.mutate()}
              >
                Пригласить в портал
              </Button>
            )}
          </div>
        </>
      )}
    </section>
  )
}

function StatusBadge({ status }: { status: PortalInviteStatusResponse['status'] }): JSX.Element | null {
  if (status === 'ACCEPTED') {
    return (
      <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400">
        Есть доступ
      </span>
    )
  }
  if (status === 'PENDING') {
    return (
      <span className="text-xs px-2 py-0.5 rounded-full bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400">
        Приглашён
      </span>
    )
  }
  return null
}

function renderDescription(
  status: PortalInviteStatusResponse['status'],
  expiresAt: string | null,
  hasEmail: boolean,
): string {
  if (status === 'ACCEPTED') {
    return 'Клиент подтвердил приглашение и имеет доступ к порталу.'
  }
  if (status === 'PENDING') {
    const until = expiresAt ? ` Ссылка действительна до ${new Date(expiresAt).toLocaleDateString('ru-RU')}.` : ''
    return `Приглашение отправлено, ожидает подтверждения клиентом.${until}`
  }
  if (!hasEmail) {
    return 'Добавьте email клиенту, чтобы отправить приглашение в портал.'
  }
  return 'Клиент ещё не приглашён. Отправьте ссылку-приглашение для доступа к порталу.'
}
