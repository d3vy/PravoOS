import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import { workflowDefinitionsApi } from '../../api/workflows'
import type {
  WorkflowDefinitionDto,
  WorkflowRunDto,
  WorkflowRunStatus,
  WorkflowStepRun,
  WorkflowStepStatus,
  WorkflowStepType,
} from '../../types'
import { Button } from '../ui/Button'

const STEP_TYPE_LABEL_KEY: Record<WorkflowStepType, string> = {
  AI_ANALYSIS: 'status.workflowStepType.AI_ANALYSIS',
  GENERATE_DRAFT: 'status.workflowStepType.GENERATE_DRAFT',
  GENERATE_TASKS: 'status.workflowStepType.GENERATE_TASKS',
  SET_DEADLINE: 'status.workflowStepType.SET_DEADLINE',
}

const STEP_STATUS_META: Record<WorkflowStepStatus, { labelKey: string; tone: string }> = {
  PENDING: { labelKey: 'status.workflowStepStatus.PENDING', tone: 'border-line text-fg-muted bg-bg' },
  RUNNING: { labelKey: 'status.workflowStepStatus.RUNNING', tone: 'border-blue-300 text-blue-700 bg-blue-50 dark:border-blue-500/40 dark:text-blue-400 dark:bg-blue-500/10' },
  COMPLETED: { labelKey: 'status.workflowStepStatus.COMPLETED', tone: 'border-emerald-300 text-emerald-700 bg-emerald-50 dark:border-emerald-500/40 dark:text-emerald-400 dark:bg-emerald-500/10' },
  FAILED: { labelKey: 'status.workflowStepStatus.FAILED', tone: 'border-red-300 text-red-700 bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:bg-red-500/10' },
  SKIPPED: { labelKey: 'status.workflowStepStatus.SKIPPED', tone: 'border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10' },
}

const RUN_STATUS_META: Record<WorkflowRunStatus, { labelKey: string; tone: string }> = {
  RUNNING: { labelKey: 'status.workflowRunStatus.RUNNING', tone: 'text-info' },
  COMPLETED: { labelKey: 'status.workflowRunStatus.COMPLETED', tone: 'text-success' },
  FAILED: { labelKey: 'status.workflowRunStatus.FAILED', tone: 'text-danger' },
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US', { dateStyle: 'short', timeStyle: 'short' })
}

export function WorkflowProcessSection({ caseId }: { caseId: string }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState('')

  const { data: definitions = [] } = useQuery<WorkflowDefinitionDto[]>({
    queryKey: ['workflow-definitions'],
    queryFn: workflowDefinitionsApi.getAll,
  })

  const { data: runs = [] } = useQuery<WorkflowRunDto[]>({
    queryKey: ['case-workflow-runs', caseId],
    queryFn: () => casesApi.getWorkflowRuns(caseId),
    enabled: caseId !== '',
  })

  const runMutation = useMutation({
    mutationFn: () => casesApi.runWorkflowDefinition(caseId, selectedId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-workflow-runs', caseId] })
      queryClient.invalidateQueries({ queryKey: ['case-responses', caseId] })
      queryClient.invalidateQueries({ queryKey: ['case-drafts', caseId] })
      queryClient.invalidateQueries({ queryKey: ['case-tasks', caseId] })
      queryClient.invalidateQueries({ queryKey: ['case', caseId] })
    },
  })

  const selected = definitions.find((d) => d.id === selectedId)

  return (
    <section className="mb-10 p-5 rounded-xl bg-surface border border-line">
      <div className="flex items-center justify-between mb-1">
        <h2 className="text-sm font-semibold text-fg">{t('process.title')}</h2>
        <Link to="/workflows" className="text-xs text-accent hover:underline">
          {t('process.builderLink')}
        </Link>
      </div>
      <p className="text-xs text-fg-muted mb-4">
        {t('process.hint')}
      </p>

      <div className="grid gap-2 sm:grid-cols-2 mb-3">
        {definitions.map((definition) => (
          <button
            key={definition.id}
            type="button"
            onClick={() => setSelectedId(definition.id)}
            className={`text-left p-3 rounded-lg border text-sm transition-colors ${
              selectedId === definition.id
                ? 'border-accent bg-accent/5 text-fg'
                : 'border-line text-fg-muted hover:border-accent/50'
            }`}
          >
            <span className="block font-medium text-fg">{definition.name}</span>
            <span className="block text-xs mt-0.5">
              {definition.categoryName} · {t('process.stepsCount', { count: definition.steps.length })}
            </span>
          </button>
        ))}
      </div>

      {definitions.length === 0 && (
        <p className="text-sm text-fg-muted mb-3">{t('process.noProcesses')}</p>
      )}

      {selected && (
        <div className="mb-3 p-3 rounded-lg bg-bg border border-line">
          {selected.description && (
            <p className="text-xs text-fg-muted mb-2">{selected.description}</p>
          )}
          <ol className="space-y-1">
            {selected.steps.map((step) => (
              <li key={step.order} className="text-xs text-fg flex gap-2">
                <span className="text-fg-muted">{step.order + 1}.</span>
                <span className="px-1.5 py-0.5 rounded bg-surface border border-line text-fg-muted">
                  {t(STEP_TYPE_LABEL_KEY[step.type])}
                </span>
                <span>{step.title}</span>
              </li>
            ))}
          </ol>
        </div>
      )}

      {runMutation.isError && (
        <p className="text-sm text-danger mb-2">{t('process.runError')}</p>
      )}

      <Button
        variant="primary"
        disabled={!selectedId}
        loading={runMutation.isPending}
        onClick={() => runMutation.mutate()}
      >
        {t('process.runProcess')}
      </Button>

      {runs.length > 0 && (
        <div className="mt-6">
          <h3 className="text-xs font-semibold text-fg-muted uppercase tracking-wide mb-2">
            {t('process.runHistory')}
          </h3>
          <div className="space-y-3">
            {runs.map((run) => (
              <WorkflowRunCard key={run.id} run={run} />
            ))}
          </div>
        </div>
      )}
    </section>
  )
}

function WorkflowRunCard({ run }: { run: WorkflowRunDto }): JSX.Element {
  const { t } = useTranslation()
  const statusMeta = RUN_STATUS_META[run.status]
  return (
    <div className="p-3 rounded-lg border border-line bg-bg">
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-medium text-fg">{run.definitionName}</span>
        <span className={`text-xs font-semibold ${statusMeta.tone}`}>{t(statusMeta.labelKey)}</span>
      </div>
      <p className="text-xs text-fg-muted mb-2">{formatDateTime(run.startedAt)}</p>
      <div className="space-y-1.5">
        {run.steps.map((step) => (
          <WorkflowRunStepRow key={step.order} step={step} />
        ))}
      </div>
    </div>
  )
}

function WorkflowRunStepRow({ step }: { step: WorkflowStepRun }): JSX.Element {
  const { t } = useTranslation()
  const meta = STEP_STATUS_META[step.status]
  return (
    <div className="flex items-start gap-2 text-xs">
      <span className={`shrink-0 px-1.5 py-0.5 rounded border ${meta.tone}`}>{t(meta.labelKey)}</span>
      <div className="min-w-0">
        <span className="text-fg">{step.title}</span>
        {step.detail && (
          <span className="block text-fg-muted truncate">{step.detail}</span>
        )}
        {step.error && <span className="block text-danger">{step.error}</span>}
      </div>
    </div>
  )
}
