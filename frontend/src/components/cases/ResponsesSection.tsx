import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { workflowsApi } from '../../api/workflows'
import { citationsApi } from '../../api/citations'
import type { AiResponseDto } from '../../types'
import { CitationList, citationSummary } from '../ui/CitationList'
import { Button } from '../ui/Button'
import { RatingButtons } from '../ui/RatingButtons'

export function ResponsesSection({ caseId, responses }: { caseId: string; responses: AiResponseDto[] }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const rateMutation = useMutation({
    mutationFn: ({ responseId, rating }: { responseId: string; rating: number }) =>
      workflowsApi.rateResponse(responseId, { rating }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['case-responses', caseId] }),
  })

  if (responses.length === 0) {
    return (
      <section>
        <h2 className="text-sm font-semibold text-fg mb-3">{t('responses.title')}</h2>
        <p className="text-sm text-fg-muted">
          {t('responses.emptyHint')}
        </p>
      </section>
    )
  }

  return (
    <section>
      <h2 className="text-sm font-semibold text-fg mb-3">
        {t('responses.title')} <span className="font-normal text-fg-muted">({responses.length})</span>
      </h2>
      <div className="flex flex-col gap-4">
        <AnimatePresence>
          {responses.map((response) => (
            <motion.div
              key={response.id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="p-5 rounded-xl bg-surface border border-line"
            >
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-medium px-2.5 py-0.5 rounded-full bg-accent/10 text-accent">
                  {response.workflowName}
                </span>
                <span className="text-xs text-fg-muted">
                  {new Date(response.createdAt).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')}
                </span>
              </div>

              <p className="text-sm text-fg whitespace-pre-wrap mb-4">
                {response.result}
              </p>

              {response.sources.length > 0 && (
                <div className="mb-4 pt-3 border-t border-line">
                  <p className="text-xs font-semibold text-fg-muted mb-2">
                    {t('responses.sources', { count: response.sources.length })}
                  </p>
                  <div className="flex flex-col gap-2">
                    {response.sources.map((source, i) => (
                      <div key={i} className="text-xs text-fg-muted">
                        <span className="font-medium text-fg">{source.title}</span>
                        {source.fragment && <span className="block mt-0.5 opacity-80">{source.fragment}</span>}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {response.followUps && response.followUps.length > 0 && (
                <div className="mb-4 pt-3 border-t border-line">
                  <p className="text-xs font-semibold text-fg-muted mb-2">
                    {t('responses.followUps')}
                  </p>
                  <div className="flex flex-col gap-1.5">
                    {response.followUps.map((q, i) => (
                      <p key={i} className="text-xs text-fg-muted pl-2 border-l-2 border-line">
                        {q}
                      </p>
                    ))}
                  </div>
                </div>
              )}

              <CitationCheckPanel responseId={response.id} />

              <div className="flex items-center gap-4 pt-3 border-t border-line">
                <CopyButton text={response.result} />
                <div className="flex items-center gap-2 ml-auto">
                  <span className="text-xs text-fg-muted">{t('responses.ratingLabel')}</span>
                  <RatingButtons
                    rating={response.rating}
                    onRate={(rating) => rateMutation.mutate({ responseId: response.id, rating })}
                    disabled={rateMutation.isPending}
                  />
                </div>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </section>
  )
}

function CitationCheckPanel({ responseId }: { responseId: string }): JSX.Element {
  const { t } = useTranslation()
  const checkMutation = useMutation({
    mutationFn: () => citationsApi.checkResponse(responseId),
  })
  const result = checkMutation.data

  return (
    <div className="mb-4 pt-3 border-t border-line">
      <div className="flex items-center gap-3 mb-2">
        <Button
          variant="ghost"
          size="sm"
          loading={checkMutation.isPending}
          onClick={() => checkMutation.mutate()}
        >
          {t('responses.checkCitations')}
        </Button>
        {result && (
          <span className="text-xs text-fg-muted">
            {citationSummary(result)}
          </span>
        )}
      </div>

      {checkMutation.isError && (
        <p className="text-sm text-danger">{t('responses.checkError')}</p>
      )}

      {result && <CitationList result={result} />}
    </div>
  )
}

function CopyButton({ text }: { text: string }): JSX.Element {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const handleCopy = (): void => {
    void navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }
  return (
    <button
      onClick={handleCopy}
      title={t('common.copy')}
      className="inline-flex items-center gap-1 text-xs text-fg-muted hover:text-fg transition-colors"
    >
      {copied ? (
        <>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12" /></svg>
          {t('common.copied')}
        </>
      ) : (
        <>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </svg>
          {t('responses.copyLabel')}
        </>
      )}
    </button>
  )
}
