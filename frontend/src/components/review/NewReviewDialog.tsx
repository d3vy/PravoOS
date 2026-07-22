import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { casesApi } from '../../api/cases'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { Spinner } from '../ui/Spinner'
import type { CreateTabularReviewRequest, DocumentResponse } from '../../types'

interface NewReviewDialogProps {
  open: boolean
  caseId: string | null
  submitting: boolean
  onCaseChange: (caseId: string) => void
  onClose: () => void
  onSubmit: (request: CreateTabularReviewRequest) => void
}

const MAX_QUESTIONS = 8
const MAX_DOCUMENTS = 15
const DEFAULT_QUESTION_COUNT = 3

export function NewReviewDialog({
  open,
  caseId,
  submitting,
  onCaseChange,
  onClose,
  onSubmit,
}: NewReviewDialogProps): JSX.Element {
  const { t } = useTranslation()
  const [title, setTitle] = useState('')
  const [questions, setQuestions] = useState<string[]>(Array(DEFAULT_QUESTION_COUNT).fill(''))
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([])
  const [formError, setFormError] = useState<string | null>(null)

  const { data: casesPage, isLoading: casesLoading } = useQuery({
    queryKey: ['review-cases'],
    queryFn: () => casesApi.list(undefined, undefined, 0, 100),
    enabled: open,
  })

  const { data: documents = [], isLoading: documentsLoading } = useQuery<DocumentResponse[]>({
    queryKey: ['review-case-documents', caseId],
    queryFn: () => casesApi.getDocuments(caseId as string),
    enabled: open && Boolean(caseId),
  })

  useEffect(() => {
    setSelectedDocumentIds([])
  }, [caseId])

  useEffect(() => {
    if (!open) {
      setTitle('')
      setQuestions(Array(DEFAULT_QUESTION_COUNT).fill(''))
      setSelectedDocumentIds([])
      setFormError(null)
    }
  }, [open])

  const readyDocuments = documents.filter((document) => document.status === 'READY')

  const toggleDocument = (documentId: string): void => {
    setSelectedDocumentIds((current) =>
      current.includes(documentId)
        ? current.filter((id) => id !== documentId)
        : current.length >= MAX_DOCUMENTS
          ? current
          : [...current, documentId]
    )
  }

  const updateQuestion = (index: number, value: string): void => {
    setQuestions((current) => current.map((question, i) => (i === index ? value : question)))
  }

  const handleSubmit = (): void => {
    const filledQuestions = questions.map((question) => question.trim()).filter(Boolean)
    if (!caseId) {
      setFormError(t('review.errorNoCase'))
      return
    }
    if (selectedDocumentIds.length === 0) {
      setFormError(t('review.errorNoDocuments'))
      return
    }
    if (filledQuestions.length === 0) {
      setFormError(t('review.errorNoQuestions'))
      return
    }
    setFormError(null)
    onSubmit({
      caseId,
      title: title.trim() || undefined,
      documentIds: selectedDocumentIds,
      questions: filledQuestions,
    })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('review.newReview')}
      size="lg"
      footer={
        <>
          <Button variant="secondary" size="sm" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button variant="primary" size="sm" loading={submitting} onClick={handleSubmit}>
            {t('review.runReview')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-5">
        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium uppercase tracking-wide text-fg-muted">
            {t('review.caseLabel')}
          </span>
          {casesLoading ? (
            <Spinner />
          ) : (
            <select
              value={caseId ?? ''}
              onChange={(event) => onCaseChange(event.target.value)}
              className="rounded-lg border border-line bg-surface px-3 py-2 text-sm text-fg focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            >
              <option value="">{t('review.casePlaceholder')}</option>
              {(casesPage?.items ?? []).map((caseItem) => (
                <option key={caseItem.id} value={caseItem.id}>
                  {caseItem.title}
                </option>
              ))}
            </select>
          )}
        </label>

        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium uppercase tracking-wide text-fg-muted">
            {t('review.titleLabel')}
          </span>
          <input
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={300}
            placeholder={t('review.titlePlaceholder')}
            className="rounded-lg border border-line bg-surface px-3 py-2 text-sm text-fg placeholder:text-fg-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          />
        </label>

        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium uppercase tracking-wide text-fg-muted">
            {t('review.documentsLabel', { selected: selectedDocumentIds.length, max: MAX_DOCUMENTS })}
          </span>
          {documentsLoading ? (
            <Spinner />
          ) : !caseId ? (
            <p className="text-sm text-fg-muted">{t('review.selectCaseFirst')}</p>
          ) : readyDocuments.length === 0 ? (
            <p className="text-sm text-fg-muted">{t('review.noReadyDocuments')}</p>
          ) : (
            <ul className="flex max-h-56 flex-col gap-1 overflow-y-auto rounded-lg border border-line p-2 scrollbar-thin">
              {readyDocuments.map((document) => (
                <li key={document.id}>
                  <label className="flex cursor-pointer items-start gap-2 rounded-md px-2 py-1.5 hover:bg-surface">
                    <input
                      type="checkbox"
                      checked={selectedDocumentIds.includes(document.id)}
                      onChange={() => toggleDocument(document.id)}
                      className="mt-0.5 accent-[rgb(var(--accent-solid))]"
                    />
                    <span className="min-w-0 text-sm text-fg [overflow-wrap:anywhere]">{document.title}</span>
                  </label>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium uppercase tracking-wide text-fg-muted">
            {t('review.questionsLabel')}
          </span>
          <div className="flex flex-col gap-2">
            {questions.map((question, index) => (
              <input
                key={index}
                value={question}
                onChange={(event) => updateQuestion(index, event.target.value)}
                maxLength={300}
                placeholder={t(`review.questionPlaceholder${Math.min(index, 2)}`)}
                className="rounded-lg border border-line bg-surface px-3 py-2 text-sm text-fg placeholder:text-fg-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
              />
            ))}
          </div>
          {questions.length < MAX_QUESTIONS && (
            <button
              type="button"
              onClick={() => setQuestions((current) => [...current, ''])}
              className="self-start text-sm text-accent hover:underline"
            >
              {t('review.addQuestion')}
            </button>
          )}
        </div>

        {formError && (
          <p role="alert" className="text-sm text-danger">
            {formError}
          </p>
        )}
      </div>
    </Modal>
  )
}
