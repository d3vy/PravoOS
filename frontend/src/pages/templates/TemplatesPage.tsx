import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { templatesApi } from '../../api/templates'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import type { TemplateResponse } from '../../types'

const PLACEHOLDERS = [
  '{{client_name}}',
  '{{client_phone}}',
  '{{client_email}}',
  '{{client_inn}}',
  '{{case_title}}',
  '{{case_description}}',
  '{{filing_deadline}}',
  '{{next_hearing_date}}',
  '{{expires_at}}',
  '{{today}}',
]

type EditorState = { mode: 'new' } | { mode: 'edit'; template: TemplateResponse } | null

export default function TemplatesPage(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [editor, setEditor] = useState<EditorState>(null)

  const { data: templates, isLoading, isError } = useQuery({
    queryKey: ['templates'],
    queryFn: templatesApi.getAll,
  })

  const deleteMutation = useMutation({
    mutationFn: (templateId: string) => templatesApi.delete(templateId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['templates'] }),
  })

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-4xl">
        <div className="flex items-start justify-between gap-4 mb-8">
          <div>
            <p className="eyebrow mb-1">{t('templates.eyebrow')}</p>
            <h1 className="text-3xl font-semibold text-fg">{t('templates.title')}</h1>
          </div>
          {editor === null && (
            <Button size="sm" onClick={() => setEditor({ mode: 'new' })}>
              {t('templates.newTemplate')}
            </Button>
          )}
        </div>

        {editor !== null && (
          <TemplateEditor
            key={editor.mode === 'edit' ? editor.template.id : 'new'}
            initial={editor.mode === 'edit' ? editor.template : undefined}
            onClose={() => setEditor(null)}
          />
        )}

        {isLoading && (
          <div className="flex justify-center py-16">
            <Spinner />
          </div>
        )}

        {isError && (
          <p className="text-sm text-fg-muted">
            {t('templates.loadError')}
          </p>
        )}

        {templates && templates.length === 0 && editor === null && (
          <p className="text-sm text-fg-muted">
            {t('templates.empty')}
          </p>
        )}

        {templates && templates.length > 0 && (
          <div className="flex flex-col gap-2 mt-2">
            {templates.map((template) => (
              <div
                key={template.id}
                className="p-4 rounded-lg bg-surface border border-line"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-fg">{template.name}</p>
                    <p className="text-xs text-fg-muted mt-1 line-clamp-2 whitespace-pre-wrap">
                      {template.content}
                    </p>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <Button variant="secondary" size="sm" onClick={() => setEditor({ mode: 'edit', template })}>
                      {t('templates.edit')}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      loading={deleteMutation.isPending && deleteMutation.variables === template.id}
                      onClick={() => deleteMutation.mutate(template.id)}
                    >
                      {t('templates.delete')}
                    </Button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function TemplateEditor({
  initial,
  onClose,
}: {
  initial?: TemplateResponse
  onClose: () => void
}): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState(initial?.name ?? '')
  const [content, setContent] = useState(initial?.content ?? '')
  const [error, setError] = useState<string | null>(null)
  const contentRef = useRef<HTMLTextAreaElement>(null)

  const insertPlaceholder = (placeholder: string): void => {
    const textarea = contentRef.current
    if (!textarea) {
      setContent((current) => current + placeholder)
      return
    }
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const nextContent = content.slice(0, start) + placeholder + content.slice(end)
    setContent(nextContent)
    requestAnimationFrame(() => {
      const caret = start + placeholder.length
      textarea.focus()
      textarea.setSelectionRange(caret, caret)
    })
  }

  const saveMutation = useMutation({
    mutationFn: () =>
      initial
        ? templatesApi.update(initial.id, { name: name.trim(), content })
        : templatesApi.create({ name: name.trim(), content }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['templates'] })
      onClose()
    },
    onError: () => setError(t('templates.saveError')),
  })

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    if (!name.trim()) {
      setError(t('templates.nameRequired'))
      return
    }
    if (!content.trim()) {
      setError(t('templates.contentRequired'))
      return
    }
    setError(null)
    saveMutation.mutate()
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-6 p-6 rounded-xl bg-surface border border-line flex flex-col gap-4"
    >
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-fg">
          {initial ? t('templates.editTitle') : t('templates.newTemplate')}
        </h2>
        <button
          type="button"
          onClick={onClose}
          className="text-xs text-fg-muted hover:text-fg"
        >
          {t('templates.cancel')}
        </button>
      </div>

      <Input label={t('templates.nameLabel')} value={name} onChange={(e) => setName(e.target.value)} maxLength={300} />

      <div>
        <label className="block text-sm font-medium text-fg mb-1.5">{t('templates.contentLabel')}</label>
        <textarea
          ref={contentRef}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={10}
          className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent font-mono"
        />
      </div>

      <div className="text-xs text-fg-muted">
        <p className="mb-1.5">
          {t('templates.helpTextBefore')} <code className="px-1 rounded bg-bg">{'{{client_name}}'}</code>.{' '}
          {t('templates.helpTextAfter')}
        </p>
        <div className="flex flex-wrap gap-1.5 mt-2">
          {PLACEHOLDERS.map((placeholder) => (
            <button
              key={placeholder}
              type="button"
              onClick={() => insertPlaceholder(placeholder)}
              className="px-1.5 py-0.5 rounded bg-bg border border-line font-mono hover:border-accent hover:text-fg transition-colors"
            >
              {placeholder}
            </button>
          ))}
        </div>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <div>
        <Button type="submit" size="sm" loading={saveMutation.isPending}>
          {initial ? t('templates.save') : t('templates.create')}
        </Button>
      </div>
    </form>
  )
}
