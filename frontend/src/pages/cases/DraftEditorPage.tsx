import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { CaseDraftDto, CaseDraftVersionDto } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'

const AI_PRESETS: { label: string; instruction: string }[] = [
  { label: 'Усилить формулировки', instruction: 'Усиль юридические формулировки, сделай позицию более убедительной и защищённой, сохранив исходный смысл.' },
  { label: 'Упростить язык', instruction: 'Упрости язык, убери канцелярит и двусмысленности, сохранив юридическую точность.' },
  { label: 'Снизить риски', instruction: 'Найди и устрани юридические риски и невыгодные формулировки для стороны, которую представляет юрист.' },
  { label: 'Сделать формальнее', instruction: 'Сделай текст более официальным и выдержанным в деловом юридическом стиле.' },
]

interface Selection {
  start: number
  end: number
}

export default function DraftEditorPage(): JSX.Element {
  const { caseId = '', draftId = '' } = useParams()
  const queryClient = useQueryClient()
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const [content, setContent] = useState('')
  const [loadedId, setLoadedId] = useState('')
  const [note, setNote] = useState('')
  const [customInstruction, setCustomInstruction] = useState('')
  const [selection, setSelection] = useState<Selection | null>(null)
  const [suggestion, setSuggestion] = useState<{ text: string; selection: Selection | null } | null>(null)
  const [showVersions, setShowVersions] = useState(false)
  const [downloading, setDownloading] = useState(false)

  const { data: draft, isLoading } = useQuery<CaseDraftDto>({
    queryKey: ['draft', draftId],
    queryFn: () => casesApi.getDraft(draftId),
    enabled: draftId !== '',
  })

  useEffect(() => {
    if (draft && draft.id !== loadedId) {
      setContent(draft.content)
      setLoadedId(draft.id)
    }
  }, [draft, loadedId])

  const isDirty = draft != null && content !== draft.content

  const saveMutation = useMutation({
    mutationFn: () => casesApi.updateDraft(draftId, { content, note: note.trim() || undefined }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['draft', draftId], updated)
      queryClient.invalidateQueries({ queryKey: ['draft-versions', draftId] })
      queryClient.invalidateQueries({ queryKey: ['case-drafts', caseId] })
      setNote('')
    },
  })

  const refineMutation = useMutation({
    mutationFn: (instruction: string) => {
      const activeSelection = captureSelection()
      const selectedText =
        activeSelection && activeSelection.end > activeSelection.start
          ? content.slice(activeSelection.start, activeSelection.end)
          : undefined
      return casesApi
        .refineDraft(draftId, { instruction, selectedText })
        .then((response) => ({ response, activeSelection: selectedText ? activeSelection : null }))
    },
    onSuccess: ({ response, activeSelection }) => {
      setSuggestion({ text: response.revisedText, selection: activeSelection })
    },
  })

  const captureSelection = (): Selection | null => {
    const el = textareaRef.current
    if (!el) return null
    const start = el.selectionStart
    const end = el.selectionEnd
    setSelection(end > start ? { start, end } : null)
    return end > start ? { start, end } : null
  }

  const runRefine = (instruction: string): void => {
    if (!instruction.trim() || refineMutation.isPending) return
    setSuggestion(null)
    refineMutation.mutate(instruction.trim())
  }

  const applySuggestion = (): void => {
    if (!suggestion) return
    if (suggestion.selection) {
      const { start, end } = suggestion.selection
      setContent(content.slice(0, start) + suggestion.text + content.slice(end))
    } else {
      setContent(suggestion.text)
    }
    setSuggestion(null)
    setSelection(null)
  }

  const handleDownload = async (): Promise<void> => {
    if (!draft) return
    setDownloading(true)
    try {
      await casesApi.downloadDraft(draft.id, draft.title)
    } finally {
      setDownloading(false)
    }
  }

  if (isLoading) {
    return (
      <div className="bg-light-bg dark:bg-dark-bg">
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!draft) {
    return (
      <div className="bg-light-bg dark:bg-dark-bg">
        <div className="page-container py-16 text-center">
          <p className="text-light-secondary dark:text-dark-secondary mb-4">Черновик не найден</p>
          <Link to={`/cases/${caseId}`} className="text-light-accent dark:text-dark-accent text-sm">
            ← К делу
          </Link>
        </div>
      </div>
    )
  }

  const hasSelection = selection != null && selection.end > selection.start

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8 max-w-6xl">
        <div className="flex items-center justify-between mb-6 gap-4 flex-wrap">
          <div>
            <Link to={`/cases/${caseId}`} className="text-xs text-light-accent dark:text-dark-accent">
              ← К делу
            </Link>
            <h1 className="text-lg font-semibold text-light-text dark:text-dark-text mt-1">{draft.title}</h1>
            <p className="text-xs text-light-secondary dark:text-dark-secondary">
              {draft.draftTypeName}
              {draft.updatedAt && ` · изменён ${new Date(draft.updatedAt).toLocaleString('ru-RU')}`}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" onClick={() => setShowVersions((v) => !v)}>
              История версий
            </Button>
            <Button variant="secondary" size="sm" loading={downloading} onClick={() => void handleDownload()}>
              Скачать .docx
            </Button>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 flex flex-col gap-3">
            <textarea
              ref={textareaRef}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              onSelect={captureSelection}
              spellCheck={false}
              className="w-full min-h-[60vh] px-4 py-3 rounded-xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text text-sm font-mono leading-relaxed focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-y"
            />
            <div className="flex items-center gap-3 flex-wrap">
              <input
                type="text"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Описание правки (необязательно)"
                className="flex-1 min-w-[200px] px-3 py-2 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
              />
              <Button
                variant="primary"
                disabled={!isDirty}
                loading={saveMutation.isPending}
                onClick={() => saveMutation.mutate()}
              >
                Сохранить версию
              </Button>
            </div>
            {saveMutation.isError && (
              <p className="text-sm text-red-600 dark:text-red-400">Не удалось сохранить. Попробуйте снова.</p>
            )}
            {isDirty && !saveMutation.isPending && (
              <p className="text-xs text-amber-600 dark:text-amber-400">Есть несохранённые изменения.</p>
            )}
          </div>

          <div className="flex flex-col gap-4">
            {showVersions ? (
              <VersionsPanel draftId={draftId} onClose={() => setShowVersions(false)} onRestored={(updated) => {
                queryClient.setQueryData(['draft', draftId], updated)
                setContent(updated.content)
                setLoadedId('')
                queryClient.invalidateQueries({ queryKey: ['draft-versions', draftId] })
                queryClient.invalidateQueries({ queryKey: ['case-drafts', caseId] })
              }} />
            ) : (
              <section className="p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
                <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-1">AI-помощник</h2>
                <p className="text-xs text-light-secondary dark:text-dark-secondary mb-3">
                  {hasSelection ? 'Правка применится к выделенному фрагменту.' : 'Правка применится ко всему документу. Выделите фрагмент, чтобы изменить только его.'}
                </p>

                <div className="flex flex-col gap-2">
                  {AI_PRESETS.map((preset) => (
                    <Button
                      key={preset.label}
                      variant="secondary"
                      size="sm"
                      disabled={refineMutation.isPending}
                      onClick={() => runRefine(preset.instruction)}
                    >
                      {preset.label}
                    </Button>
                  ))}
                </div>

                <div className="mt-3 flex flex-col gap-2">
                  <textarea
                    value={customInstruction}
                    onChange={(e) => setCustomInstruction(e.target.value)}
                    placeholder="Своя инструкция, напр.: добавить условие о неустойке 0,1% за день просрочки"
                    rows={3}
                    className="w-full px-3 py-2 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-y"
                  />
                  <Button
                    variant="primary"
                    size="sm"
                    disabled={!customInstruction.trim() || refineMutation.isPending}
                    onClick={() => runRefine(customInstruction)}
                  >
                    Применить инструкцию
                  </Button>
                </div>

                {refineMutation.isPending && (
                  <div className="flex items-center gap-2 mt-3 text-xs text-light-secondary dark:text-dark-secondary">
                    <Spinner size="sm" /> AI дорабатывает текст…
                  </div>
                )}
                {refineMutation.isError && (
                  <p className="text-sm text-red-600 dark:text-red-400 mt-3">Не удалось получить правку. Попробуйте снова.</p>
                )}

                {suggestion && (
                  <div className="mt-4 pt-3 border-t border-light-border dark:border-dark-border">
                    <h3 className="text-xs font-semibold text-light-text dark:text-dark-text mb-2">
                      Предложение AI {suggestion.selection ? '(для фрагмента)' : '(весь документ)'}
                    </h3>
                    <div className="max-h-72 overflow-y-auto p-3 rounded-lg bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-xs whitespace-pre-wrap text-light-text dark:text-dark-text">
                      {suggestion.text}
                    </div>
                    <div className="flex items-center gap-2 mt-2">
                      <Button variant="primary" size="sm" onClick={applySuggestion}>
                        Применить
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setSuggestion(null)}>
                        Отклонить
                      </Button>
                    </div>
                  </div>
                )}
              </section>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

function VersionsPanel({
  draftId,
  onClose,
  onRestored,
}: {
  draftId: string
  onClose: () => void
  onRestored: (updated: CaseDraftDto) => void
}): JSX.Element {
  const { data: versions = [], isLoading } = useQuery<CaseDraftVersionDto[]>({
    queryKey: ['draft-versions', draftId],
    queryFn: () => casesApi.getDraftVersions(draftId),
  })

  const restoreMutation = useMutation({
    mutationFn: (versionId: string) => casesApi.restoreDraftVersion(draftId, versionId),
    onSuccess: onRestored,
  })

  return (
    <section className="p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">История версий</h2>
        <button onClick={onClose} className="text-xs text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text">
          Закрыть
        </button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-6"><Spinner size="sm" /></div>
      ) : versions.length === 0 ? (
        <p className="text-xs text-light-secondary dark:text-dark-secondary">Версий пока нет.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {versions.map((version) => (
            <div key={version.id} className="p-3 rounded-lg border border-light-border dark:border-dark-border">
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-medium text-light-text dark:text-dark-text">
                  Версия #{version.versionNo}
                </span>
                <span className="text-xs text-light-secondary dark:text-dark-secondary">
                  {new Date(version.createdAt).toLocaleString('ru-RU')}
                </span>
              </div>
              {version.note && (
                <p className="text-xs text-light-secondary dark:text-dark-secondary mb-2">{version.note}</p>
              )}
              <Button
                variant="secondary"
                size="sm"
                loading={restoreMutation.isPending && restoreMutation.variables === version.id}
                onClick={() => restoreMutation.mutate(version.id)}
              >
                Восстановить
              </Button>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
