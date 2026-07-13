import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { casesApi } from '../../api/cases'
import type { CaseThreadResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'

function formatTimestamp(value: string): string {
  const date = new Date(value)
  const today = new Date()
  const sameDay =
    date.getDate() === today.getDate() &&
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear()

  return sameDay
    ? date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit' })
}

export default function MessagesPage(): JSX.Element {
  const { data: threads = [], isLoading } = useQuery<CaseThreadResponse[]>({
    queryKey: ['messageThreads'],
    queryFn: casesApi.listThreads,
  })

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8 max-w-3xl">
        <div className="mb-8">
          <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Сообщения</h1>
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            Переписка с клиентами по делам
          </p>
        </div>

        {isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner />
          </div>
        ) : threads.length === 0 ? (
          <div className="card-elevated rounded-xl p-10 text-center">
            <p className="text-light-secondary dark:text-dark-secondary text-sm">
              Переписок пока нет. Откройте дело и напишите клиенту первым.
            </p>
            <Link
              to="/cases"
              className="inline-block mt-4 text-sm text-light-accent dark:text-dark-accent hover:underline"
            >
              Перейти к делам
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
                  className="card-elevated rounded-xl p-4 flex items-start gap-3 hover:border-light-accent/40 dark:hover:border-dark-accent/40 transition-colors"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-0.5">
                      <span className="font-medium text-light-text dark:text-dark-text truncate">
                        {thread.caseTitle}
                      </span>
                      {thread.unreadCount > 0 && (
                        <span className="shrink-0 min-w-[20px] h-5 px-1.5 rounded-full bg-light-accent dark:bg-dark-accent text-white dark:text-dark-bg text-xs font-semibold flex items-center justify-center">
                          {thread.unreadCount}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-light-secondary dark:text-dark-secondary mb-1.5 truncate">
                      {thread.clientName ?? 'Клиент не назначен'}
                    </p>
                    <p className="text-sm text-light-secondary dark:text-dark-secondary truncate">
                      <span className="opacity-70">
                        {thread.lastAuthorRole === 'LAWYER' ? 'Вы: ' : ''}
                      </span>
                      {thread.lastMessagePreview}
                    </p>
                  </div>
                  <span className="shrink-0 text-xs text-light-secondary dark:text-dark-secondary">
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
