export const SUPPORTED_LANGUAGES = ['ru', 'en'] as const

export type AppLanguage = (typeof SUPPORTED_LANGUAGES)[number]

export const DEFAULT_LANGUAGE: AppLanguage = 'ru'

export const LANGUAGE_STORAGE_KEY = 'pravoos-language'

export const LANGUAGE_LABELS: Record<AppLanguage, string> = {
  ru: 'Русский',
  en: 'English',
}

export function isSupportedLanguage(value: string | null | undefined): value is AppLanguage {
  return value != null && (SUPPORTED_LANGUAGES as readonly string[]).includes(value)
}

export function normalizeLanguage(value: string | null | undefined): AppLanguage {
  if (!value) return DEFAULT_LANGUAGE
  const base = value.toLowerCase().split('-')[0]
  return isSupportedLanguage(base) ? base : DEFAULT_LANGUAGE
}
