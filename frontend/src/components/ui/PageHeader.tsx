import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export interface Breadcrumb {
  label: string
  to?: string
}

interface PageHeaderProps {
  title: ReactNode
  eyebrow?: ReactNode
  description?: ReactNode
  breadcrumbs?: Breadcrumb[]
  titleSuffix?: ReactNode
  actions?: ReactNode
  size?: 'md' | 'lg'
  className?: string
}

const TITLE_SIZE = {
  md: 'text-2xl',
  lg: 'text-3xl sm:text-4xl',
} as const

export function PageHeader({
  title,
  eyebrow,
  description,
  breadcrumbs,
  titleSuffix,
  actions,
  size = 'lg',
  className = 'mb-8',
}: PageHeaderProps) {
  const { t } = useTranslation()

  return (
    <header className={className}>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <nav aria-label={t('nav.breadcrumbs')} className="mb-3">
          <ol className="flex flex-wrap items-center gap-1.5 text-xs text-fg-muted">
            {breadcrumbs.map((crumb, index) => {
              const isLast = index === breadcrumbs.length - 1
              return (
                <li key={`${crumb.label}-${index}`} className="flex items-center gap-1.5">
                  {crumb.to && !isLast ? (
                    <Link to={crumb.to} className="hover:text-accent transition-colors">
                      {crumb.label}
                    </Link>
                  ) : (
                    <span aria-current={isLast ? 'page' : undefined}>{crumb.label}</span>
                  )}
                  {!isLast && (
                    <span aria-hidden="true" className="text-line">
                      /
                    </span>
                  )}
                </li>
              )
            })}
          </ol>
        </nav>
      )}

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          {eyebrow && <p className="eyebrow mb-1">{eyebrow}</p>}
          <div className="flex flex-wrap items-center gap-3">
            <h1 className={`${TITLE_SIZE[size]} font-display tracking-tight text-fg [overflow-wrap:anywhere]`}>
              {title}
            </h1>
            {titleSuffix}
          </div>
          {description && <p className="mt-1 text-sm text-fg-muted">{description}</p>}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </header>
  )
}
