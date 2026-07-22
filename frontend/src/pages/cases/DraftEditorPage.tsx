import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { CaseDraftDto, CaseDraftVersionDto } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'

const AI_PRESET_KEYS: { labelKey: string; instructionKey: string }[] = [
  { labelKey: 'draftEditor.presetStrengthenLabel', instructionKey: 'draftEditor.presetStrengthenInstruction' },
  { labelKey: 'draftEditor.presetSimplifyLabel', instructionKey: 'draftEditor.presetSimplifyInstruction' },
  { labelKey: 'draftEditor.presetReduceRisksLabel', instructionKey: 'draftEditor.presetReduceRisksInstruction' },
  { labelKey: 'draftEditor.presetFormalLabel', instructionKey: 'draftEditor.presetFormalInstruction' },
]

function locale(): string {
  return i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
}

interface Selection {
  start: number
  end: number
}

export default function DraftEditorPage(): JSX.Element {
  const { t } = useTranslation()
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
      <div className="bg-bg">
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!draft) {
    return (
      <div className="bg-bg">
        <div className="page-container py-16 text-center">
          <p className="text-fg-muted mb-4">{t('draftEditor.notFound')}</p>
          <Link to={`/cases/${caseId}`} className="text-accent text-sm">
            {t('draftEditor.backToCase')}
          </Link>
        </div>
      </div>
    )
  }

  const hasSelection = selection != null && selection.end > selection.start

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-6xl">
        <div className="flex items-center justify-between mb-6 gap-4 flex-wrap">
          <div>
            <Link to={`/cases/${caseId}`} className="text-xs text-accent">
              {t('draftEditor.backToCase')}
            </Link>
            <h1 className="text-lg font-semibold text-fg mt-1">{draft.title}</h1>
            <p className="text-xs text-fg-muted">
              {draft.draftTypeName}
              {draft.updatedAt && t('draftEditor.changedAt', { date: new Date(draft.updatedAt).toLocaleString(locale()) })}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" onClick={() => setShowVersions((v) => !v)}>
              {t('draftEditor.versionHistory')}
            </Button>
            <Button variant="secondary" size="sm" loading={downloading} onClick={() => void handleDownload()}>
              {t('draftEditor.downloadDocx')}
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
              className="w-full min-h-[60vh] px-4 py-3 rounded-xl border border-line bg-surface text-fg text-sm font-mono leading-relaxed focus:outline-none focus:ring-2 focus:ring-accent resize-y"
            />
            <div className="flex items-center gap-3 flex-wrap">
              <input
                type="text"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder={t('draftEditor.notePlaceholder')}
                className="flex-1 min-w-[200px] px-3 py-2 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              />
              <Button
                variant="primary"
                disabled={!isDirty}
                loading={saveMutation.isPending}
                onClick={() => saveMutation.mutate()}
              >
                {t('draftEditor.saveVersion')}
              </Button>
            </div>
            {saveMutation.isError && (
              <p className="text-sm text-danger">{t('draftEditor.saveError')}</p>
            )}
            {isDirty && !saveMutation.isPending && (
              <p className="text-xs text-warning">{t('draftEditor.unsavedChanges')}</p>
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
              <section className="p-4 rounded-xl bg-surface border border-line">
                <h2 className="text-sm font-semibold text-fg mb-1">{t('draftEditor.aiAssistant')}</h2>
                <p className="text-xs text-fg-muted mb-3">
                  {hasSelection ? t('draftEditor.appliesToSelection') : t('draftEditor.appliesToDocument')}
                </p>

                <div className="flex flex-col gap-2">
                  {AI_PRESET_KEYS.map((preset) => (
                    <Button
                      key={preset.labelKey}
                      variant="secondary"
                      size="sm"
                      disabled={refineMutation.isPending}
                      onClick={() => runRefine(t(preset.instructionKey))}
                    >
                      {t(preset.labelKey)}
                    </Button>
                  ))}
                </div>

                <div className="mt-3 flex flex-col gap-2">
                  <textarea
                    value={customInstruction}
                    onChange={(e) => setCustomInstruction(e.target.value)}
                    placeholder={t('draftEditor.customPlaceholder')}
                    rows={3}
                    className="w-full px-3 py-2 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent resize-y"
                  />
                  <Button
                    variant="primary"
                    size="sm"
                    disabled={!customInstruction.trim() || refineMutation.isPending}
                    onClick={() => runRefine(customInstruction)}
                  >
                    {t('draftEditor.applyInstruction')}
                  </Button>
                </div>

                {refineMutation.isPending && (
                  <div className="flex items-center gap-2 mt-3 text-xs text-fg-muted">
                    <Spinner size="sm" /> {t('draftEditor.refining')}
                  </div>
                )}
                {refineMutation.isError && (
                  <p className="text-sm text-danger mt-3">{t('draftEditor.refineError')}</p>
                )}

                {suggestion && (
                  <div className="mt-4 pt-3 border-t border-line">
                    <h3 className="text-xs font-semibold text-fg mb-2">
                      {t('draftEditor.suggestionTitle')} {suggestion.selection ? t('draftEditor.forFragment') : t('draftEditor.wholeDocument')}
                    </h3>
                    <div className="max-h-72 overflow-y-auto p-3 rounded-lg bg-bg border border-line text-xs whitespace-pre-wrap text-fg">
                      {suggestion.text}
                    </div>
                    <div className="flex items-center gap-2 mt-2">
                      <Button variant="primary" size="sm" onClick={applySuggestion}>
                        {t('draftEditor.apply')}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setSuggestion(null)}>
                        {t('draftEditor.reject')}
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
  const { t } = useTranslation()
  const { data: versions = [], isLoading } = useQuery<CaseDraftVersionDto[]>({
    queryKey: ['draft-versions', draftId],
    queryFn: () => casesApi.getDraftVersions(draftId),
  })

  const restoreMutation = useMutation({
    mutationFn: (versionId: string) => casesApi.restoreDraftVersion(draftId, versionId),
    onSuccess: onRestored,
  })

  return (
    <section className="p-4 rounded-xl bg-surface border border-line">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-fg">{t('draftEditor.versionHistory')}</h2>
        <button onClick={onClose} className="text-xs text-fg-muted hover:text-fg">
          {t('draftEditor.close')}
        </button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-6"><Spinner size="sm" /></div>
      ) : versions.length === 0 ? (
        <p className="text-xs text-fg-muted">{t('draftEditor.noVersions')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          {versions.map((version) => (
            <div key={version.id} className="p-3 rounded-lg border border-line">
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-medium text-fg">
                  {t('draftEditor.versionNo', { no: version.versionNo })}
                </span>
                <span className="text-xs text-fg-muted">
                  {new Date(version.createdAt).toLocaleString(locale())}
                </span>
              </div>
              {version.note && (
                <p className="text-xs text-fg-muted mb-2">{version.note}</p>
              )}
              <Button
                variant="secondary"
                size="sm"
                loading={restoreMutation.isPending && restoreMutation.variables === version.id}
                onClick={() => restoreMutation.mutate(version.id)}
              >
                {t('draftEditor.restore')}
              </Button>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
