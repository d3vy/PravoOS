import { useEffect } from 'react'
import { Link, useParams, useLocation, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { AiResponseDto, CaseDraftSummaryDto, CaseResponse, DocumentResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'
import { CaseHeaderSection } from '../../components/cases/CaseHeaderSection'
import { CaseTabsNav, type CaseTabDescriptor } from '../../components/cases/CaseTabsNav'
import { CaseOverviewTab } from '../../components/cases/CaseOverviewTab'
import { DocumentsSection } from '../../components/cases/DocumentsSection'
import { DraftSection } from '../../components/cases/DraftSection'
import { ContractReviewSection } from '../../components/cases/ContractReviewSection'
import { ComparisonSection } from '../../components/cases/ComparisonSection'
import { CaseSignatureSection } from '../../components/cases/CaseSignatureSection'
import { CaseAnalyticsSection } from '../../components/cases/CaseAnalyticsSection'
import { WorkflowSection } from '../../components/cases/WorkflowSection'
import { WorkflowProcessSection } from '../../components/cases/WorkflowProcessSection'
import { ResponsesSection } from '../../components/cases/ResponsesSection'
import { CaseTimeSection } from '../../components/cases/CaseTimeSection'
import { CaseTasksSection } from '../../components/cases/CaseTasksSection'
import { ArbitrSection } from '../../components/cases/ArbitrSection'
import { CaseMessageThread } from '../../components/messages/CaseMessageThread'
import { DOCUMENT_POLLING_INTERVAL_MS } from '../../components/cases/caseFormatting'

const TAB_IDS = ['overview', 'documents', 'analysis', 'time', 'tasks', 'messages'] as const
type TabId = (typeof TAB_IDS)[number]

function resolveInitialTab(param: string | null, hash: string): TabId {
  if (param && (TAB_IDS as readonly string[]).includes(param)) return param as TabId
  if (hash === '#messages') return 'messages'
  return 'overview'
}

export default function CaseDetailPage(): JSX.Element {
  const { caseId = '' } = useParams()
  const { hash } = useLocation()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  const activeTab = resolveInitialTab(searchParams.get('tab'), hash)

  const selectTab = (tabId: string): void => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set('tab', tabId)
        return next
      },
      { replace: true }
    )
  }

  useEffect(() => {
    if (activeTab !== 'messages' || caseId === '') return
    void casesApi.markMessagesRead(caseId).then(() => {
      queryClient.invalidateQueries({ queryKey: ['messageThreads'] })
    })
  }, [activeTab, caseId, queryClient])

  const { data: caseItem, isLoading: caseLoading } = useQuery<CaseResponse>({
    queryKey: ['case', caseId],
    queryFn: () => casesApi.get(caseId),
    enabled: caseId !== '',
  })

  const { data: documents = [] } = useQuery<DocumentResponse[]>({
    queryKey: ['case-documents', caseId],
    queryFn: () => casesApi.getDocuments(caseId),
    enabled: caseId !== '',
    refetchInterval: (query) =>
      query.state.data?.some((d) => d.status === 'PROCESSING') ? DOCUMENT_POLLING_INTERVAL_MS : false,
  })

  const { data: drafts = [] } = useQuery<CaseDraftSummaryDto[]>({
    queryKey: ['case-drafts', caseId],
    queryFn: () => casesApi.getDrafts(caseId),
    enabled: caseId !== '',
  })

  const { data: responses = [] } = useQuery<AiResponseDto[]>({
    queryKey: ['case-responses', caseId],
    queryFn: () => casesApi.getResponses(caseId),
    enabled: caseId !== '',
  })

  if (caseLoading) {
    return (
      <div className="bg-light-bg dark:bg-dark-bg">
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!caseItem) {
    return (
      <div className="bg-light-bg dark:bg-dark-bg">
        <div className="page-container py-16 text-center">
          <p className="text-light-secondary dark:text-dark-secondary mb-4">Дело не найдено</p>
          <Link to="/cases" className="text-light-accent dark:text-dark-accent text-sm">
            ← Ко всем делам
          </Link>
        </div>
      </div>
    )
  }

  const tabs: CaseTabDescriptor[] = [
    { id: 'overview', label: 'Обзор' },
    { id: 'documents', label: 'Документы', badge: documents.length },
    { id: 'analysis', label: 'AI-анализ', badge: responses.length },
    { id: 'time', label: 'Время и счёт' },
    { id: 'tasks', label: 'Задачи и сроки' },
    { id: 'messages', label: 'Сообщения' },
  ]

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8 max-w-4xl">
        <Link to="/cases" className="text-sm text-light-secondary dark:text-dark-secondary hover:text-light-accent dark:hover:text-dark-accent mb-4 inline-block">
          ← Ко всем делам
        </Link>

        <CaseHeaderSection caseItem={caseItem} />

        <CaseTabsNav tabs={tabs} activeTab={activeTab} onSelect={selectTab} />

        <div id={`case-panel-${activeTab}`} role="tabpanel" aria-labelledby={`case-tab-${activeTab}`}>
          {activeTab === 'overview' && (
            <CaseOverviewTab
              caseItem={caseItem}
              documents={documents}
              drafts={drafts}
              responses={responses}
              onNavigate={selectTab}
            />
          )}

          {activeTab === 'documents' && (
            <>
              <DocumentsSection caseId={caseId} documents={documents} />
              <DraftSection caseId={caseId} drafts={drafts} />
              <ContractReviewSection caseId={caseId} documents={documents} />
              <ComparisonSection caseId={caseId} documents={documents} />
              <CaseSignatureSection caseId={caseId} documents={documents} />
            </>
          )}

          {activeTab === 'analysis' && (
            <>
              <CaseAnalyticsSection caseId={caseId} />
              <WorkflowSection caseId={caseId} />
              <WorkflowProcessSection caseId={caseId} />
              <ResponsesSection caseId={caseId} responses={responses} />
            </>
          )}

          {activeTab === 'time' && <CaseTimeSection caseId={caseId} clientId={caseItem.clientId} />}

          {activeTab === 'tasks' && (
            <>
              <CaseTasksSection caseId={caseId} />
              <ArbitrSection caseItem={caseItem} />
            </>
          )}

          {activeTab === 'messages' && (
            <CaseMessageThread
              queryKey={['case', caseId, 'messages']}
              viewerRole="LAWYER"
              listMessages={() => casesApi.listMessages(caseId)}
              sendMessage={(body) => casesApi.sendMessage(caseId, body)}
            />
          )}
        </div>
      </div>
    </div>
  )
}
