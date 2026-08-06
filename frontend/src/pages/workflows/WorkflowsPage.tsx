import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { workflowDefinitionsApi } from '../../api/workflows'
import { casesApi } from '../../api/cases'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import { useConfirm } from '../../hooks/useConfirm'
import { PageHeader } from '../../components/ui/PageHeader'
import type {
  DraftTypeInfo,
  SaveWorkflowDefinitionRequest,
  WorkflowCategory,
  WorkflowDefinitionDto,
  WorkflowStepInput,
  WorkflowStepType,
} from '../../types'

const CATEGORY_OPTIONS: { value: WorkflowCategory; labelKey: string }[] = [
  { value: 'BANKRUPTCY', labelKey: 'workflowBuilder.catBankruptcy' },
  { value: 'DEBT_COLLECTION', labelKey: 'workflowBuilder.catDebtCollection' },
  { value: 'REGISTRATION', labelKey: 'workflowBuilder.catRegistration' },
  { value: 'CUSTOM', labelKey: 'workflowBuilder.catCustom' },
]

const STEP_TYPE_OPTIONS: { value: WorkflowStepType; labelKey: string }[] = [
  { value: 'AI_ANALYSIS', labelKey: 'workflowBuilder.stepAiAnalysis' },
  { value: 'GENERATE_DRAFT', labelKey: 'workflowBuilder.stepGenerateDraft' },
  { value: 'GENERATE_TASKS', labelKey: 'workflowBuilder.stepGenerateTasks' },
  { value: 'SET_DEADLINE', labelKey: 'workflowBuilder.stepSetDeadline' },
]

const DEADLINE_OPTIONS = [
  { value: 'FILING_DEADLINE', labelKey: 'workflowBuilder.deadlineFiling' },
  { value: 'NEXT_HEARING', labelKey: 'workflowBuilder.deadlineHearing' },
  { value: 'EXPIRY', labelKey: 'workflowBuilder.deadlineExpiry' },
]

const SELECT_CLASS =
  'w-full px-3 py-2 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent'
const TEXTAREA_CLASS = `${SELECT_CLASS} resize-none`

type EditorState = { mode: 'new' } | { mode: 'edit'; definition: WorkflowDefinitionDto } | null

export default function WorkflowsPage(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const confirm = useConfirm()
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
    <div className="bg-bg">
      <div className="page-container py-8 max-w-4xl">
        <PageHeader
          eyebrow={t('workflowBuilder.eyebrow')}
          title={t('workflowBuilder.title')}
          description={t('workflowBuilder.subtitle')}
          actions={
            editor === null && (
              <Button size="sm" onClick={() => setEditor({ mode: 'new' })}>
                {t('workflowBuilder.newProcess')}
              </Button>
            )
          }
        />

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
        {isError && <p className="text-sm text-danger">{t('workflowBuilder.loadError')}</p>}

        <div className="space-y-3 mt-2">
          {definitions.map((definition) => (
            <div
              key={definition.id}
              className="p-4 rounded-xl bg-surface border border-line"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="text-sm font-semibold text-fg">{definition.name}</h3>
                    <span className="text-xs px-1.5 py-0.5 rounded bg-bg border border-line text-fg-muted">
                      {definition.categoryName}
                    </span>
                    {definition.system && (
                      <span className="text-xs px-1.5 py-0.5 rounded bg-bg border border-line text-fg-muted">
                        {t('workflowBuilder.system')}
                      </span>
                    )}
                  </div>
                  {definition.description && (
                    <p className="text-xs text-fg-muted mt-1">{definition.description}</p>
                  )}
                  <p className="text-xs text-fg-muted mt-1">
                    {t('workflowBuilder.stepsSummary', { count: definition.steps.length, titles: definition.steps.map((s) => s.title).join(' → ') })}
                  </p>
                </div>
                <div className="flex gap-2 shrink-0">
                  <Button size="sm" variant="secondary" onClick={() => setEditor({ mode: 'edit', definition })}>
                    {definition.editable ? t('workflowBuilder.edit') : t('workflowBuilder.view')}
                  </Button>
                  {definition.editable && (
                    <Button
                      size="sm"
                      variant="ghost"
                      loading={deleteMutation.isPending && deleteMutation.variables === definition.id}
                      onClick={async () => {
                        const confirmed = await confirm({
                          title: t('workflowBuilder.delete'),
                          description: t('workflowBuilder.deleteConfirm'),
                          danger: true,
                        })
                        if (confirmed) deleteMutation.mutate(definition.id)
                      }}
                    >
                      {t('workflowBuilder.delete')}
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
  const { t } = useTranslation()
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
    <div className="mb-8 p-5 rounded-xl bg-surface border border-line">
      <h2 className="text-sm font-semibold text-fg mb-4">
        {initial ? (readOnly ? t('workflowBuilder.editorViewTitle') : t('workflowBuilder.editorEditTitle')) : t('workflowBuilder.editorNewTitle')}
      </h2>

      <div className="space-y-3">
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t('workflowBuilder.namePlaceholder')} disabled={readOnly} />
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          maxLength={1000}
          placeholder={t('workflowBuilder.descPlaceholder')}
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
              {t(option.labelKey)}
            </option>
          ))}
        </select>
      </div>

      <div className="mt-5 space-y-3">
        <h3 className="text-xs font-semibold text-fg-muted uppercase tracking-wide">
          {t('workflowBuilder.stepsHeader')}
        </h3>
        {steps.map((step, index) => (
          <div
            key={index}
            className="p-3 rounded-lg border border-line bg-bg space-y-2"
          >
            <div className="flex items-center gap-2">
              <span className="text-xs text-fg-muted w-5">{index + 1}.</span>
              <select
                value={step.type}
                onChange={(e) => updateStep(index, { type: e.target.value as WorkflowStepType })}
                disabled={readOnly}
                className={SELECT_CLASS}
              >
                {STEP_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {t(option.labelKey)}
                  </option>
                ))}
              </select>
              {!readOnly && (
                <div className="flex gap-1">
                  <button type="button" onClick={() => moveStep(index, -1)} className="px-2 text-fg-muted hover:text-fg" aria-label={t('workflowBuilder.moveUp')}>↑</button>
                  <button type="button" onClick={() => moveStep(index, 1)} className="px-2 text-fg-muted hover:text-fg" aria-label={t('workflowBuilder.moveDown')}>↓</button>
                  <button type="button" onClick={() => setSteps((prev) => prev.filter((_, i) => i !== index))} className="px-2 text-danger" aria-label={t('workflowBuilder.deleteStep')}>✕</button>
                </div>
              )}
            </div>

            <Input
              value={step.title}
              onChange={(e) => updateStep(index, { title: e.target.value })}
              placeholder={t('workflowBuilder.stepTitlePlaceholder')}
              disabled={readOnly}
            />

            {(step.type === 'AI_ANALYSIS' || step.type === 'GENERATE_TASKS') && (
              <textarea
                value={step.instruction ?? ''}
                onChange={(e) => updateStep(index, { instruction: e.target.value })}
                rows={3}
                maxLength={4000}
                placeholder={step.type === 'GENERATE_TASKS' ? t('workflowBuilder.tasksInstructionPlaceholder') : t('workflowBuilder.aiInstructionPlaceholder')}
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
                <option value="">{t('workflowBuilder.chooseDraftType')}</option>
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
                  <option value="">{t('workflowBuilder.chooseDeadlineType')}</option>
                  {DEADLINE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {t(option.labelKey)}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  min={0}
                  max={3650}
                  value={step.deadlineOffsetDays ?? ''}
                  onChange={(e) => updateStep(index, { deadlineOffsetDays: e.target.value === '' ? undefined : Number(e.target.value) })}
                  placeholder={t('workflowBuilder.daysPlaceholder')}
                  disabled={readOnly}
                  className={`${SELECT_CLASS} w-28`}
                />
              </div>
            )}
          </div>
        ))}

        {!readOnly && (
          <Button size="sm" variant="secondary" onClick={() => setSteps((prev) => [...prev, emptyStep()])}>
            {t('workflowBuilder.addStep')}
          </Button>
        )}
      </div>

      {saveMutation.isError && (
        <p className="text-sm text-danger mt-3">{t('workflowBuilder.saveError')}</p>
      )}

      <div className="flex gap-2 mt-5">
        {!readOnly && (
          <Button onClick={handleSave} disabled={!canSave} loading={saveMutation.isPending}>
            {t('workflowBuilder.save')}
          </Button>
        )}
        <Button variant="ghost" onClick={onClose}>
          {readOnly ? t('workflowBuilder.close') : t('workflowBuilder.cancel')}
        </Button>
      </div>
    </div>
  )
}
