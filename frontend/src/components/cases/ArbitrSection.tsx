import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { CaseHearingEvent, CaseResponse } from '../../types'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'

export function ArbitrSection({ caseItem }: { caseItem: CaseResponse }): JSX.Element {
  const queryClient = useQueryClient()
  const hasNumber = Boolean(caseItem.arbitrCaseNumber)

  const { data: hearings = [], isLoading } = useQuery<CaseHearingEvent[]>({
    queryKey: ['case-hearings', caseItem.id],
    queryFn: () => casesApi.getHearings(caseItem.id),
    enabled: hasNumber,
  })

  const syncMutation = useMutation({
    mutationFn: () => casesApi.syncArbitr(caseItem.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-hearings', caseItem.id] })
      queryClient.invalidateQueries({ queryKey: ['case', caseItem.id] })
    },
  })

  return (
    <section className="mb-10">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">
          События по делу (КАД.Арбитр)
        </h2>
        {hasNumber && (
          <Button
            variant="ghost"
            size="sm"
            loading={syncMutation.isPending}
            onClick={() => syncMutation.mutate()}
          >
            Обновить из КАД
          </Button>
        )}
      </div>

      {!hasNumber ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Укажите номер дела в КАД.Арбитр в карточке дела, чтобы отслеживать заседания и события.
        </p>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-3 mb-3 text-sm">
            <span className="text-light-secondary dark:text-dark-secondary">
              Номер: <span className="text-light-text dark:text-dark-text font-medium">{caseItem.arbitrCaseNumber}</span>
            </span>
            {caseItem.arbitrCardUrl && (
              <a
                href={caseItem.arbitrCardUrl}
                target="_blank"
                rel="noreferrer"
                className="text-light-accent dark:text-dark-accent hover:underline"
              >
                Открыть на kad.arbitr.ru ↗
              </a>
            )}
          </div>

          {syncMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400 mb-3">
              Не удалось обновить данные из КАД. Попробуйте позже.
            </p>
          )}

          {isLoading ? (
            <Spinner size="md" />
          ) : hearings.length === 0 ? (
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              Событий пока нет. Нажмите «Обновить из КАД».
            </p>
          ) : (
            <div className="flex flex-col gap-2">
              {hearings.map((event) => (
                <div
                  key={event.id}
                  className="p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
                >
                  <div className="flex items-center justify-between gap-3 mb-0.5">
                    <span className="text-sm font-medium text-light-text dark:text-dark-text">
                      {event.eventType ?? 'Событие'}
                    </span>
                    <span className="text-xs text-light-secondary dark:text-dark-secondary shrink-0">
                      {event.eventDate ? new Date(event.eventDate).toLocaleDateString('ru-RU') : ''}
                    </span>
                  </div>
                  {event.description && (
                    <p className="text-xs text-light-secondary dark:text-dark-secondary">{event.description}</p>
                  )}
                  {event.courtName && (
                    <p className="text-xs text-light-secondary dark:text-dark-secondary mt-0.5">{event.courtName}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
