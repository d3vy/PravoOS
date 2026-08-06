import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { emailsApi } from '../../api/emails'
import { clientsApi } from '../../api/clients'
import { casesApi } from '../../api/cases'
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, type Page } from '../../api/pagination'
import type { CaseResponse, ClientResponse, EmailMessageResponse } from '../../types'
import { Button } from '../ui/Button'
import { SkeletonList } from '../ui/Skeleton'
import { EmptyState } from '../ui/EmptyState'
import { Pagination } from '../ui/Pagination'
import { EmailMessageCard } from './EmailMessageCard'

export function UnlinkedEmailsSection(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery<Page<EmailMessageResponse>>({
    queryKey: ['emails-unlinked', page],
    queryFn: () => emailsApi.unlinked(page, DEFAULT_PAGE_SIZE),
    placeholderData: keepPreviousData,
  })

  const { data: clients = [] } = useQuery<ClientResponse[]>({
    queryKey: ['clients-all'],
    queryFn: clientsApi.getAll,
  })

  const { data: casesPage } = useQuery<Page<CaseResponse>>({
    queryKey: ['cases-all-for-linking'],
    queryFn: () => casesApi.list(undefined, undefined, 0, MAX_PAGE_SIZE),
  })
  const cases = casesPage?.items ?? []

  const linkMutation = useMutation({
    mutationFn: ({ emailId, caseId, clientId }: { emailId: string; caseId?: string; clientId?: string }) =>
      emailsApi.link(emailId, { caseId: caseId || undefined, clientId: clientId || undefined }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['emails-unlinked'] })
    },
  })

  const emails = data?.items ?? []
  const total = data?.total ?? 0

  if (isLoading) {
    return <SkeletonList count={4} />
  }

  if (emails.length === 0) {
    return <EmptyState illustration="mail" description={t('emails.unlinkedEmpty')} />
  }

  return (
    <div>
      <div className="flex flex-col gap-3">
        {emails.map((message) => (
          <LinkRow
            key={message.id}
            message={message}
            clients={clients}
            cases={cases}
            isLinking={linkMutation.isPending && linkMutation.variables?.emailId === message.id}
            onLink={(caseId, clientId) => linkMutation.mutate({ emailId: message.id, caseId, clientId })}
          />
        ))}
      </div>
      <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={total} onPageChange={setPage} />
    </div>
  )
}

function LinkRow({
  message,
  clients,
  cases,
  isLinking,
  onLink,
}: {
  message: EmailMessageResponse
  clients: ClientResponse[]
  cases: CaseResponse[]
  isLinking: boolean
  onLink: (caseId?: string, clientId?: string) => void
}): JSX.Element {
  const { t } = useTranslation()
  const [clientId, setClientId] = useState('')
  const [caseId, setCaseId] = useState('')

  const casesForClient = clientId ? cases.filter((c) => c.clientId === clientId) : cases

  return (
    <EmailMessageCard
      message={message}
      actions={
        <div className="flex flex-wrap items-center gap-2 w-full mt-1">
          <select
            value={clientId}
            onChange={(e) => {
              setClientId(e.target.value)
              setCaseId('')
            }}
            className="px-2.5 py-1.5 rounded-lg border border-line bg-bg text-fg text-xs focus:outline-none focus:ring-2 focus:ring-accent"
          >
            <option value="">{t('emails.selectClient')}</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </select>
          <select
            value={caseId}
            onChange={(e) => setCaseId(e.target.value)}
            className="px-2.5 py-1.5 rounded-lg border border-line bg-bg text-fg text-xs focus:outline-none focus:ring-2 focus:ring-accent"
          >
            <option value="">{t('emails.selectCase')}</option>
            {casesForClient.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
          <Button
            size="sm"
            variant="secondary"
            disabled={!clientId && !caseId}
            loading={isLinking}
            onClick={() => onLink(caseId || undefined, clientId || undefined)}
          >
            {t('emails.linkSubmit')}
          </Button>
        </div>
      }
    />
  )
}
