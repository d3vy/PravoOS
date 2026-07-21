import i18n from '../i18n'

export function formatMoney(amount: number, currency = 'RUB'): string {
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount)
}

export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return i18n.t('common.durationMin', { n: rest })
  if (rest === 0) return i18n.t('common.durationHours', { n: hours })
  return i18n.t('common.durationHoursMin', { h: hours, m: rest })
}

export function parseHoursToMinutes(hours: string): number {
  const value = Number(hours.replace(',', '.'))
  if (!Number.isFinite(value) || value <= 0) return 0
  return Math.round(value * 60)
}
