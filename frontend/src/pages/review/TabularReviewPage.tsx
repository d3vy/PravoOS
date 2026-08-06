import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { reviewApi, type ReviewExportFormat } from '../../api/review'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { SkeletonList } from '../../components/ui/Skeleton'
import { CitationDrawer } from '../../components/review/CitationDrawer'
import { NewReviewDialog } from '../../components/review/NewReviewDialog'
import { ReviewTable } from '../../components/review/ReviewTable'
import { useToast } from '../../hooks/useToast'
import { PageHeader } from '../../components/ui/PageHeader'
import type {
  CreateTabularReviewRequest,
  TabularReviewCellDto,
  TabularReviewDto,
  TabularReviewStatus,
} from '../../types'

const POLL_INTERVAL_MS = 2500
const TERMINAL_STATUSES: TabularReviewStatus[] = ['COMPLETED', 'PARTIAL', 'FAILED']

export default function TabularReviewPage(): JSX.Element {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const { reviewId } = useParams<{ reviewId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()

  const caseId = searchParams.get('caseId')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [selectedCell, setSelectedCell] = useState<TabularReviewCellDto | null>(null)

  const { data: reviews = [], isLoading: reviewsLoading } = useQuery({
    queryKey: ['tabular-reviews', caseId],
    queryFn: () => reviewApi.listByCase(caseId as string),
    enabled: Boolean(caseId) && !reviewId,
  })

  const { data: review, isLoading: reviewLoading } = useQuery<TabularReviewDto>({
    queryKey: ['tabular-review', reviewId],
    queryFn: () => reviewApi.get(reviewId as string),
    enabled: Boolean(reviewId),
    refetchInterval: (query) =>
      query.state.data && TERMINAL_STATUSES.includes(query.state.data.status) ? false : POLL_INTERVAL_MS,
  })

  useEffect(() => {
    setSelectedCell(null)
  }, [reviewId])

  const createMutation = useMutation({
    mutationFn: (request: CreateTabularReviewRequest) => reviewApi.create(request),
    onSuccess: (created) => {
      setDialogOpen(false)
      queryClient.invalidateQueries({ queryKey: ['tabular-reviews'] })
      navigate(`/review/${created.id}?caseId=${created.caseId}`)
    },
  })

  const exportMutation = useMutation({
    mutationFn: (format: ReviewExportFormat) =>
      reviewApi.download(review!.id, review!.title, format),
    onError: () => toast.error(t('review.exportFailed')),
  })

  const handleCaseChange = (nextCaseId: string): void => {
    const params = new URLSearchParams(searchParams)
    if (nextCaseId) {
      params.set('caseId', nextCaseId)
    } else {
      params.delete('caseId')
    }
    setSearchParams(params, { replace: true })
  }

  if (reviewId) {
    return (
      <div className="bg-bg">
        <div className="page-container py-8">
          {reviewLoading || !review ? (
            <SkeletonList count={4} />
          ) : (
            <>
              <PageHeader
                breadcrumbs={[
                  { label: t('review.title'), to: caseId ? `/review?caseId=${caseId}` : '/review' },
                  { label: review.title },
                ]}
                title={review.title}
                description={`${t('review.progress', { filled: review.filledCells, total: review.totalCells })} · ${t(`review.status.${review.status}`)}`}
                className="mb-6"
                actions={
                  <>
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={exportMutation.isPending}
                      onClick={() => exportMutation.mutate('xlsx')}
                    >
                      {t('review.exportXlsx')}
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={exportMutation.isPending}
                      onClick={() => exportMutation.mutate('docx')}
                    >
                      {t('review.exportDocx')}
                    </Button>
                  </>
                }
              />

              {review.errorMessage && (
                <p role="alert" className="mb-4 rounded-lg bg-danger-soft px-4 py-3 text-sm text-danger">
                  {review.errorMessage}
                </p>
              )}

              <ReviewTable review={review} selectedCell={selectedCell} onSelectCell={setSelectedCell} />

              <CitationDrawer
                cell={selectedCell}
                documentTitle={
                  review.documents.find((document) => document.documentId === selectedCell?.documentId)
                    ?.documentTitle ?? ''
                }
                question={selectedCell ? review.questions[selectedCell.questionIndex] ?? '' : ''}
                onClose={() => setSelectedCell(null)}
              />
            </>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-bg">
      <div className="page-container py-8">
        <PageHeader
          title={t('review.title')}
          description={t('review.subtitle')}
          actions={
            <Button variant="primary" size="sm" onClick={() => setDialogOpen(true)}>
              {t('review.newReview')}
            </Button>
          }
        />

        {!caseId ? (
          <EmptyState illustration="documents"
            title={t('review.pickCaseTitle')}
            description={t('review.pickCaseDescription')}
            action={{ label: t('review.newReview'), onClick: () => setDialogOpen(true) }}
          />
        ) : reviewsLoading ? (
          <SkeletonList count={4} />
        ) : reviews.length === 0 ? (
          <EmptyState illustration="documents"
            title={t('review.emptyTitle')}
            description={t('review.emptyDescription')}
            action={{ label: t('review.newReview'), onClick: () => setDialogOpen(true) }}
          />
        ) : (
          <div className="flex flex-col gap-2">
            {reviews.map((summary) => (
              <button
                key={summary.id}
                type="button"
                onClick={() => navigate(`/review/${summary.id}?caseId=${summary.caseId}`)}
                className="flex items-center gap-4 rounded-xl border border-line bg-surface p-4 text-left transition-colors hover:border-accent"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-fg">{summary.title}</p>
                  <span className="text-xs text-fg-muted">
                    {t('review.matrixSize', {
                      documents: summary.documentCount,
                      questions: summary.questionCount,
                    })}
                  </span>
                </div>
                <span className="shrink-0 text-xs text-fg-muted">{t(`review.status.${summary.status}`)}</span>
              </button>
            ))}
          </div>
        )}

        <NewReviewDialog
          open={dialogOpen}
          caseId={caseId}
          submitting={createMutation.isPending}
          onCaseChange={handleCaseChange}
          onClose={() => setDialogOpen(false)}
          onSubmit={(request) => createMutation.mutate(request)}
        />
      </div>
    </div>
  )
}
