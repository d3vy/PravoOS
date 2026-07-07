import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { CaseTaskResponse } from '../../types'
import { Button } from '../ui/Button'

export function CaseTasksSection({ caseId }: { caseId: string }): JSX.Element {
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
      setGenerateError(
        created.length === 0
          ? 'AI не нашёл недостающих документов для добавления задач.'
          : null
      )
    },
    onError: () => setGenerateError('Не удалось сгенерировать задачи. Попробуйте снова.'),
  })

  const handleAdd = (): void => {
    if (!text.trim()) return
    createMutation.mutate()
  }

  const openCount = tasks.filter((task) => !task.done).length

  return (
    <section className="mb-10">
      <div className="flex items-center justify-between gap-3 mb-3">
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">
          Задачи по делу{' '}
          {tasks.length > 0 && (
            <span className="font-normal text-light-secondary dark:text-dark-secondary">
              ({openCount} активн., {tasks.length} всего)
            </span>
          )}
        </h2>
        <Button
          variant="secondary"
          size="sm"
          title="AI проверит по материалам дела, каких документов не хватает, и добавит задачи на их подготовку"
          loading={generateMutation.isPending}
          onClick={() => generateMutation.mutate()}
        >
          AI: задачи по недостающим документам
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
          placeholder="Новая задача…"
          className="flex-1 px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
        />
        <input
          type="date"
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          className="px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
        />
        <Button variant="primary" disabled={!text.trim()} loading={createMutation.isPending} onClick={handleAdd}>
          Добавить
        </Button>
      </div>

      {generateError && <p className="text-sm text-red-600 dark:text-red-400 mb-3">{generateError}</p>}

      {tasks.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Задач пока нет. Добавьте вручную или дайте AI проверить, каких документов не хватает по делу.
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          {tasks.map((task) => (
            <div
              key={task.id}
              className="flex items-center gap-3 p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <input
                type="checkbox"
                checked={task.done}
                disabled={toggleMutation.isPending}
                onChange={() => toggleMutation.mutate(task)}
                className="h-4 w-4 shrink-0 accent-light-accent dark:accent-dark-accent cursor-pointer"
              />
              <div className="flex-1 min-w-0">
                <p
                  className={`text-sm ${
                    task.done
                      ? 'line-through text-light-secondary dark:text-dark-secondary'
                      : 'text-light-text dark:text-dark-text'
                  }`}
                >
                  {task.text}
                </p>
                {task.dueDate && (
                  <span className="text-xs text-light-secondary dark:text-dark-secondary">
                    до {new Date(task.dueDate).toLocaleDateString('ru-RU')}
                  </span>
                )}
              </div>
              <button
                type="button"
                title="Удалить задачу"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(task.id)}
                className="shrink-0 text-light-secondary dark:text-dark-secondary hover:text-red-600 dark:hover:text-red-400 transition-colors"
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
