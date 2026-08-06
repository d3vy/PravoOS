import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { documentComparisonsApi } from '../../api/documentComparisons'
import type {
  DiffChange,
  DiffSegment,
  DiffSegmentType,
  DocumentComparisonDto,
  DocumentResponse,
} from '../../types'
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
    <section className="mb-10 p-5 rounded-xl bg-surface border border-line">
      <h2 className="text-sm font-semibold text-fg mb-1">{t('comparison.title')}</h2>
      <p className="text-xs text-fg-muted mb-3">
        {t('comparison.hint')}
      </p>

      {readyDocuments.length < 2 ? (
        <p className="text-sm text-fg-muted">
          {t('comparison.uploadHint')}
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-fg-muted mb-1">{t('comparison.baseVersion')}</label>
              <select
                value={baseDocId}
                onChange={(e) => setBaseDocId(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              >
                <option value="">{t('comparison.selectDoc')}</option>
                {readyDocuments.map((doc) => (
                  <option key={doc.id} value={doc.id}>{doc.title}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs text-fg-muted mb-1">{t('comparison.revisedVersion')}</label>
              <select
                value={revisedDocId}
                onChange={(e) => setRevisedDocId(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              >
                <option value="">{t('comparison.selectDoc')}</option>
                {readyDocuments.map((doc) => (
                  <option key={doc.id} value={doc.id}>{doc.title}</option>
                ))}
              </select>
            </div>
          </div>

          {baseDocId !== '' && baseDocId === revisedDocId && (
            <p className="text-sm text-warning">{t('comparison.pickTwoDifferent')}</p>
          )}
          {compareMutation.isError && (
            <p className="text-sm text-danger">{t('comparison.compareError')}</p>
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
  const exportMutation = useMutation({
    mutationFn: () =>
      documentComparisonsApi.downloadDocx(
        comparison.id,
        `redline-${comparison.baseDocumentTitle}-${comparison.revisedDocumentTitle}.docx`,
      ),
  })
  return (
    <div className="p-4 rounded-lg border border-line bg-bg">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="min-w-0">
          <p className="text-sm font-medium text-fg [overflow-wrap:anywhere]">
            <span className="text-danger">{comparison.baseDocumentTitle}</span>
            {' → '}
            <span className="text-success">{comparison.revisedDocumentTitle}</span>
          </p>
          <p className="text-xs text-fg-muted">
            {new Date(comparison.createdAt).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')} · {t('comparison.changeCount', { count: comparison.changeCount })}
          </p>
        </div>
        <div className="text-right shrink-0">
          <span className={`text-lg font-semibold ${riskScoreTone(comparison.riskScore)}`}>{comparison.riskScore}</span>
          <span className="text-xs text-fg-muted">/100</span>
          {comparison.highRiskCount > 0 && (
            <p className="text-xs text-danger">{t('contractReview.highRiskCount', { count: comparison.highRiskCount })}</p>
          )}
        </div>
      </div>

      <p className="text-sm text-fg whitespace-pre-wrap mb-3">{comparison.summary}</p>

      {comparison.changes.length === 0 ? (
        <p className="text-xs text-fg-muted">{t('comparison.noDiff')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          {comparison.changes.map((change) => (
            <DiffChangeRow key={change.order} change={change} />
          ))}
        </div>
      )}

      <div className="mt-3 flex items-center gap-2">
        <Button variant="ghost" loading={exportMutation.isPending} onClick={() => exportMutation.mutate()}>
          {t('comparison.exportDocx')}
        </Button>
        {exportMutation.isError && (
          <span className="text-xs text-danger">{t('comparison.exportError')}</span>
        )}
      </div>
    </div>
  )
}

function DiffChangeRow({ change }: { change: DiffChange }): JSX.Element {
  const { t } = useTranslation()
  const typeMeta = DIFF_TYPE_META[change.type]
  const riskMeta = change.riskLevel ? RISK_LEVEL_META[change.riskLevel] : null
  return (
    <div className="p-3 rounded-lg border border-line">
      <div className="flex items-center gap-2 mb-2">
        <span className={`text-xs font-semibold uppercase tracking-wide ${typeMeta.tone}`}>{t(typeMeta.labelKey)}</span>
        {riskMeta && (
          <span className={`text-xs font-semibold px-1.5 py-0.5 rounded border ${riskMeta.tone}`}>{t('comparison.riskSuffix', { level: t(riskMeta.labelKey) })}</span>
        )}
      </div>
      {change.segments.length > 0 ? (
        <InlineDiff segments={change.segments} />
      ) : (
        <>
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
        </>
      )}
      {change.comment && (
        <p className="text-xs text-fg-muted mt-1">{change.comment}</p>
      )}
    </div>
  )
}

const SEGMENT_CLASS: Record<DiffSegmentType, string> = {
  EQUAL: '',
  REMOVED: 'bg-red-50 text-red-800 line-through decoration-red-400/60 dark:bg-red-500/10 dark:text-red-300',
  ADDED: 'bg-emerald-50 text-emerald-800 dark:bg-emerald-500/10 dark:text-emerald-300',
}

function InlineDiff({ segments }: { segments: DiffSegment[] }): JSX.Element {
  const { t } = useTranslation()
  return (
    <p className="text-sm mb-1 px-2 py-1 rounded border border-line text-fg [overflow-wrap:anywhere] whitespace-pre-wrap">
      {segments.map((segment, index) => (
        <span
          key={index}
          className={SEGMENT_CLASS[segment.type]}
          aria-label={segment.type === 'EQUAL' ? undefined : t(`comparison.segment.${segment.type}`)}
        >
          {segment.text}
        </span>
      ))}
    </p>
  )
}
