import { useTranslation } from 'react-i18next'

interface DateFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
}

export function DateField({ label, value, onChange }: DateFieldProps): JSX.Element {
  const { t } = useTranslation()
  return (
    <div>
      <label className="block text-sm font-medium text-fg mb-1.5">
        {label} <span className="text-fg-muted font-normal">{t('cases.optionalHint')}</span>
      </label>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
      />
    </div>
  )
}
