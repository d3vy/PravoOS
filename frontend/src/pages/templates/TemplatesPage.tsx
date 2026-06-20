import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { templatesApi } from '../../api/templates'
import { Navbar } from '../../components/layout/Navbar'
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
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
      <div className="page-container py-8 max-w-4xl">
        <div className="flex items-start justify-between gap-4 mb-8">
          <div>
            <p className="eyebrow mb-1">Документы</p>
            <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text">Шаблоны</h1>
          </div>
          {editor === null && (
            <Button size="sm" onClick={() => setEditor({ mode: 'new' })}>
              Новый шаблон
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
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            Не удалось загрузить шаблоны. Обновите страницу.
          </p>
        )}

        {templates && templates.length === 0 && editor === null && (
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            Шаблонов пока нет. Создайте первый — и применяйте его к делам в один клик.
          </p>
        )}

        {templates && templates.length > 0 && (
          <div className="flex flex-col gap-2 mt-2">
            {templates.map((template) => (
              <div
                key={template.id}
                className="p-4 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-light-text dark:text-dark-text">{template.name}</p>
                    <p className="text-xs text-light-secondary dark:text-dark-secondary mt-1 line-clamp-2 whitespace-pre-wrap">
                      {template.content}
                    </p>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <Button variant="secondary" size="sm" onClick={() => setEditor({ mode: 'edit', template })}>
                      Изменить
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      loading={deleteMutation.isPending && deleteMutation.variables === template.id}
                      onClick={() => deleteMutation.mutate(template.id)}
                    >
                      Удалить
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
    onError: () => setError('Не удалось сохранить шаблон. Проверьте поля.'),
  })

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    if (!name.trim()) {
      setError('Укажите название шаблона')
      return
    }
    if (!content.trim()) {
      setError('Текст шаблона не может быть пустым')
      return
    }
    setError(null)
    saveMutation.mutate()
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-6 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border flex flex-col gap-4"
    >
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">
          {initial ? 'Редактирование шаблона' : 'Новый шаблон'}
        </h2>
        <button
          type="button"
          onClick={onClose}
          className="text-xs text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text"
        >
          Отмена
        </button>
      </div>

      <Input label="Название" value={name} onChange={(e) => setName(e.target.value)} maxLength={300} />

      <div>
        <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">Текст шаблона</label>
        <textarea
          ref={contentRef}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={10}
          className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent font-mono"
        />
      </div>

      <div className="text-xs text-light-secondary dark:text-dark-secondary">
        <p className="mb-1.5">
          Пишите текст как обычно. Там, где должно подставиться имя клиента, дата или другие данные дела,
          вставьте плейсхолдер — например <code className="px-1 rounded bg-light-bg dark:bg-dark-bg">{'{{client_name}}'}</code>.
          При применении шаблона к делу он заменится на реальное значение. Нажмите на плейсхолдер, чтобы вставить его в текст.
        </p>
        <div className="flex flex-wrap gap-1.5 mt-2">
          {PLACEHOLDERS.map((placeholder) => (
            <button
              key={placeholder}
              type="button"
              onClick={() => insertPlaceholder(placeholder)}
              className="px-1.5 py-0.5 rounded bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border font-mono hover:border-light-accent dark:hover:border-dark-accent hover:text-light-text dark:hover:text-dark-text transition-colors"
            >
              {placeholder}
            </button>
          ))}
        </div>
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      <div>
        <Button type="submit" size="sm" loading={saveMutation.isPending}>
          {initial ? 'Сохранить' : 'Создать шаблон'}
        </Button>
      </div>
    </form>
  )
}
