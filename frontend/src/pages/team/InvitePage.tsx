import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { organizationsApi } from '../../api/organizations'
import { refreshSession } from '../../api/client'
import type { Organization } from '../../types'
import { Button } from '../../components/ui/Button'

export default function InvitePage(): JSX.Element {
  const { t } = useTranslation()
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
    <div className="bg-bg">
      <div className="page-container py-16 max-w-md">
        <div className="p-8 rounded-2xl bg-surface border border-line text-center">
          {joined ? (
            <>
              <h1 className="text-xl font-semibold text-fg mb-2">
                {t('invite.joinedTitle', { name: joined.name })}
              </h1>
              <p className="text-sm text-fg-muted mb-6">
                {t('invite.joinedText')}
              </p>
              <Link to="/team">
                <Button variant="primary">{t('invite.goToOrg')}</Button>
              </Link>
            </>
          ) : !token ? (
            <p className="text-sm text-danger">{t('invite.badLink')}</p>
          ) : (
            <>
              <h1 className="text-xl font-semibold text-fg mb-2">
                {t('invite.title')}
              </h1>
              <p className="text-sm text-fg-muted mb-6">
                {t('invite.text')}
              </p>
              {acceptMutation.isError && (
                <p className="mb-4 text-sm text-danger">
                  {t('invite.error')}
                </p>
              )}
              <Button variant="primary" loading={acceptMutation.isPending} onClick={() => acceptMutation.mutate()}>
                {t('invite.accept')}
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
