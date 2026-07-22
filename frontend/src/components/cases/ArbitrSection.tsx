import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { CaseHearingEvent, CaseResponse } from '../../types'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'

export function ArbitrSection({ caseItem }: { caseItem: CaseResponse }): JSX.Element {
  const { t } = useTranslation()
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
        <h2 className="text-sm font-semibold text-fg">
          {t('arbitr.title')}
        </h2>
        {hasNumber && (
          <Button
            variant="ghost"
            size="sm"
            loading={syncMutation.isPending}
            onClick={() => syncMutation.mutate()}
          >
            {t('arbitr.syncButton')}
          </Button>
        )}
      </div>

      {!hasNumber ? (
        <p className="text-sm text-fg-muted">
          {t('arbitr.noNumberHint')}
        </p>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-3 mb-3 text-sm">
            <span className="text-fg-muted">
              {t('arbitr.numberLabel')} <span className="text-fg font-medium">{caseItem.arbitrCaseNumber}</span>
            </span>
            {caseItem.arbitrCardUrl && (
              <a
                href={caseItem.arbitrCardUrl}
                target="_blank"
                rel="noreferrer"
                className="text-accent hover:underline"
              >
                {t('arbitr.openOnKad')}
              </a>
            )}
          </div>

          {syncMutation.isError && (
            <p className="text-sm text-danger mb-3">
              {t('arbitr.syncError')}
            </p>
          )}

          {isLoading ? (
            <Spinner size="md" />
          ) : hearings.length === 0 ? (
            <p className="text-sm text-fg-muted">
              {t('arbitr.emptyEvents')}
            </p>
          ) : (
            <div className="flex flex-col gap-2">
              {hearings.map((event) => (
                <div
                  key={event.id}
                  className="p-3 rounded-lg bg-surface border border-line"
                >
                  <div className="flex items-center justify-between gap-3 mb-0.5">
                    <span className="text-sm font-medium text-fg">
                      {event.eventType ?? t('arbitr.eventFallback')}
                    </span>
                    <span className="text-xs text-fg-muted shrink-0">
                      {event.eventDate ? new Date(event.eventDate).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US') : ''}
                    </span>
                  </div>
                  {event.description && (
                    <p className="text-xs text-fg-muted">{event.description}</p>
                  )}
                  {event.courtName && (
                    <p className="text-xs text-fg-muted mt-0.5">{event.courtName}</p>
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
