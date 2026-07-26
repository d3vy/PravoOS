import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { documentsApi } from '../../api/documents'
import type { DocumentInsightResponse } from '../../types'
import { Spinner } from '../ui/Spinner'

export function DocumentSummaryCard({ documentId }: { documentId: string }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const {
    data: insight,
    isLoading,
    isError,
  } = useQuery<DocumentInsightResponse>({
    queryKey: ['document-insight', documentId],
    queryFn: () => documentsApi.getInsight(documentId),
  })

  const regenerate = useMutation({
    mutationFn: () => documentsApi.regenerateInsight(documentId),
    onSuccess: (data) => queryClient.setQueryData(['document-insight', documentId], data),
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-6">
        <Spinner />
      </div>
    )
  }

  if (isError || !insight) {
    return <p className="text-sm text-danger py-3">{t('documentSummary.loadError')}</p>
  }

  const isPending = insight.summaryStatus === 'PENDING' || regenerate.isPending
  const hasSummary = insight.summaryStatus === 'READY'

  return (
    <div className="rounded-xl border border-line bg-surface p-4 mb-4">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <h3 className="text-sm font-semibold text-fg">{t('documentSummary.title')}</h3>
          {insight.generatedAt && (
            <p className="text-[11px] text-fg-muted mt-0.5">
              {t('documentSummary.generatedAt', {
                date: new Date(insight.generatedAt).toLocaleString(),
              })}
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={() => regenerate.mutate()}
          disabled={isPending}
          className="text-xs px-3 py-1.5 rounded-lg border border-line text-fg-muted hover:text-fg hover:border-accent transition-colors disabled:opacity-60 shrink-0"
        >
          {hasSummary ? t('documentSummary.regenerate') : t('documentSummary.generate')}
        </button>
      </div>

      {isPending ? (
        <div className="flex items-center gap-2 text-sm text-fg-muted py-2">
          <Spinner size="sm" />
          {t('documentSummary.pending')}
        </div>
      ) : hasSummary ? (
        <>
          <p className="text-sm text-fg whitespace-pre-line">{insight.summary}</p>
          {insight.keyPoints.length > 0 && (
            <ul className="mt-3 flex flex-col gap-1.5">
              {insight.keyPoints.map((point) => (
                <li key={point} className="text-sm text-fg-muted flex gap-2">
                  <span className="text-accent shrink-0">•</span>
                  <span>{point}</span>
                </li>
              ))}
            </ul>
          )}
        </>
      ) : (
        <p className="text-sm text-fg-muted">
          {insight.summaryStatus === 'FAILED'
            ? t('documentSummary.failed')
            : t('documentSummary.absent')}
        </p>
      )}

      {regenerate.isError && (
        <p className="text-sm text-danger mt-3">{t('documentSummary.regenerateError')}</p>
      )}
    </div>
  )
}
