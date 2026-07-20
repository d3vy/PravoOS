import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import { workflowsApi } from '../../api/workflows'
import type { WorkflowInfo } from '../../types'
import { Button } from '../ui/Button'

const WORKFLOW_TABS = [
  { id: 'analysis', label: 'Анализ', ids: ['DEBTOR_SOLVENCY_ANALYSIS', 'CHALLENGE_TRANSACTIONS', 'CREDITOR_CLAIMS', 'SUBSIDIARY_LIABILITY', 'BANKRUPTCY_ESTATE'] },
  { id: 'documents', label: 'Документы', ids: ['DOCUMENT_CHECKLIST', 'DATA_EXTRACTION'] },
  { id: 'summary', label: 'Итоги', ids: ['CASE_SUMMARY', 'RISK_MAP', 'CHRONOLOGY'] },
] as const

export function WorkflowSection({ caseId }: { caseId: string }): JSX.Element {
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
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">Запустить AI-анализ</h2>

      <div className="flex gap-1 mb-4 p-1 rounded-lg bg-light-bg dark:bg-dark-bg">
        {WORKFLOW_TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => handleTabChange(tab.id)}
            className={`flex-1 px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              activeTab === tab.id
                ? 'bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text shadow-sm'
                : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
            }`}
          >
            {tab.label}
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
                  ? 'border-light-accent dark:border-dark-accent bg-light-accent/5 dark:bg-dark-accent/10 text-light-text dark:text-dark-text'
                  : 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:border-light-accent/50 dark:hover:border-dark-accent/50'
              }`}
            >
              {workflow.displayName}
            </button>
          ))}
        </div>

        {selectedWorkflow && (
          <p className="text-xs text-light-secondary dark:text-dark-secondary">{selectedWorkflow.instruction}</p>
        )}

        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          rows={2}
          maxLength={2000}
          placeholder="Дополнительный вопрос (опционально)"
          className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-none"
        />

        {runMutation.isError && (
          <p className="text-sm text-red-600 dark:text-red-400">Ошибка запуска анализа. Попробуйте снова.</p>
        )}

        <div>
          <Button
            variant="primary"
            disabled={!selectedId}
            loading={runMutation.isPending}
            onClick={() => runMutation.mutate()}
          >
            Запустить анализ
          </Button>
        </div>
      </div>
    </section>
  )
}
