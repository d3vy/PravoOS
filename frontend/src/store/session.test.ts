import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'
import { useRecentEntitiesStore } from './recentEntitiesStore'
import { clearLocalSession } from './session'

describe('clearLocalSession', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ accessToken: null, user: null })
    useRecentEntitiesStore.setState({ entries: [] })
  })

  it('drops the token and the user of the finished session', () => {
    useAuthStore.getState().setSession('token-1', {
      userId: 'user-1',
      email: 'lawyer@example.com',
      role: 'LAWYER',
    })

    clearLocalSession()

    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('erases recently opened cases and clients from local storage', () => {
    useRecentEntitiesStore
      .getState()
      .record({ type: 'client', id: 'client-1', label: 'Иванов И. И.', subtitle: 'ИНН 1234567890' })

    clearLocalSession()

    expect(useRecentEntitiesStore.getState().entries).toEqual([])
    expect(localStorage.getItem('pravoos-recent-entities')).toBeNull()
  })
})
