import { useTranslation } from 'react-i18next'

interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const sizeClasses = {
  sm: 'w-4 h-4 border-2',
  md: 'w-6 h-6 border-2',
  lg: 'w-10 h-10 border-3',
}

export function Spinner({ size = 'md', className = '' }: SpinnerProps): JSX.Element {
  const { t } = useTranslation()
  return (
    <div
      className={`
        ${sizeClasses[size]}
        rounded-full
        border-light-border dark:border-dark-border
        border-t-light-accent dark:border-t-dark-accent
        animate-spin
        ${className}
      `}
      role="status"
      aria-label={t('common.loading')}
    />
  )
}
