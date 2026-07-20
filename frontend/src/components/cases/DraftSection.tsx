import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import { templatesApi } from '../../api/templates'
import type { CaseDraftSummaryDto, DraftTypeInfo } from '../../types'
import { Button } from '../ui/Button'

export function DraftSection({ caseId, drafts }: { caseId: string; drafts: CaseDraftSummaryDto[] }): JSX.Element {
  const queryClient = useQueryClient()
  const [selectedDraftType, setSelectedDraftType] = useState('')
  const [selectedTemplate, setSelectedTemplate] = useState('')
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const { data: draftTypes = [] } = useQuery<DraftTypeInfo[]>({
    queryKey: ['draft-types'],
    queryFn: casesApi.getDraftTypes,
  })

  const { data: templates = [] } = useQuery({
    queryKey: ['templates'],
    queryFn: templatesApi.getAll,
  })

  const generateMutation = useMutation({
    mutationFn: () => casesApi.generateDraft(caseId, { draftType: selectedDraftType }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-drafts', caseId] })
    },
  })

  const applyTemplateMutation = useMutation({
    mutationFn: () => templatesApi.applyToCase(caseId, selectedTemplate),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-drafts', caseId] })
      setSelectedTemplate('')
    },
  })

  const handleDownload = async (draft: CaseDraftSummaryDto): Promise<void> => {
    setDownloadingId(draft.id)
    try {
      await casesApi.downloadDraft(draft.id, draft.title)
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">Черновик документа</h2>

      <div className="flex flex-col gap-3">
        <select
          value={selectedDraftType}
          onChange={(e) => setSelectedDraftType(e.target.value)}
          className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
        >
          <option value="">Выберите тип документа</option>
          {draftTypes.map((type) => (
            <option key={type.id} value={type.id}>
              {type.displayName}
            </option>
          ))}
        </select>

        {generateMutation.isError && (
          <p className="text-sm text-red-600 dark:text-red-400">Ошибка генерации. Попробуйте снова.</p>
        )}

        <div>
          <Button
            variant="primary"
            disabled={!selectedDraftType}
            loading={generateMutation.isPending}
            onClick={() => generateMutation.mutate()}
          >
            Сгенерировать
          </Button>
        </div>

        <div className="pt-3 mt-1 border-t border-light-border dark:border-dark-border flex flex-col gap-2">
          <span className="text-xs font-semibold text-light-secondary dark:text-dark-secondary">
            Применить шаблон
          </span>
          {templates.length === 0 ? (
            <p className="text-xs text-light-secondary dark:text-dark-secondary">
              Шаблонов нет.{' '}
              <Link to="/templates" className="text-light-accent dark:text-dark-accent">
                Создать шаблон
              </Link>
            </p>
          ) : (
            <>
              <select
                value={selectedTemplate}
                onChange={(e) => setSelectedTemplate(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
              >
                <option value="">Выберите шаблон</option>
                {templates.map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.name}
                  </option>
                ))}
              </select>
              {applyTemplateMutation.isError && (
                <p className="text-sm text-red-600 dark:text-red-400">Не удалось применить шаблон.</p>
              )}
              <div>
                <Button
                  variant="secondary"
                  disabled={!selectedTemplate}
                  loading={applyTemplateMutation.isPending}
                  onClick={() => applyTemplateMutation.mutate()}
                >
                  Применить шаблон
                </Button>
              </div>
            </>
          )}
        </div>

        {drafts.length > 0 && (
          <div className="flex flex-col gap-2 mt-2">
            {drafts.map((draft) => (
              <div
                key={draft.id}
                className="p-3 rounded-lg border border-light-border dark:border-dark-border"
              >
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-xs font-medium text-light-text dark:text-dark-text">{draft.draftTypeName}</span>
                  <span className="text-xs text-light-secondary dark:text-dark-secondary">
                    {new Date(draft.createdAt).toLocaleString('ru-RU')}
                  </span>
                </div>
                <p className="text-xs text-light-secondary dark:text-dark-secondary mb-2 line-clamp-2">
                  {draft.title}
                </p>
                <div className="flex items-center gap-2">
                  <Link to={`/cases/${caseId}/drafts/${draft.id}`}>
                    <Button variant="primary" size="sm">
                      Редактировать
                    </Button>
                  </Link>
                  <Button
                    variant="secondary"
                    size="sm"
                    loading={downloadingId === draft.id}
                    onClick={() => void handleDownload(draft)}
                  >
                    Скачать .docx
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
