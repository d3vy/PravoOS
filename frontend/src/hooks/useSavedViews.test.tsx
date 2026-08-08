import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { SavedViewResponse } from '../types'

const { listMock, createMock, updateMock, deleteMock } = vi.hoisted(() => ({
  listMock: vi.fn(),
  createMock: vi.fn(),
  updateMock: vi.fn(),
  deleteMock: vi.fn(),
}))

vi.mock('../api/savedViews', () => ({
  savedViewsApi: {
    list: listMock,
    create: createMock,
    update: updateMock,
    delete: deleteMock,
  },
}))

import { useSavedViews } from './useSavedViews'

function makeWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

const rawView: SavedViewResponse = {
  id: 'view-1',
  scope: 'CASES',
  name: 'My cases',
  config: JSON.stringify({ status: 'OPEN' }),
  sharedWithTeam: false,
  owned: true,
  orgId: null,
  updatedAt: '2026-08-01T10:00:00',
}

describe('useSavedViews', () => {
  beforeEach(() => {
    listMock.mockReset()
    createMock.mockReset()
    updateMock.mockReset()
    deleteMock.mockReset()
  })

  it('parses the JSON config of each view', async () => {
    listMock.mockResolvedValue([rawView])
    const { result } = renderHook(() => useSavedViews('CASES'), { wrapper: makeWrapper() })

    await waitFor(() => expect(result.current.views).toHaveLength(1))
    expect(result.current.views[0]).toMatchObject({
      id: 'view-1',
      name: 'My cases',
      config: { status: 'OPEN' },
    })
  })

  it('falls back to a null config when stored JSON is malformed', async () => {
    listMock.mockResolvedValue([{ ...rawView, config: '{not json' }])
    const { result } = renderHook(() => useSavedViews('CASES'), { wrapper: makeWrapper() })

    await waitFor(() => expect(result.current.views).toHaveLength(1))
    expect(result.current.views[0].config).toBeNull()
  })

  it('saveView serializes config and calls create with the given scope', async () => {
    listMock.mockResolvedValue([])
    createMock.mockResolvedValue(rawView)
    const { result } = renderHook(() => useSavedViews('CASES'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.views).toEqual([]))

    act(() => {
      result.current.saveView({ name: 'New view', config: { status: 'OPEN' }, sharedWithTeam: true })
    })

    await waitFor(() =>
      expect(createMock).toHaveBeenCalledWith({
        scope: 'CASES',
        name: 'New view',
        config: JSON.stringify({ status: 'OPEN' }),
        sharedWithTeam: true,
        orgId: null,
      })
    )
  })

  it('updateView serializes config and calls update with the view id', async () => {
    listMock.mockResolvedValue([])
    updateMock.mockResolvedValue(rawView)
    const { result } = renderHook(() => useSavedViews('CASES'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.views).toEqual([]))

    act(() => {
      result.current.updateView('view-1', { name: 'Renamed', config: { status: 'CLOSED' }, sharedWithTeam: false })
    })

    await waitFor(() =>
      expect(updateMock).toHaveBeenCalledWith('view-1', {
        name: 'Renamed',
        config: JSON.stringify({ status: 'CLOSED' }),
        sharedWithTeam: false,
        orgId: null,
      })
    )
  })

  it('deleteView calls delete with the view id', async () => {
    listMock.mockResolvedValue([])
    deleteMock.mockResolvedValue(undefined)
    const { result } = renderHook(() => useSavedViews('CASES'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.views).toEqual([]))

    act(() => {
      result.current.deleteView('view-1')
    })

    await waitFor(() => expect(deleteMock).toHaveBeenCalled())
    expect(deleteMock.mock.calls[0][0]).toBe('view-1')
  })

  it('isSaving reflects a pending create mutation', async () => {
    listMock.mockResolvedValue([])
    let resolveCreate: (value: SavedViewResponse) => void = () => undefined
    createMock.mockReturnValue(
      new Promise<SavedViewResponse>((resolve) => {
        resolveCreate = resolve
      })
    )
    const { result } = renderHook(() => useSavedViews('CASES'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.views).toEqual([]))

    act(() => {
      result.current.saveView({ name: 'New view', config: {}, sharedWithTeam: false })
    })

    await waitFor(() => expect(result.current.isSaving).toBe(true))

    await act(async () => {
      resolveCreate(rawView)
    })

    await waitFor(() => expect(result.current.isSaving).toBe(false))
  })
})
