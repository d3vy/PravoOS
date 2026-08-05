import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { emailsApi } from '../../api/emails'
import type { EmailMessageResponse } from '../../types'
import { Spinner } from '../ui/Spinner'
import { Button } from '../ui/Button'
import { EmailMessageCard } from '../mailboxes/EmailMessageCard'

export function ClientEmailSection({ clientId }: { clientId: string }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data: emails = [], isLoading } = useQuery<EmailMessageResponse[]>({
    queryKey: ['client-emails', clientId],
    queryFn: () => emailsApi.byClient(clientId),
    enabled: clientId !== '',
  })

  const unlinkMutation = useMutation({
    mutationFn: (emailId: string) => emailsApi.unlink(emailId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['client-emails', clientId] })
    },
  })

  return (
    <section className="mb-10">
      <h2 className="text-sm font-semibold text-fg mb-3">
        {t('emails.clientSectionTitle')}
        {emails.length > 0 && <span className="font-normal text-fg-muted"> ({emails.length})</span>}
      </h2>

      {isLoading ? (
        <div className="flex justify-center py-6">
          <Spinner />
        </div>
      ) : emails.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('emails.clientSectionEmpty')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          {emails.map((message) => (
            <EmailMessageCard
              key={message.id}
              message={message}
              actions={
                <Button
                  size="sm"
                  variant="ghost"
                  loading={unlinkMutation.isPending && unlinkMutation.variables === message.id}
                  onClick={() => unlinkMutation.mutate(message.id)}
                >
                  {t('emails.unlinkButton')}
                </Button>
              }
            />
          ))}
        </div>
      )}
    </section>
  )
}
