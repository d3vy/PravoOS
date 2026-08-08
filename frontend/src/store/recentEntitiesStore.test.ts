import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useRecentEntitiesStore } from './recentEntitiesStore'

describe('useRecentEntitiesStore', () => {
  beforeEach(() => {
    useRecentEntitiesStore.setState({ entries: [] })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('records a new entry with a visitedAt timestamp', () => {
    vi.useFakeTimers()
    vi.setSystemTime(1000)
    useRecentEntitiesStore.getState().record({ type: 'case', id: 'c1', label: 'Case 1', subtitle: null })
    expect(useRecentEntitiesStore.getState().entries).toEqual([
      { type: 'case', id: 'c1', label: 'Case 1', subtitle: null, visitedAt: 1000 },
    ])
  })

  it('puts the most recently recorded entry first', () => {
    useRecentEntitiesStore.getState().record({ type: 'case', id: 'c1', label: 'Case 1', subtitle: null })
    useRecentEntitiesStore.getState().record({ type: 'client', id: 'cl1', label: 'Client 1', subtitle: null })
    const entries = useRecentEntitiesStore.getState().entries
    expect(entries.map((e) => e.id)).toEqual(['cl1', 'c1'])
  })

  it('moves a re-recorded entity to the front instead of duplicating it', () => {
    useRecentEntitiesStore.getState().record({ type: 'case', id: 'c1', label: 'Case 1', subtitle: null })
    useRecentEntitiesStore.getState().record({ type: 'client', id: 'cl1', label: 'Client 1', subtitle: null })
    useRecentEntitiesStore.getState().record({ type: 'case', id: 'c1', label: 'Case 1 updated', subtitle: 'new' })

    const entries = useRecentEntitiesStore.getState().entries
    expect(entries).toHaveLength(2)
    expect(entries[0]).toMatchObject({ id: 'c1', label: 'Case 1 updated', subtitle: 'new' })
  })

  it('treats a case and a client with the same id as distinct entries', () => {
    useRecentEntitiesStore.getState().record({ type: 'case', id: 'shared-id', label: 'A case', subtitle: null })
    useRecentEntitiesStore.getState().record({ type: 'client', id: 'shared-id', label: 'A client', subtitle: null })
    expect(useRecentEntitiesStore.getState().entries).toHaveLength(2)
  })

  it('caps the list at 20 entries, dropping the oldest', () => {
    for (let i = 0; i < 25; i += 1) {
      useRecentEntitiesStore.getState().record({ type: 'case', id: `c${i}`, label: `Case ${i}`, subtitle: null })
    }
    const entries = useRecentEntitiesStore.getState().entries
    expect(entries).toHaveLength(20)
    expect(entries[0].id).toBe('c24')
    expect(entries.some((e) => e.id === 'c4')).toBe(false)
  })
})
