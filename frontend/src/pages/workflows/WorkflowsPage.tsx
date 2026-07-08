import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { workflowDefinitionsApi } from '../../api/workflows'
import { casesApi } from '../../api/cases'
import { Navbar } from '../../components/layout/Navbar'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import type {
  DraftTypeInfo,
  SaveWorkflowDefinitionRequest,
  WorkflowCategory,
  WorkflowDefinitionDto,
  WorkflowStepInput,
  WorkflowStepType,
} from '../../types'

const CATEGORY_OPTIONS: { value: WorkflowCategory; label: string }[] = [
  { value: 'BANKRUPTCY', label: 'Банкротство' },
  { value: 'DEBT_COLLECTION', label: 'Взыскание задолженности' },
  { value: 'REGISTRATION', label: 'Регистрация' },
  { value: 'CUSTOM', label: 'Пользовательский' },
]

const STEP_TYPE_OPTIONS: { value: WorkflowStepType; label: string }[] = [
  { value: 'AI_ANALYSIS', label: 'AI-анализ' },
  { value: 'GENERATE_DRAFT', label: 'Генерация черновика' },
  { value: 'GENERATE_TASKS', label: 'Задачи по чеклисту' },
  { value: 'SET_DEADLINE', label: 'Дедлайн' },
]

const DEADLINE_OPTIONS = [
  { value: 'FILING_DEADLINE', label: 'Срок подачи' },
  { value: 'NEXT_HEARING', label: 'Судебное заседание' },
  { value: 'EXPIRY', label: 'Истечение срока' },
]

const SELECT_CLASS =
  'w-full px-3 py-2 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent'
const TEXTAREA_CLASS = `${SELECT_CLASS} resize-none`

type EditorState = { mode: 'new' } | { mode: 'edit'; definition: WorkflowDefinitionDto } | null

export default function WorkflowsPage(): JSX.Element {
  const queryClient = useQueryClient()
  const [editor, setEditor] = useState<EditorState>(null)

  const { data: definitions = [], isLoading, isError } = useQuery<WorkflowDefinitionDto[]>({
    queryKey: ['workflow-definitions'],
    queryFn: workflowDefinitionsApi.getAll,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => workflowDefinitionsApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workflow-definitions'] }),
  })

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
      <div className="page-container py-8 max-w-4xl">
        <div className="flex items-start justify-between gap-4 mb-8">
          <div>
            <p className="eyebrow mb-1">AI-процессы</p>
            <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text">Конструктор процессов</h1>
            <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
              Настраиваемые многошаговые сценарии. Запуск — на странице дела.
            </p>
          </div>
          {editor === null && (
            <Button size="sm" onClick={() => setEditor({ mode: 'new' })}>
              Новый процесс
            </Button>
          )}
        </div>

        {editor !== null && (
          <WorkflowEditor
            key={editor.mode === 'edit' ? editor.definition.id : 'new'}
            initial={editor.mode === 'edit' ? editor.definition : undefined}
            onClose={() => setEditor(null)}
          />
        )}

        {isLoading && (
          <div className="flex justify-center py-16">
            <Spinner size="lg" />
          </div>
        )}
        {isError && <p className="text-sm text-red-600 dark:text-red-400">Не удалось загрузить процессы.</p>}

        <div className="space-y-3 mt-2">
          {definitions.map((definition) => (
            <div
              key={definition.id}
              className="p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="text-sm font-semibold text-light-text dark:text-dark-text">{definition.name}</h3>
                    <span className="text-xs px-1.5 py-0.5 rounded bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary">
                      {definition.categoryName}
                    </span>
                    {definition.system && (
                      <span className="text-xs px-1.5 py-0.5 rounded bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary">
                        Системный
                      </span>
                    )}
                  </div>
                  {definition.description && (
                    <p className="text-xs text-light-secondary dark:text-dark-secondary mt-1">{definition.description}</p>
                  )}
                  <p className="text-xs text-light-secondary dark:text-dark-secondary mt-1">
                    {definition.steps.length} шаг(ов): {definition.steps.map((s) => s.title).join(' → ')}
                  </p>
                </div>
                <div className="flex gap-2 shrink-0">
                  <Button size="sm" variant="secondary" onClick={() => setEditor({ mode: 'edit', definition })}>
                    {definition.editable ? 'Изменить' : 'Просмотр'}
                  </Button>
                  {definition.editable && (
                    <Button
                      size="sm"
                      variant="ghost"
                      loading={deleteMutation.isPending && deleteMutation.variables === definition.id}
                      onClick={() => {
                        if (window.confirm('Удалить процесс?')) deleteMutation.mutate(definition.id)
                      }}
                    >
                      Удалить
                    </Button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function emptyStep(): WorkflowStepInput {
  return { type: 'AI_ANALYSIS', title: '', instruction: '' }
}

function WorkflowEditor({
  initial,
  onClose,
}: {
  initial?: WorkflowDefinitionDto
  onClose: () => void
}): JSX.Element {
  const queryClient = useQueryClient()
  const readOnly = initial != null && !initial.editable

  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [category, setCategory] = useState<WorkflowCategory>(initial?.category ?? 'CUSTOM')
  const [steps, setSteps] = useState<WorkflowStepInput[]>(
    initial?.steps.map((s) => ({
      type: s.type,
      title: s.title,
      instruction: s.instruction ?? '',
      draftType: s.draftType ?? undefined,
      deadlineType: s.deadlineType ?? undefined,
      deadlineOffsetDays: s.deadlineOffsetDays ?? undefined,
    })) ?? [emptyStep()]
  )

  const { data: draftTypes = [] } = useQuery<DraftTypeInfo[]>({
    queryKey: ['draft-types'],
    queryFn: casesApi.getDraftTypes,
  })

  const saveMutation = useMutation({
    mutationFn: (payload: SaveWorkflowDefinitionRequest) =>
      initial && initial.editable
        ? workflowDefinitionsApi.update(initial.id, payload)
        : workflowDefinitionsApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-definitions'] })
      onClose()
    },
  })

  const updateStep = (index: number, patch: Partial<WorkflowStepInput>): void => {
    setSteps((prev) => prev.map((step, i) => (i === index ? { ...step, ...patch } : step)))
  }

  const moveStep = (index: number, delta: number): void => {
    const target = index + delta
    if (target < 0 || target >= steps.length) return
    setSteps((prev) => {
      const next = [...prev]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  const canSave =
    name.trim().length > 0 &&
    steps.length > 0 &&
    steps.every((s) => s.title.trim().length > 0)

  const handleSave = (): void => {
    const payload: SaveWorkflowDefinitionRequest = {
      name: name.trim(),
      description: description.trim() || undefined,
      category,
      steps: steps.map((s) => ({
        type: s.type,
        title: s.title.trim(),
        instruction: s.type === 'AI_ANALYSIS' || s.type === 'GENERATE_TASKS' ? s.instruction : undefined,
        draftType: s.type === 'GENERATE_DRAFT' ? s.draftType : undefined,
        deadlineType: s.type === 'SET_DEADLINE' ? s.deadlineType : undefined,
        deadlineOffsetDays: s.type === 'SET_DEADLINE' ? s.deadlineOffsetDays : undefined,
      })),
    }
    saveMutation.mutate(payload)
  }

  return (
    <div className="mb-8 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-4">
        {initial ? (readOnly ? 'Просмотр процесса' : 'Редактирование процесса') : 'Новый процесс'}
      </h2>

      <div className="space-y-3">
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Название процесса" disabled={readOnly} />
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          maxLength={1000}
          placeholder="Описание (опционально)"
          disabled={readOnly}
          className={TEXTAREA_CLASS}
        />
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as WorkflowCategory)}
          disabled={readOnly}
          className={SELECT_CLASS}
        >
          {CATEGORY_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="mt-5 space-y-3">
        <h3 className="text-xs font-semibold text-light-secondary dark:text-dark-secondary uppercase tracking-wide">
          Шаги
        </h3>
        {steps.map((step, index) => (
          <div
            key={index}
            className="p-3 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg space-y-2"
          >
            <div className="flex items-center gap-2">
              <span className="text-xs text-light-secondary dark:text-dark-secondary w-5">{index + 1}.</span>
              <select
                value={step.type}
                onChange={(e) => updateStep(index, { type: e.target.value as WorkflowStepType })}
                disabled={readOnly}
                className={SELECT_CLASS}
              >
                {STEP_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {!readOnly && (
                <div className="flex gap-1">
                  <button type="button" onClick={() => moveStep(index, -1)} className="px-2 text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text" aria-label="Вверх">↑</button>
                  <button type="button" onClick={() => moveStep(index, 1)} className="px-2 text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text" aria-label="Вниз">↓</button>
                  <button type="button" onClick={() => setSteps((prev) => prev.filter((_, i) => i !== index))} className="px-2 text-red-600 dark:text-red-400" aria-label="Удалить шаг">✕</button>
                </div>
              )}
            </div>

            <Input
              value={step.title}
              onChange={(e) => updateStep(index, { title: e.target.value })}
              placeholder="Название шага"
              disabled={readOnly}
            />

            {(step.type === 'AI_ANALYSIS' || step.type === 'GENERATE_TASKS') && (
              <textarea
                value={step.instruction ?? ''}
                onChange={(e) => updateStep(index, { instruction: e.target.value })}
                rows={3}
                maxLength={4000}
                placeholder={step.type === 'GENERATE_TASKS' ? 'Инструкция для чеклиста (markdown-таблица Документ | Статус | Примечание)' : 'Инструкция для AI'}
                disabled={readOnly}
                className={TEXTAREA_CLASS}
              />
            )}

            {step.type === 'GENERATE_DRAFT' && (
              <select
                value={step.draftType ?? ''}
                onChange={(e) => updateStep(index, { draftType: e.target.value })}
                disabled={readOnly}
                className={SELECT_CLASS}
              >
                <option value="">— выберите тип черновика —</option>
                {draftTypes.map((type) => (
                  <option key={type.id} value={type.id}>
                    {type.displayName}
                  </option>
                ))}
              </select>
            )}

            {step.type === 'SET_DEADLINE' && (
              <div className="flex gap-2">
                <select
                  value={step.deadlineType ?? ''}
                  onChange={(e) => updateStep(index, { deadlineType: (e.target.value || undefined) as WorkflowStepInput['deadlineType'] })}
                  disabled={readOnly}
                  className={SELECT_CLASS}
                >
                  <option value="">— тип дедлайна —</option>
                  {DEADLINE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  min={0}
                  max={3650}
                  value={step.deadlineOffsetDays ?? ''}
                  onChange={(e) => updateStep(index, { deadlineOffsetDays: e.target.value === '' ? undefined : Number(e.target.value) })}
                  placeholder="Дней"
                  disabled={readOnly}
                  className={`${SELECT_CLASS} w-28`}
                />
              </div>
            )}
          </div>
        ))}

        {!readOnly && (
          <Button size="sm" variant="secondary" onClick={() => setSteps((prev) => [...prev, emptyStep()])}>
            + Добавить шаг
          </Button>
        )}
      </div>

      {saveMutation.isError && (
        <p className="text-sm text-red-600 dark:text-red-400 mt-3">Не удалось сохранить процесс. Проверьте поля шагов.</p>
      )}

      <div className="flex gap-2 mt-5">
        {!readOnly && (
          <Button onClick={handleSave} disabled={!canSave} loading={saveMutation.isPending}>
            Сохранить
          </Button>
        )}
        <Button variant="ghost" onClick={onClose}>
          {readOnly ? 'Закрыть' : 'Отмена'}
        </Button>
      </div>
    </div>
  )
}
