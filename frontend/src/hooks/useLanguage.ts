import { useCallback, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { usersApi } from '../api/users'
import { useAuthStore } from '../store/authStore'
import { AppLanguage, normalizeLanguage, SUPPORTED_LANGUAGES } from '../i18n/config'

export function useLanguage(): {
  language: AppLanguage
  supportedLanguages: readonly AppLanguage[]
  setLanguage: (language: AppLanguage) => void
} {
  const { i18n } = useTranslation()
  const language = normalizeLanguage(i18n.language)

  const setLanguage = useCallback(
    (next: AppLanguage) => {
      if (next === normalizeLanguage(i18n.language)) return
      i18n.changeLanguage(next)
      document.documentElement.lang = next
      if (useAuthStore.getState().isAuthenticated()) {
        usersApi.updateLanguage({ language: next }).catch(() => undefined)
      }
    },
    [i18n],
  )

  return { language, supportedLanguages: SUPPORTED_LANGUAGES, setLanguage }
}

export function useLanguageSync(): void {
  const { i18n } = useTranslation()
  const isAuthenticated = useAuthStore((state) => state.accessToken !== null)

  useEffect(() => {
    if (!isAuthenticated) return
    let cancelled = false
    usersApi
      .getLanguage()
      .then((response) => {
        if (cancelled) return
        const stored = normalizeLanguage(response.language)
        if (stored !== normalizeLanguage(i18n.language)) {
          i18n.changeLanguage(stored)
          document.documentElement.lang = stored
        }
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, i18n])
}
