import { useTranslation } from 'react-i18next'
import { Spinner } from './Spinner'

interface QueryStateProps {
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
  spinnerSize?: 'sm' | 'md' | 'lg'
}

export function QueryState({
  isLoading = false,
  isError = false,
  errorMessage,
  spinnerSize = 'md',
}: QueryStateProps): JSX.Element | null {
  const { t } = useTranslation()

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size={spinnerSize} />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="card-elevated rounded-xl p-8 text-center text-fg-muted" role="alert">
        {errorMessage ?? t('common.loadError')}
      </div>
    )
  }

  return null
}
