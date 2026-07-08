import { useState } from 'react'
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

const STEP_TYPE_LABEL: Record<WorkflowStepType, string> = {
  AI_ANALYSIS: 'AI-анализ',
  GENERATE_DRAFT: 'Черновик',
  GENERATE_TASKS: 'Задачи',
  SET_DEADLINE: 'Дедлайн',
}

const STEP_STATUS_META: Record<WorkflowStepStatus, { label: string; tone: string }> = {
  PENDING: { label: 'Ожидает', tone: 'border-light-border text-light-secondary bg-light-bg dark:border-dark-border dark:text-dark-secondary dark:bg-dark-bg' },
  RUNNING: { label: 'Выполняется', tone: 'border-blue-300 text-blue-700 bg-blue-50 dark:border-blue-500/40 dark:text-blue-400 dark:bg-blue-500/10' },
  COMPLETED: { label: 'Готово', tone: 'border-emerald-300 text-emerald-700 bg-emerald-50 dark:border-emerald-500/40 dark:text-emerald-400 dark:bg-emerald-500/10' },
  FAILED: { label: 'Ошибка', tone: 'border-red-300 text-red-700 bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:bg-red-500/10' },
  SKIPPED: { label: 'Пропущено', tone: 'border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10' },
}

const RUN_STATUS_META: Record<WorkflowRunStatus, { label: string; tone: string }> = {
  RUNNING: { label: 'Выполняется', tone: 'text-blue-600 dark:text-blue-400' },
  COMPLETED: { label: 'Завершён', tone: 'text-emerald-600 dark:text-emerald-400' },
  FAILED: { label: 'Ошибка', tone: 'text-red-600 dark:text-red-400' },
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
}

export function WorkflowProcessSection({ caseId }: { caseId: string }): JSX.Element {
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
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <div className="flex items-center justify-between mb-1">
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">Процессы (AI-workflow)</h2>
        <Link to="/workflows" className="text-xs text-light-accent dark:text-dark-accent hover:underline">
          Конструктор процессов →
        </Link>
      </div>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mb-4">
        Многошаговый сценарий: анализ, документы, черновики и дедлайны выполняются последовательно.
      </p>

      <div className="grid gap-2 sm:grid-cols-2 mb-3">
        {definitions.map((definition) => (
          <button
            key={definition.id}
            type="button"
            onClick={() => setSelectedId(definition.id)}
            className={`text-left p-3 rounded-lg border text-sm transition-colors ${
              selectedId === definition.id
                ? 'border-light-accent dark:border-dark-accent bg-light-accent/5 dark:bg-dark-accent/10 text-light-text dark:text-dark-text'
                : 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:border-light-accent/50 dark:hover:border-dark-accent/50'
            }`}
          >
            <span className="block font-medium text-light-text dark:text-dark-text">{definition.name}</span>
            <span className="block text-xs mt-0.5">
              {definition.categoryName} · {definition.steps.length} шаг(ов)
            </span>
          </button>
        ))}
      </div>

      {definitions.length === 0 && (
        <p className="text-sm text-light-secondary dark:text-dark-secondary mb-3">Нет доступных процессов.</p>
      )}

      {selected && (
        <div className="mb-3 p-3 rounded-lg bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border">
          {selected.description && (
            <p className="text-xs text-light-secondary dark:text-dark-secondary mb-2">{selected.description}</p>
          )}
          <ol className="space-y-1">
            {selected.steps.map((step) => (
              <li key={step.order} className="text-xs text-light-text dark:text-dark-text flex gap-2">
                <span className="text-light-secondary dark:text-dark-secondary">{step.order + 1}.</span>
                <span className="px-1.5 py-0.5 rounded bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary">
                  {STEP_TYPE_LABEL[step.type]}
                </span>
                <span>{step.title}</span>
              </li>
            ))}
          </ol>
        </div>
      )}

      {runMutation.isError && (
        <p className="text-sm text-red-600 dark:text-red-400 mb-2">Не удалось запустить процесс. Попробуйте снова.</p>
      )}

      <Button
        variant="primary"
        disabled={!selectedId}
        loading={runMutation.isPending}
        onClick={() => runMutation.mutate()}
      >
        Запустить процесс
      </Button>

      {runs.length > 0 && (
        <div className="mt-6">
          <h3 className="text-xs font-semibold text-light-secondary dark:text-dark-secondary uppercase tracking-wide mb-2">
            История запусков
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
  const statusMeta = RUN_STATUS_META[run.status]
  return (
    <div className="p-3 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-medium text-light-text dark:text-dark-text">{run.definitionName}</span>
        <span className={`text-xs font-semibold ${statusMeta.tone}`}>{statusMeta.label}</span>
      </div>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mb-2">{formatDateTime(run.startedAt)}</p>
      <div className="space-y-1.5">
        {run.steps.map((step) => (
          <WorkflowRunStepRow key={step.order} step={step} />
        ))}
      </div>
    </div>
  )
}

function WorkflowRunStepRow({ step }: { step: WorkflowStepRun }): JSX.Element {
  const meta = STEP_STATUS_META[step.status]
  return (
    <div className="flex items-start gap-2 text-xs">
      <span className={`shrink-0 px-1.5 py-0.5 rounded border ${meta.tone}`}>{meta.label}</span>
      <div className="min-w-0">
        <span className="text-light-text dark:text-dark-text">{step.title}</span>
        {step.detail && (
          <span className="block text-light-secondary dark:text-dark-secondary truncate">{step.detail}</span>
        )}
        {step.error && <span className="block text-red-600 dark:text-red-400">{step.error}</span>}
      </div>
    </div>
  )
}
