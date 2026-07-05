import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { PortalLayout } from '../../components/layout/PortalLayout'
import { CaseStatusBadge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { portalApi } from '../../api/portal'
import type { PortalCaseResponse } from '../../types'

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleDateString('ru-RU') : '—'
}

export default function PortalCasesPage(): JSX.Element {
  const { data: cases = [], isLoading, isError } = useQuery<PortalCaseResponse[]>({
    queryKey: ['portal', 'cases'],
    queryFn: portalApi.listCases,
  })

  return (
    <PortalLayout>
      <h1 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-6">Мои дела</h1>

      {isLoading && (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      )}

      {isError && (
        <div className="card-elevated rounded-xl p-8 text-center text-light-secondary dark:text-dark-secondary">
          Не удалось загрузить дела. Попробуйте обновить страницу.
        </div>
      )}

      {!isLoading && !isError && cases.length === 0 && (
        <div className="card-elevated rounded-xl p-10 text-center text-light-secondary dark:text-dark-secondary">
          У вас пока нет дел. Ваш юрист добавит их сюда.
        </div>
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
                  <p className="font-medium text-light-text dark:text-dark-text truncate">
                    {caseItem.title}
                  </p>
                  {caseItem.nextHearingDate && (
                    <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
                      Ближайшее заседание: {formatDate(caseItem.nextHearingDate)}
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
