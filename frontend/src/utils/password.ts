import i18n from '../i18n'

export function validatePassword(password: string): string | undefined {
  if (password.length < 8) return i18n.t('auth.pwMinLength')
  if (!/[a-zA-Zа-яА-ЯёЁ]/.test(password)) return i18n.t('auth.pwNeedLetter')
  if (!/\d/.test(password)) return i18n.t('auth.pwNeedDigit')
  return undefined
}
