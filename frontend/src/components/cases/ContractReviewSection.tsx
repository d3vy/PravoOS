import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { contractReviewsApi } from '../../api/contractReviews'
import type { ContractReviewDto, DocumentResponse } from '../../types'
import { Button } from '../ui/Button'
import { RISK_LEVEL_META, riskScoreTone } from './caseFormatting'

export function ContractReviewSection({ caseId, documents }: { caseId: string; documents: DocumentResponse[] }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [selectedDocId, setSelectedDocId] = useState('')
  const readyDocuments = documents.filter((doc) => doc.status === 'READY')

  const { data: reviews = [] } = useQuery<ContractReviewDto[]>({
    queryKey: ['case-contract-reviews', caseId],
    queryFn: () => contractReviewsApi.listByCase(caseId),
    enabled: caseId !== '',
  })

  const reviewMutation = useMutation({
    mutationFn: () => contractReviewsApi.create(selectedDocId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-contract-reviews', caseId] })
      setSelectedDocId('')
    },
  })

  return (
    <section className="mb-10 p-5 rounded-xl bg-surface border border-line">
      <h2 className="text-sm font-semibold text-fg mb-1">{t('contractReview.title')}</h2>
      <p className="text-xs text-fg-muted mb-3">
        {t('contractReview.hint')}
      </p>

      {readyDocuments.length === 0 ? (
        <p className="text-sm text-fg-muted">
          {t('contractReview.uploadHint')}
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <select
            value={selectedDocId}
            onChange={(e) => setSelectedDocId(e.target.value)}
            className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
          >
            <option value="">{t('contractReview.selectContract')}</option>
            {readyDocuments.map((doc) => (
              <option key={doc.id} value={doc.id}>
                {doc.title}
              </option>
            ))}
          </select>

          {reviewMutation.isError && (
            <p className="text-sm text-danger">{t('contractReview.reviewError')}</p>
          )}

          <div>
            <Button
              variant="primary"
              disabled={!selectedDocId}
              loading={reviewMutation.isPending}
              onClick={() => reviewMutation.mutate()}
            >
              {t('contractReview.analyzeRisks')}
            </Button>
          </div>
        </div>
      )}

      {reviews.length > 0 && (
        <div className="flex flex-col gap-4 mt-5">
          {reviews.map((review) => (
            <ContractReviewCard key={review.id} review={review} />
          ))}
        </div>
      )}
    </section>
  )
}

function ContractReviewCard({ review }: { review: ContractReviewDto }): JSX.Element {
  const { t } = useTranslation()
  return (
    <div className="p-4 rounded-lg border border-line bg-bg">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="min-w-0">
          <p className="text-sm font-medium text-fg truncate">{review.documentTitle}</p>
          <p className="text-xs text-fg-muted">
            {new Date(review.createdAt).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')}
          </p>
        </div>
        <div className="text-right shrink-0">
          <span className={`text-lg font-semibold ${riskScoreTone(review.riskScore)}`}>{review.riskScore}</span>
          <span className="text-xs text-fg-muted">/100</span>
          {review.highRiskCount > 0 && (
            <p className="text-xs text-danger">{t('contractReview.highRiskCount', { count: review.highRiskCount })}</p>
          )}
        </div>
      </div>

      <p className="text-sm text-fg whitespace-pre-wrap mb-3">{review.summary}</p>

      {review.findings.length === 0 ? (
        <p className="text-xs text-fg-muted">{t('contractReview.noRisks')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          {review.findings.map((risk, i) => {
            const meta = RISK_LEVEL_META[risk.level]
            return (
              <div key={i} className={`p-3 rounded-lg border ${meta.tone}`}>
                <div className="flex items-center justify-between gap-2 mb-1">
                  <span className="text-xs font-semibold uppercase tracking-wide">{t(meta.labelKey)} · {risk.category}</span>
                </div>
                <p className="text-sm font-medium text-fg mb-1">{risk.clause}</p>
                {risk.explanation && (
                  <p className="text-xs text-fg-muted mb-1">{risk.explanation}</p>
                )}
                {risk.recommendation && (
                  <p className="text-xs text-fg">
                    <span className="font-semibold">{t('contractReview.recommendation')}</span> {risk.recommendation}
                  </p>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
