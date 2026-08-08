import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import type { AiResponseDto, CaseDraftSummaryDto, CaseResponse, DocumentResponse } from '../../types'
import { DocumentStatusBadge } from '../ui/Badge'
import { DeadlineList } from './CaseHeaderSection'

interface CaseOverviewTabProps {
  caseItem: CaseResponse
  documents: DocumentResponse[]
  drafts: CaseDraftSummaryDto[]
  responses: AiResponseDto[]
  onNavigate: (tabId: string) => void
}

interface OverviewStat {
  label: string
  value: number
  tab: string
}

export function CaseOverviewTab({ caseItem, documents, drafts, responses, onNavigate }: CaseOverviewTabProps): JSX.Element {
  const { t } = useTranslation()
  const stats: OverviewStat[] = [
    { label: t('overview.statDocuments'), value: documents.length, tab: 'documents' },
    { label: t('overview.statDrafts'), value: drafts.length, tab: 'documents' },
    { label: t('overview.statResponses'), value: responses.length, tab: 'analysis' },
  ]

  const hasDeadlines = Boolean(caseItem.filingDeadline || caseItem.nextHearingDate || caseItem.expiresAt)
  const recentDocuments = documents.slice(0, 4)
  const latestResponse = responses[0]

  return (
    <div className="flex flex-col gap-8">
      <div className="grid grid-cols-3 gap-3">
        {stats.map((stat) => (
          <button
            key={stat.label}
            type="button"
            onClick={() => onNavigate(stat.tab)}
            className="text-left p-4 rounded-2xl bg-surface border border-line hover:border-accent/50 transition-colors"
          >
            <span className="block text-2xl font-semibold text-fg">{stat.value}</span>
            <span className="block text-xs text-fg-muted mt-0.5">{stat.label}</span>
          </button>
        ))}
      </div>

      {hasDeadlines && (
        <section>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-semibold text-fg">{t('overview.upcomingDeadlines')}</h2>
            <button
              type="button"
              onClick={() => onNavigate('tasks')}
              className="text-xs text-accent hover:underline"
            >
              {t('overview.tasksAndDeadlines')}
            </button>
          </div>
          <DeadlineList caseItem={caseItem} />
        </section>
      )}

      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold text-fg">{t('overview.caseDocuments')}</h2>
          <button
            type="button"
            onClick={() => onNavigate('documents')}
            className="text-xs text-accent hover:underline"
          >
            {t('overview.allDocuments')}
          </button>
        </div>
        {recentDocuments.length === 0 ? (
          <p className="text-sm text-fg-muted">
            {t('overview.noDocuments')}
          </p>
        ) : (
          <div className="flex flex-col gap-2">
            {recentDocuments.map((doc) => (
              <div
                key={doc.id}
                className="flex items-center gap-3 p-3 rounded-lg bg-surface border border-line"
              >
                <span className="text-xs font-bold uppercase text-fg-muted w-9 shrink-0">
                  {doc.fileName.split('.').pop()}
                </span>
                <p className="flex-1 min-w-0 text-sm text-fg truncate">{doc.title}</p>
                <DocumentStatusBadge status={doc.status} />
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold text-fg">{t('overview.latestResponse')}</h2>
          <button
            type="button"
            onClick={() => onNavigate('analysis')}
            className="text-xs text-accent hover:underline"
          >
            {t('overview.aiAnalysisLink')}
          </button>
        </div>
        {!latestResponse ? (
          <p className="text-sm text-fg-muted">
            {t('overview.noResponses')}
          </p>
        ) : (
          <button
            type="button"
            onClick={() => onNavigate('analysis')}
            className="block w-full text-left p-4 rounded-2xl bg-surface border border-line hover:border-accent/50 transition-colors"
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-medium px-2.5 py-0.5 rounded-full bg-accent/10 text-accent">
                {latestResponse.workflowName}
              </span>
              <span className="text-xs text-fg-muted">
                {new Date(latestResponse.createdAt).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')}
              </span>
            </div>
            <p className="text-sm text-fg-muted line-clamp-3">{latestResponse.result}</p>
          </button>
        )}
      </section>
    </div>
  )
}
