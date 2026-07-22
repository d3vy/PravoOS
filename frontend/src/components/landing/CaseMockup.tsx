import { useTranslation } from 'react-i18next'
import { AppWindow } from './AppWindow'

export function CaseMockup(): JSX.Element {
  const { t } = useTranslation()
  const tabs = [
    t('landing.caseTabOverview'),
    t('landing.caseTabDocuments'),
    t('landing.caseTabAiAnalysis'),
    t('landing.caseTabTimeBilling'),
    t('landing.caseTabTasks'),
    t('landing.caseTabMessages'),
  ]
  const documents = [
    { name: t('landing.caseDoc1'), tag: 'PDF' },
    { name: t('landing.caseDoc2'), tag: 'DOCX' },
    { name: t('landing.caseDoc3'), tag: 'PDF' },
  ]

  return (
    <AppWindow title="app.pravoos.ru/cases">
      <div className="p-5">
        <div className="flex items-start justify-between gap-3 mb-1">
          <h3 className="text-lg font-semibold text-fg">
            {t('landing.caseTitle')}
          </h3>
          <span className="shrink-0 text-[11px] px-2 py-0.5 rounded-full bg-blue-500/10 text-info font-medium">
            {t('landing.caseStatusInWork')}
          </span>
        </div>
        <div className="flex flex-wrap gap-2 mb-4">
          <span className="text-[11px] px-2 py-1 rounded-md border border-red-300 text-red-700 dark:border-red-500/40 dark:text-red-400">
            {t('landing.caseHearing')}
          </span>
          <span className="text-[11px] px-2 py-1 rounded-md border border-line text-fg-muted">
            {t('landing.caseDeadline')}
          </span>
        </div>

        <div className="flex gap-1 overflow-hidden border-b border-line mb-4">
          {tabs.map((tab, i) => (
            <span
              key={tab}
              className={`shrink-0 flex items-center gap-1.5 px-3 py-2 -mb-px border-b-2 text-xs font-medium whitespace-nowrap ${
                i === 1
                  ? 'border-accent text-fg'
                  : 'border-transparent text-fg-muted'
              }`}
            >
              {tab}
              {i === 1 && (
                <span className="inline-flex items-center justify-center min-w-4 h-4 px-1 rounded-full bg-accent/15 text-accent text-[10px]">
                  6
                </span>
              )}
            </span>
          ))}
        </div>

        <div className="flex flex-col gap-2">
          {documents.map((doc) => (
            <div
              key={doc.name}
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg bg-surface border border-line"
            >
              <span className="text-[10px] font-bold text-fg-muted w-8 shrink-0">
                {doc.tag}
              </span>
              <span className="flex-1 min-w-0 text-xs text-fg truncate">{doc.name}</span>
              <span className="text-[11px] px-2 py-0.5 rounded-md bg-emerald-500/10 text-success shrink-0">
                {t('landing.caseDocReady')}
              </span>
            </div>
          ))}
        </div>
      </div>
    </AppWindow>
  )
}
