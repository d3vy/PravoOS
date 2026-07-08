import type { CitationCheck, CitationCheckResult, CitationStatus } from '../../types'

export const CITATION_STATUS_META: Record<CitationStatus, { label: string; tone: string }> = {
  VERIFIED: { label: 'Подтверждено', tone: 'border-emerald-300 text-emerald-700 bg-emerald-50 dark:border-emerald-500/40 dark:text-emerald-400 dark:bg-emerald-500/10' },
  NOT_FOUND: { label: 'Не найдено', tone: 'border-red-300 text-red-700 bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:bg-red-500/10' },
  UNVERIFIED: { label: 'Не проверено', tone: 'border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10' },
}

export function citationSummary(result: CitationCheckResult): string {
  return result.total === 0
    ? 'Ссылки не найдены'
    : `Всего: ${result.total} · подтверждено: ${result.verified} · не найдено: ${result.notFound} · не проверено: ${result.unverified}`
}

export function CitationRow({ citation }: { citation: CitationCheck }): JSX.Element {
  const meta = CITATION_STATUS_META[citation.status]
  const typeLabel = citation.type === 'COURT_CASE' ? 'Дело' : 'Норма'
  return (
    <div className="flex items-start gap-2 text-xs">
      <span className={`shrink-0 px-1.5 py-0.5 rounded border font-medium ${meta.tone}`}>{meta.label}</span>
      <div className="min-w-0">
        <span className="font-medium text-light-text dark:text-dark-text">{typeLabel}: {citation.raw}</span>
        <span className="block text-light-secondary dark:text-dark-secondary">{citation.detail}</span>
      </div>
    </div>
  )
}

export function CitationList({ result }: { result: CitationCheckResult }): JSX.Element | null {
  if (result.citations.length === 0) return null
  const unverifiedStatutes = result.citations.some(
    (c) => c.type === 'STATUTE' && c.status !== 'VERIFIED'
  )
  return (
    <div className="flex flex-col gap-1.5">
      {unverifiedStatutes && (
        <p className="text-xs px-2 py-1.5 rounded border border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10">
          Ответ содержит ссылки на нормы, не подтверждённые актуальной редакцией. Проверьте по первоисточнику.
        </p>
      )}
      {result.citations.map((citation, i) => (
        <CitationRow key={i} citation={citation} />
      ))}
    </div>
  )
}
