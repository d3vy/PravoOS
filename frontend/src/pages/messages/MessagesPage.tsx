import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { casesApi } from '../../api/cases'
import type { CaseThreadResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'
import i18n from '../../i18n'
import { PageHeader } from '../../components/ui/PageHeader'

function formatTimestamp(value: string): string {
  const date = new Date(value)
  const today = new Date()
  const sameDay =
    date.getDate() === today.getDate() &&
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear()

  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  return sameDay
    ? date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString(locale, { day: '2-digit', month: '2-digit', year: '2-digit' })
}

export default function MessagesPage(): JSX.Element {
  const { t } = useTranslation()
  const { data: threads = [], isLoading } = useQuery<CaseThreadResponse[]>({
    queryKey: ['messageThreads'],
    queryFn: casesApi.listThreads,
  })

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-3xl">
        <PageHeader title={t('messages.title')} description={t('messages.subtitle')} />

        {isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner />
          </div>
        ) : threads.length === 0 ? (
          <div className="card-elevated p-10 text-center">
            <p className="text-fg-muted text-sm">
              {t('messages.empty')}
            </p>
            <Link
              to="/cases"
              className="inline-block mt-4 text-sm text-accent hover:underline"
            >
              {t('messages.goToCases')}
            </Link>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {threads.map((thread, index) => (
              <motion.div
                key={thread.caseId}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2, delay: index * 0.03 }}
              >
                <Link
                  to={`/cases/${thread.caseId}#messages`}
                  className="card-elevated p-4 flex items-start gap-3 hover:border-accent/40 transition-colors"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-0.5">
                      <span className="font-medium text-fg truncate">
                        {thread.caseTitle}
                      </span>
                      {thread.unreadCount > 0 && (
                        <span className="shrink-0 min-w-[20px] h-5 px-1.5 rounded-full bg-accent-solid text-accent-fg text-xs font-semibold flex items-center justify-center">
                          {thread.unreadCount}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-fg-muted mb-1.5 truncate">
                      {thread.clientName ?? t('messages.noClient')}
                    </p>
                    <p className="text-sm text-fg-muted truncate">
                      <span className="opacity-70">
                        {thread.lastAuthorRole === 'LAWYER' ? t('messages.youPrefix') : ''}
                      </span>
                      {thread.lastMessagePreview}
                    </p>
                  </div>
                  <span className="shrink-0 text-xs text-fg-muted">
                    {formatTimestamp(thread.lastMessageAt)}
                  </span>
                </Link>
              </motion.div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
