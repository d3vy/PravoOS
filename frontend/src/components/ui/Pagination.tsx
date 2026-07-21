import { useTranslation } from 'react-i18next'

interface PaginationProps {
  page: number
  pageSize: number
  total: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, pageSize, total, onPageChange }: PaginationProps): JSX.Element | null {
  const { t } = useTranslation()
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  if (totalPages <= 1) {
    return null
  }

  const buttonClass =
    'px-3 py-1.5 rounded-lg text-sm font-medium border border-light-border dark:border-dark-border ' +
    'text-light-text dark:text-dark-text disabled:opacity-40 disabled:cursor-not-allowed ' +
    'hover:bg-light-bg dark:hover:bg-dark-bg transition-colors'

  return (
    <div className="flex items-center justify-center gap-3 mt-6">
      <button className={buttonClass} disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
        {t('pagination.prev')}
      </button>
      <span className="text-sm text-light-secondary dark:text-dark-secondary">
        {t('pagination.info', { page: page + 1, total: totalPages, count: total })}
      </span>
      <button
        className={buttonClass}
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        {t('pagination.next')}
      </button>
    </div>
  )
}
