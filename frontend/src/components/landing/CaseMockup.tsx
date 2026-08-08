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
        <div className="flex items-start justify-between gap-3 mb-3">
          <h3 className="text-xl font-bold text-[#26251E]">
            {t('landing.caseTitle')}
          </h3>
          <span className="shrink-0 text-[12px] px-2 py-1 rounded-full bg-[#745C44]/20 text-[#745C44] font-medium">
            {t('landing.caseStatusInWork')}
          </span>
        </div>
        <div className="flex flex-wrap gap-2 mb-5">
          <span className="text-[13px] px-2.5 py-1.5 rounded-lg border border-[#B91C1C] text-[#B91C1C] font-medium">
            {t('landing.caseHearing')}
          </span>
          <span className="text-[13px] px-2.5 py-1.5 rounded-lg border border-[#26251E]/20 text-[#26251E]/50 font-medium">
            {t('landing.caseDeadline')}
          </span>
        </div>

        <div className="flex gap-1 overflow-hidden border-b border-[#E8E7E2] mb-5">
          {tabs.map((tab, i) => (
            <span
              key={tab}
              className={`shrink-0 flex items-center gap-1.5 px-3 py-2.5 -mb-px border-b-2 text-sm font-medium whitespace-nowrap ${
                i === 1
                  ? 'border-[#745C44] text-[#26251E]'
                  : 'border-transparent text-[#26251E]/50'
              }`}
            >
              {tab}
              {i === 1 && (
                <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-[#745C44]/20 text-[#745C44] text-[10px] font-semibold">
                  6
                </span>
              )}
            </span>
          ))}
        </div>

        <div className="flex flex-col gap-2.5">
          {documents.map((doc) => (
            <div
              key={doc.name}
              className="flex items-center gap-3 px-3.5 py-3 rounded-xl bg-[#F7F7F4] border border-[#E8E7E2]"
            >
              <span className="text-[11px] font-bold text-[#26251E]/50 w-9 shrink-0 uppercase">
                {doc.tag}
              </span>
              <span className="flex-1 min-w-0 text-[13px] text-[#26251E] truncate">{doc.name}</span>
              <span className="text-[12px] px-2 py-1 rounded-lg bg-[#E3F4EE] text-[#1F8A65] font-medium shrink-0">
                {t('landing.caseDocReady')}
              </span>
            </div>
          ))}
        </div>
      </div>
    </AppWindow>
  )
}
