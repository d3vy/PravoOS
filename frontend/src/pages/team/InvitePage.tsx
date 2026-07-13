import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { organizationsApi } from '../../api/organizations'
import { refreshSession } from '../../api/client'
import type { Organization } from '../../types'
import { Button } from '../../components/ui/Button'

export default function InvitePage(): JSX.Element {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const queryClient = useQueryClient()
  const [joined, setJoined] = useState<Organization | null>(null)

  const acceptMutation = useMutation({
    mutationFn: () => organizationsApi.acceptInvite(token),
    onSuccess: async (org) => {
      await refreshSession()
      queryClient.invalidateQueries({ queryKey: ['organizations'] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setJoined(org)
    },
  })

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-16 max-w-md">
        <div className="p-8 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-center">
          {joined ? (
            <>
              <h1 className="text-xl font-semibold text-light-text dark:text-dark-text mb-2">
                Вы присоединились к «{joined.name}»
              </h1>
              <p className="text-sm text-light-secondary dark:text-dark-secondary mb-6">
                Теперь вам доступны общие дела организации.
              </p>
              <Link to="/team">
                <Button variant="primary">Перейти к организации</Button>
              </Link>
            </>
          ) : !token ? (
            <p className="text-sm text-red-600 dark:text-red-400">Некорректная ссылка приглашения.</p>
          ) : (
            <>
              <h1 className="text-xl font-semibold text-light-text dark:text-dark-text mb-2">
                Приглашение в организацию
              </h1>
              <p className="text-sm text-light-secondary dark:text-dark-secondary mb-6">
                Примите приглашение, чтобы получить доступ к общим делам фирмы.
              </p>
              {acceptMutation.isError && (
                <p className="mb-4 text-sm text-red-600 dark:text-red-400">
                  Приглашение недействительно, истекло или предназначено для другого email.
                </p>
              )}
              <Button variant="primary" loading={acceptMutation.isPending} onClick={() => acceptMutation.mutate()}>
                Принять приглашение
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
