import { useTranslation } from 'react-i18next'
import type {
  ReviewAnswerConfidence,
  TabularReviewCellDto,
  TabularReviewDto,
  TabularReviewStatus,
} from '../../types'

interface ReviewTableProps {
  review: TabularReviewDto
  selectedCell: TabularReviewCellDto | null
  onSelectCell: (cell: TabularReviewCellDto) => void
}

const CONFIDENCE_DOT: Record<ReviewAnswerConfidence, string> = {
  HIGH: 'bg-success',
  MEDIUM: 'bg-warning',
  LOW: 'bg-warning',
  NOT_FOUND: 'bg-fg-muted/40',
}

function cellKey(documentId: string, questionIndex: number): string {
  return `${documentId}:${questionIndex}`
}

export function ReviewTable({ review, selectedCell, onSelectCell }: ReviewTableProps): JSX.Element {
  const { t } = useTranslation()

  const cellIndex = new Map<string, TabularReviewCellDto>()
  review.cells.forEach((cell) => cellIndex.set(cellKey(cell.documentId, cell.questionIndex), cell))

  const selectedKey = selectedCell
    ? cellKey(selectedCell.documentId, selectedCell.questionIndex)
    : null

  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-surface scrollbar-thin">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr>
            <th
              scope="col"
              className="sticky left-0 z-10 min-w-[220px] max-w-[280px] bg-surface-2 px-4 py-3 text-left align-top text-xs font-semibold uppercase tracking-wide text-fg-muted border-b border-r border-line"
            >
              {t('review.documentColumn')}
            </th>
            {review.questions.map((question, index) => (
              <th
                key={index}
                scope="col"
                className="min-w-[220px] bg-surface-2 px-4 py-3 text-left align-top text-xs font-semibold text-fg border-b border-line [overflow-wrap:anywhere]"
              >
                {question}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {review.documents.map((document) => (
            <tr key={document.documentId} className="group">
              <th
                scope="row"
                className="sticky left-0 z-10 min-w-[220px] max-w-[280px] bg-surface px-4 py-3 text-left align-top font-medium text-fg border-b border-r border-line"
              >
                <span className="block [overflow-wrap:anywhere]">{document.documentTitle}</span>
                <DocumentStatusHint status={document.status} error={document.errorMessage} />
              </th>
              {review.questions.map((_, questionIndex) => {
                const cell = cellIndex.get(cellKey(document.documentId, questionIndex))
                if (!cell) {
                  return (
                    <td
                      key={questionIndex}
                      className="min-w-[220px] px-4 py-3 align-top border-b border-line"
                    >
                      <PendingCell status={document.status} />
                    </td>
                  )
                }
                const isSelected = selectedKey === cellKey(cell.documentId, cell.questionIndex)
                return (
                  <td key={questionIndex} className="min-w-[220px] p-0 align-top border-b border-line">
                    <button
                      type="button"
                      onClick={() => onSelectCell(cell)}
                      aria-label={t('review.openCitation')}
                      className={`h-full w-full px-4 py-3 text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                        isSelected ? 'bg-accent/10' : 'hover:bg-surface-2'
                      }`}
                    >
                      <span className="flex items-start gap-2">
                        <span
                          className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${CONFIDENCE_DOT[cell.confidence]}`}
                          aria-hidden="true"
                        />
                        <span
                          className={`min-w-0 [overflow-wrap:anywhere] ${
                            cell.confidence === 'NOT_FOUND' ? 'text-fg-muted' : 'text-fg'
                          }`}
                        >
                          {cell.answer}
                        </span>
                      </span>
                      {cell.citations.length > 0 && (
                        <span className="mt-2 block text-xs text-accent">
                          {t('review.citationCount', { count: cell.citations.length })}
                        </span>
                      )}
                    </button>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function PendingCell({ status }: { status: TabularReviewStatus }): JSX.Element {
  const { t } = useTranslation()
  if (status === 'FAILED') {
    return <span className="text-xs text-danger">{t('review.cellFailed')}</span>
  }
  return <span className="block h-3 w-2/3 animate-pulse rounded bg-surface-2" aria-label={t('review.cellPending')} />
}

function DocumentStatusHint({
  status,
  error,
}: {
  status: TabularReviewStatus
  error: string | null
}): JSX.Element | null {
  const { t } = useTranslation()
  if (status === 'FAILED') {
    return <span className="mt-1 block text-xs text-danger [overflow-wrap:anywhere]">{error ?? t('review.documentFailed')}</span>
  }
  if (status === 'RUNNING') {
    return <span className="mt-1 block text-xs text-fg-muted">{t('review.documentRunning')}</span>
  }
  return null
}
