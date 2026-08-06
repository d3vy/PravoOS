import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { PortalLayout } from '../../components/layout/PortalLayout'
import { CaseStatusBadge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { EmptyState } from '../../components/ui/EmptyState'
import { portalApi } from '../../api/portal'
import i18n from '../../i18n'
import type { PortalCaseResponse } from '../../types'
import { PageHeader } from '../../components/ui/PageHeader'

function formatDate(value: string | null): string {
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  return value ? new Date(value).toLocaleDateString(locale) : '—'
}

export default function PortalCasesPage(): JSX.Element {
  const { t } = useTranslation()
  const { data: cases = [], isLoading, isError } = useQuery<PortalCaseResponse[]>({
    queryKey: ['portal', 'cases'],
    queryFn: portalApi.listCases,
  })

  return (
    <PortalLayout>
      <PageHeader size="md" title={t('portalCases.title')} className="mb-6" />

      {isLoading && (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      )}

      {isError && (
        <div className="card-elevated rounded-xl p-8 text-center text-fg-muted">
          {t('portalCases.loadError')}
        </div>
      )}

      {!isLoading && !isError && cases.length === 0 && (
        <EmptyState illustration="cases" description={t('portalCases.empty')} />
      )}

      {!isLoading && !isError && cases.length > 0 && (
        <ul className="space-y-3">
          {cases.map((caseItem) => (
            <li key={caseItem.id}>
              <Link
                to={`/portal/cases/${caseItem.id}`}
                className="card-elevated rounded-xl p-5 flex items-start justify-between gap-4 hover:shadow-md transition-shadow"
              >
                <div className="min-w-0">
                  <p className="font-medium text-fg truncate">
                    {caseItem.title}
                  </p>
                  {caseItem.nextHearingDate && (
                    <p className="text-sm text-fg-muted mt-1">
                      {t('portalCases.nextHearing', { date: formatDate(caseItem.nextHearingDate) })}
                    </p>
                  )}
                </div>
                <CaseStatusBadge status={caseItem.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </PortalLayout>
  )
}
