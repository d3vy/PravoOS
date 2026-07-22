import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { CaseTaskResponse } from '../../types'
import { Button } from '../ui/Button'

export function CaseTasksSection({ caseId }: { caseId: string }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [generateError, setGenerateError] = useState<string | null>(null)

  const { data: tasks = [] } = useQuery<CaseTaskResponse[]>({
    queryKey: ['case-tasks', caseId],
    queryFn: () => casesApi.getTasks(caseId),
    enabled: caseId !== '',
  })

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey: ['case-tasks', caseId] })
  }

  const createMutation = useMutation({
    mutationFn: () =>
      casesApi.createTask(caseId, { text: text.trim(), dueDate: dueDate || null }),
    onSuccess: () => {
      invalidate()
      setText('')
      setDueDate('')
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (task: CaseTaskResponse) =>
      casesApi.updateTask(caseId, task.id, {
        text: task.text,
        dueDate: task.dueDate,
        done: !task.done,
      }),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: (taskId: string) => casesApi.deleteTask(caseId, taskId),
    onSuccess: invalidate,
  })

  const generateMutation = useMutation({
    mutationFn: () => casesApi.generateTasks(caseId),
    onSuccess: (created) => {
      invalidate()
      setGenerateError(created.length === 0 ? t('tasks.noMissingDocs') : null)
    },
    onError: () => setGenerateError(t('tasks.generateError')),
  })

  const handleAdd = (): void => {
    if (!text.trim()) return
    createMutation.mutate()
  }

  const openCount = tasks.filter((task) => !task.done).length

  return (
    <section className="mb-10">
      <div className="flex items-center justify-between gap-3 mb-3">
        <h2 className="text-sm font-semibold text-fg">
          {t('tasks.title')}{' '}
          {tasks.length > 0 && (
            <span className="font-normal text-fg-muted">
              {t('tasks.countSummary', { open: openCount, total: tasks.length })}
            </span>
          )}
        </h2>
        <Button
          variant="secondary"
          size="sm"
          title={t('tasks.generateTooltip')}
          loading={generateMutation.isPending}
          onClick={() => generateMutation.mutate()}
        >
          {t('tasks.generateButton')}
        </Button>
      </div>

      <div className="flex flex-col sm:flex-row gap-2 mb-3">
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleAdd()
          }}
          maxLength={1000}
          placeholder={t('tasks.newTaskPlaceholder')}
          className="flex-1 px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent"
        />
        <input
          type="date"
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          className="px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
        />
        <Button variant="primary" disabled={!text.trim()} loading={createMutation.isPending} onClick={handleAdd}>
          {t('common.add')}
        </Button>
      </div>

      {generateError && <p className="text-sm text-danger mb-3">{generateError}</p>}

      {tasks.length === 0 ? (
        <p className="text-sm text-fg-muted">
          {t('tasks.emptyTasks')}
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          {tasks.map((task) => (
            <div
              key={task.id}
              className="flex items-center gap-3 p-3 rounded-lg bg-surface border border-line"
            >
              <input
                type="checkbox"
                checked={task.done}
                disabled={toggleMutation.isPending}
                onChange={() => toggleMutation.mutate(task)}
                className="h-4 w-4 shrink-0 accent-accent cursor-pointer"
              />
              <div className="flex-1 min-w-0">
                <p
                  className={`text-sm ${
                    task.done
                      ? 'line-through text-fg-muted'
                      : 'text-fg'
                  }`}
                >
                  {task.text}
                </p>
                {task.dueDate && (
                  <span className="text-xs text-fg-muted">
                    {t('tasks.dueBy', { date: new Date(task.dueDate).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US') })}
                  </span>
                )}
              </div>
              <button
                type="button"
                title={t('tasks.deleteTaskTitle')}
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(task.id)}
                className="shrink-0 text-fg-muted hover:text-red-600 dark:hover:text-red-400 transition-colors"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polyline points="3 6 5 6 21 6" />
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                </svg>
              </button>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
