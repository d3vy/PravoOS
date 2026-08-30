import { useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import i18n from '../../i18n'
import { templatesApi } from '../../api/templates'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { QueryState } from '../../components/ui/QueryState'
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable'
import { TableToolbar } from '../../components/ui/TableToolbar'
import { EmptyState } from '../../components/ui/EmptyState'
import { useDensity } from '../../hooks/useDensity'
import { useTablePreferences } from '../../hooks/useTablePreferences'
import type { TemplateResponse } from '../../types'
import { PageHeader } from '../../components/ui/PageHeader'

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
  const [density, toggleDensity] = useDensity()
  const { preferences, setSort, toggleColumn } = useTablePreferences('templates', {
    visibleColumnIds: ['name', 'length', 'createdAt'],
    sort: [],
    groupBy: null,
  })

  const { data: templates, isLoading, isError } = useQuery({
    queryKey: ['templates'],
    queryFn: templatesApi.getAll,
  })

  const deleteMutation = useMutation({
    mutationFn: (templateId: string) => templatesApi.delete(templateId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['templates'] }),
  })

  const columns = useMemo<DataTableColumn<TemplateResponse>[]>(
    () => [
      {
        id: 'name',
        header: t('templates.columnName'),
        alwaysVisible: true,
        sortable: true,
        width: 'minmax(0, 2fr)',
        value: (row) => row.name,
      },
      {
        id: 'length',
        header: t('templates.columnLength'),
        sortable: true,
        align: 'right',
        width: '8rem',
        value: (row) => row.content.length,
      },
      {
        id: 'createdAt',
        header: t('templates.columnCreated'),
        sortable: true,
        width: '9rem',
        value: (row) => row.createdAt,
        render: (row) =>
          new Date(row.createdAt).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'),
      },
    ],
    [t]
  )

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-4xl">
        <PageHeader
          eyebrow={t('templates.eyebrow')}
          title={t('templates.title')}
          actions={
            editor === null && (
              <Button size="sm" onClick={() => setEditor({ mode: 'new' })}>
                {t('templates.newTemplate')}
              </Button>
            )
          }
        />

        {editor !== null && (
          <TemplateEditor
            key={editor.mode === 'edit' ? editor.template.id : 'new'}
            initial={editor.mode === 'edit' ? editor.template : undefined}
            onClose={() => setEditor(null)}
          />
        )}

        <QueryState
          isLoading={isLoading}
          isError={isError}
          errorMessage={t('templates.loadError')}
        />

        {templates && (
          <>
            <div className="flex justify-end mb-3">
              <TableToolbar
                columns={columns}
                visibleColumnIds={preferences.visibleColumnIds}
                onToggleColumn={toggleColumn}
                density={density}
                onDensityToggle={toggleDensity}
              />
            </div>
            <DataTable
              rows={templates}
              columns={columns}
              rowId={(row) => row.id}
              onRowClick={(template) => setEditor({ mode: 'edit', template })}
              sort={preferences.sort}
              onSortChange={setSort}
              visibleColumnIds={preferences.visibleColumnIds}
              density={density}
              rowActions={(template) => (
                <>
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
                </>
              )}
              emptyState={<EmptyState illustration="templates" description={t('templates.empty')} />}
            />
          </>
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
      className="mb-6 p-6 rounded-2xl bg-surface border border-line flex flex-col gap-4"
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
          className="w-full px-3 py-2.5 rounded-xl border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent font-mono"
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
