import { useTranslation } from 'react-i18next'

export function SkipLink(): JSX.Element {
  const { t } = useTranslation()
  return (
    <a
      href="#main-content"
      className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-[200] focus:rounded-lg focus:bg-accent-solid focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-accent-fg focus:shadow-card"
    >
      {t('common.skipToContent')}
    </a>
  )
}
