import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import { workflowsApi } from '../../api/workflows'
import type { WorkflowInfo } from '../../types'
import { Button } from '../ui/Button'

const WORKFLOW_TABS = [
  { id: 'analysis', labelKey: 'workflow.tabAnalysis', ids: ['DEBTOR_SOLVENCY_ANALYSIS', 'CHALLENGE_TRANSACTIONS', 'CREDITOR_CLAIMS', 'SUBSIDIARY_LIABILITY', 'BANKRUPTCY_ESTATE'] },
  { id: 'documents', labelKey: 'workflow.tabDocuments', ids: ['DOCUMENT_CHECKLIST', 'DATA_EXTRACTION'] },
  { id: 'summary', labelKey: 'workflow.tabSummary', ids: ['CASE_SUMMARY', 'RISK_MAP', 'CHRONOLOGY'] },
] as const

export function WorkflowSection({ caseId }: { caseId: string }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<string>('analysis')
  const [selectedId, setSelectedId] = useState('')
  const [question, setQuestion] = useState('')

  const { data: workflows = [] } = useQuery<WorkflowInfo[]>({
    queryKey: ['workflows'],
    queryFn: workflowsApi.getAll,
  })

  const runMutation = useMutation({
    mutationFn: () => casesApi.runWorkflow(caseId, selectedId, question.trim() || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-responses', caseId] })
      setQuestion('')
    },
  })

  const currentTab = WORKFLOW_TABS.find((t) => t.id === activeTab)
  const visibleWorkflows = workflows.filter((w) => currentTab?.ids.includes(w.id as never))
  const selectedWorkflow = visibleWorkflows.find((w) => w.id === selectedId)

  const handleTabChange = (tabId: string): void => {
    setActiveTab(tabId)
    setSelectedId('')
  }

  return (
    <section className="mb-10 p-5 rounded-xl bg-surface border border-line">
      <h2 className="text-sm font-semibold text-fg mb-3">{t('workflow.runTitle')}</h2>

      <div className="flex gap-1 mb-4 p-1 rounded-lg bg-bg">
        {WORKFLOW_TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => handleTabChange(tab.id)}
            className={`flex-1 px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              activeTab === tab.id
                ? 'bg-surface text-fg shadow-sm'
                : 'text-fg-muted hover:text-fg'
            }`}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      <div className="flex flex-col gap-3">
        <div className="grid gap-2 sm:grid-cols-2">
          {visibleWorkflows.map((workflow) => (
            <button
              key={workflow.id}
              type="button"
              onClick={() => setSelectedId(workflow.id)}
              className={`text-left p-3 rounded-lg border text-sm transition-colors ${
                selectedId === workflow.id
                  ? 'border-accent bg-accent/5 text-fg'
                  : 'border-line text-fg-muted hover:border-accent/50'
              }`}
            >
              {workflow.displayName}
            </button>
          ))}
        </div>

        {selectedWorkflow && (
          <p className="text-xs text-fg-muted">{selectedWorkflow.instruction}</p>
        )}

        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          rows={2}
          maxLength={2000}
          placeholder={t('workflow.extraQuestion')}
          className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent resize-none"
        />

        {runMutation.isError && (
          <p className="text-sm text-danger">{t('workflow.runError')}</p>
        )}

        <div>
          <Button
            variant="primary"
            disabled={!selectedId}
            loading={runMutation.isPending}
            onClick={() => runMutation.mutate()}
          >
            {t('workflow.runAction')}
          </Button>
        </div>
      </div>
    </section>
  )
}
