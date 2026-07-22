import { Link } from 'react-router-dom'
import { Button } from './Button'

interface EmptyStateAction {
  label: string
  to?: string
  onClick?: () => void
}

interface EmptyStateProps {
  title?: string
  description: string
  action?: EmptyStateAction
}

export function EmptyState({ title, description, action }: EmptyStateProps): JSX.Element {
  return (
    <div className="text-center py-16 px-6 rounded-xl border border-dashed border-line">
      {title && (
        <p className="text-base font-medium text-fg mb-1">{title}</p>
      )}
      <p className="text-sm text-fg-muted max-w-md mx-auto">{description}</p>
      {action && (
        <div className="mt-4">
          {action.to ? (
            <Link to={action.to}>
              <Button variant="primary" size="sm">{action.label}</Button>
            </Link>
          ) : (
            <Button variant="primary" size="sm" onClick={action.onClick}>{action.label}</Button>
          )}
        </div>
      )}
    </div>
  )
}
