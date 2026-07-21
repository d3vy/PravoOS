import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { documentComparisonsApi } from '../../api/documentComparisons'
import type { DiffChange, DocumentComparisonDto, DocumentResponse } from '../../types'
import { Button } from '../ui/Button'
import { DIFF_TYPE_META, RISK_LEVEL_META, riskScoreTone } from './caseFormatting'

export function ComparisonSection({ caseId, documents }: { caseId: string; documents: DocumentResponse[] }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [baseDocId, setBaseDocId] = useState('')
  const [revisedDocId, setRevisedDocId] = useState('')
  const readyDocuments = documents.filter((doc) => doc.status === 'READY')

  const { data: comparisons = [] } = useQuery<DocumentComparisonDto[]>({
    queryKey: ['case-comparisons', caseId],
    queryFn: () => documentComparisonsApi.listByCase(caseId),
    enabled: caseId !== '',
  })

  const compareMutation = useMutation({
    mutationFn: () => documentComparisonsApi.create(baseDocId, revisedDocId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-comparisons', caseId] })
      setBaseDocId('')
      setRevisedDocId('')
    },
  })

  const canCompare = baseDocId !== '' && revisedDocId !== '' && baseDocId !== revisedDocId

  return (
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-1">{t('comparison.title')}</h2>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mb-3">
        {t('comparison.hint')}
      </p>

      {readyDocuments.length < 2 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          {t('comparison.uploadHint')}
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-light-secondary dark:text-dark-secondary mb-1">{t('comparison.baseVersion')}</label>
              <select
                value={baseDocId}
                onChange={(e) => setBaseDocId(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
              >
                <option value="">{t('comparison.selectDoc')}</option>
                {readyDocuments.map((doc) => (
                  <option key={doc.id} value={doc.id}>{doc.title}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs text-light-secondary dark:text-dark-secondary mb-1">{t('comparison.revisedVersion')}</label>
              <select
                value={revisedDocId}
                onChange={(e) => setRevisedDocId(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
              >
                <option value="">{t('comparison.selectDoc')}</option>
                {readyDocuments.map((doc) => (
                  <option key={doc.id} value={doc.id}>{doc.title}</option>
                ))}
              </select>
            </div>
          </div>

          {baseDocId !== '' && baseDocId === revisedDocId && (
            <p className="text-sm text-amber-600 dark:text-amber-400">{t('comparison.pickTwoDifferent')}</p>
          )}
          {compareMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">{t('comparison.compareError')}</p>
          )}

          <div>
            <Button
              variant="primary"
              disabled={!canCompare}
              loading={compareMutation.isPending}
              onClick={() => compareMutation.mutate()}
            >
              {t('comparison.compareAction')}
            </Button>
          </div>
        </div>
      )}

      {comparisons.length > 0 && (
        <div className="flex flex-col gap-4 mt-5">
          {comparisons.map((comparison) => (
            <ComparisonCard key={comparison.id} comparison={comparison} />
          ))}
        </div>
      )}
    </section>
  )
}

function ComparisonCard({ comparison }: { comparison: DocumentComparisonDto }): JSX.Element {
  const { t } = useTranslation()
  return (
    <div className="p-4 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="min-w-0">
          <p className="text-sm font-medium text-light-text dark:text-dark-text [overflow-wrap:anywhere]">
            <span className="text-red-600 dark:text-red-400">{comparison.baseDocumentTitle}</span>
            {' → '}
            <span className="text-emerald-600 dark:text-emerald-400">{comparison.revisedDocumentTitle}</span>
          </p>
          <p className="text-xs text-light-secondary dark:text-dark-secondary">
            {new Date(comparison.createdAt).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')} · {t('comparison.changeCount', { count: comparison.changeCount })}
          </p>
        </div>
        <div className="text-right shrink-0">
          <span className={`text-lg font-semibold ${riskScoreTone(comparison.riskScore)}`}>{comparison.riskScore}</span>
          <span className="text-xs text-light-secondary dark:text-dark-secondary">/100</span>
          {comparison.highRiskCount > 0 && (
            <p className="text-xs text-red-600 dark:text-red-400">{t('contractReview.highRiskCount', { count: comparison.highRiskCount })}</p>
          )}
        </div>
      </div>

      <p className="text-sm text-light-text dark:text-dark-text whitespace-pre-wrap mb-3">{comparison.summary}</p>

      {comparison.changes.length === 0 ? (
        <p className="text-xs text-light-secondary dark:text-dark-secondary">{t('comparison.noDiff')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          {comparison.changes.map((change) => (
            <DiffChangeRow key={change.order} change={change} />
          ))}
        </div>
      )}
    </div>
  )
}

function DiffChangeRow({ change }: { change: DiffChange }): JSX.Element {
  const { t } = useTranslation()
  const typeMeta = DIFF_TYPE_META[change.type]
  const riskMeta = change.riskLevel ? RISK_LEVEL_META[change.riskLevel] : null
  return (
    <div className="p-3 rounded-lg border border-light-border dark:border-dark-border">
      <div className="flex items-center gap-2 mb-2">
        <span className={`text-xs font-semibold uppercase tracking-wide ${typeMeta.tone}`}>{t(typeMeta.labelKey)}</span>
        {riskMeta && (
          <span className={`text-xs font-semibold px-1.5 py-0.5 rounded border ${riskMeta.tone}`}>{t('comparison.riskSuffix', { level: t(riskMeta.labelKey) })}</span>
        )}
      </div>
      {change.baseText && (
        <p className="text-sm mb-1 px-2 py-1 rounded bg-red-50 text-red-800 line-through decoration-red-400/60 dark:bg-red-500/10 dark:text-red-300 [overflow-wrap:anywhere] whitespace-pre-wrap">
          {change.baseText}
        </p>
      )}
      {change.revisedText && (
        <p className="text-sm mb-1 px-2 py-1 rounded bg-emerald-50 text-emerald-800 dark:bg-emerald-500/10 dark:text-emerald-300 [overflow-wrap:anywhere] whitespace-pre-wrap">
          {change.revisedText}
        </p>
      )}
      {change.comment && (
        <p className="text-xs text-light-secondary dark:text-dark-secondary mt-1">{change.comment}</p>
      )}
    </div>
  )
}
