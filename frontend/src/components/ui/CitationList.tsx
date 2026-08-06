import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import type { CitationCheck, CitationCheckResult, CitationStatus } from '../../types'

export const CITATION_STATUS_TONE: Record<CitationStatus, string> = {
  VERIFIED: 'border-emerald-300 text-emerald-700 bg-emerald-50 dark:border-emerald-500/40 dark:text-emerald-400 dark:bg-emerald-500/10',
  NOT_FOUND: 'border-red-300 text-red-700 bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:bg-red-500/10',
  OUTDATED: 'border-orange-300 text-orange-700 bg-orange-50 dark:border-orange-500/40 dark:text-orange-400 dark:bg-orange-500/10',
  UNVERIFIED: 'border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10',
}

export function citationSummary(result: CitationCheckResult): string {
  return result.total === 0
    ? i18n.t('citation.noneFound')
    : i18n.t('citation.summary', {
        total: result.total,
        verified: result.verified,
        notFound: result.notFound,
        outdated: result.outdated,
        unverified: result.unverified,
      })
}

export function CitationRow({ citation }: { citation: CitationCheck }): JSX.Element {
  const { t } = useTranslation()
  const typeLabel = citation.type === 'COURT_CASE' ? t('citation.typeCase') : t('citation.typeStatute')
  return (
    <div className="flex items-start gap-2 text-xs">
      <span className={`shrink-0 px-1.5 py-0.5 rounded border font-medium ${CITATION_STATUS_TONE[citation.status]}`}>
        {t(`status.citation.${citation.status}`)}
      </span>
      <div className="min-w-0">
        <span className="font-medium text-fg">{typeLabel}: {citation.raw}</span>
        <span className="block text-fg-muted">{citation.detail}</span>
      </div>
    </div>
  )
}

export function CitationList({ result }: { result: CitationCheckResult }): JSX.Element | null {
  const { t } = useTranslation()
  if (result.citations.length === 0) return null
  const unverifiedStatutes = result.citations.some(
    (c) => c.type === 'STATUTE' && c.status !== 'VERIFIED'
  )
  return (
    <div className="flex flex-col gap-1.5">
      {unverifiedStatutes && (
        <p className="text-xs px-2 py-1.5 rounded border border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10">
          {t('citation.unverifiedWarning')}
        </p>
      )}
      {result.citations.map((citation, i) => (
        <CitationRow key={i} citation={citation} />
      ))}
    </div>
  )
}
