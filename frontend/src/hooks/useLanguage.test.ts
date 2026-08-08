import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { updateLanguageMock, getLanguageMock } = vi.hoisted(() => ({
  updateLanguageMock: vi.fn(),
  getLanguageMock: vi.fn(),
}))

vi.mock('../api/users', () => ({
  usersApi: {
    updateLanguage: updateLanguageMock,
    getLanguage: getLanguageMock,
  },
}))

import i18n from '../i18n'
import { useLanguage, useLanguageSync } from './useLanguage'
import { useAuthStore } from '../store/authStore'

describe('useLanguage', () => {
  beforeEach(async () => {
    updateLanguageMock.mockReset()
    getLanguageMock.mockReset()
    updateLanguageMock.mockResolvedValue(undefined)
    useAuthStore.setState({ accessToken: null, user: null })
    await act(async () => {
      await i18n.changeLanguage('ru')
    })
  })

  afterEach(async () => {
    await act(async () => {
      await i18n.changeLanguage('ru')
    })
  })

  it('reports the current normalized language and supported languages', () => {
    const { result } = renderHook(() => useLanguage())
    expect(result.current.language).toBe('ru')
    expect(result.current.supportedLanguages).toEqual(['ru', 'en'])
  })

  it('setLanguage switches i18n language and updates the document lang attribute', async () => {
    const { result } = renderHook(() => useLanguage())
    await act(async () => {
      result.current.setLanguage('en')
    })
    expect(i18n.language).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })

  it('setLanguage is a no-op when the language is already active', async () => {
    const { result } = renderHook(() => useLanguage())
    await act(async () => {
      result.current.setLanguage('ru')
    })
    expect(updateLanguageMock).not.toHaveBeenCalled()
  })

  it('persists the language server-side only when authenticated', async () => {
    useAuthStore.setState({ accessToken: 'token', user: { userId: 'u1', email: 'a@b.com', role: 'LAWYER' } })
    const { result } = renderHook(() => useLanguage())
    await act(async () => {
      result.current.setLanguage('en')
    })
    await waitFor(() => expect(updateLanguageMock).toHaveBeenCalledWith({ language: 'en' }))
  })

  it('does not call the API when unauthenticated', async () => {
    const { result } = renderHook(() => useLanguage())
    await act(async () => {
      result.current.setLanguage('en')
    })
    expect(updateLanguageMock).not.toHaveBeenCalled()
  })
})

describe('useLanguageSync', () => {
  beforeEach(async () => {
    updateLanguageMock.mockReset()
    getLanguageMock.mockReset()
    useAuthStore.setState({ accessToken: null, user: null })
    await act(async () => {
      await i18n.changeLanguage('ru')
    })
  })

  it('does nothing when unauthenticated', () => {
    renderHook(() => useLanguageSync())
    expect(getLanguageMock).not.toHaveBeenCalled()
  })

  it('fetches and applies the stored server-side language once authenticated', async () => {
    getLanguageMock.mockResolvedValue({ language: 'en' })
    useAuthStore.setState({ accessToken: 'token', user: { userId: 'u1', email: 'a@b.com', role: 'LAWYER' } })

    renderHook(() => useLanguageSync())

    await waitFor(() => expect(i18n.language).toBe('en'))
    expect(document.documentElement.lang).toBe('en')
  })

  it('does not change language when the server value matches the current one', async () => {
    getLanguageMock.mockResolvedValue({ language: 'ru' })
    useAuthStore.setState({ accessToken: 'token', user: { userId: 'u1', email: 'a@b.com', role: 'LAWYER' } })

    renderHook(() => useLanguageSync())

    await waitFor(() => expect(getLanguageMock).toHaveBeenCalled())
    expect(i18n.language).toBe('ru')
  })
})
