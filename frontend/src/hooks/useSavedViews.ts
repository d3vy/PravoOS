import { useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { savedViewsApi } from '../api/savedViews'
import type { SavedViewResponse, SavedViewScope } from '../types'

export interface SavedView<TConfig> {
  id: string
  name: string
  sharedWithTeam: boolean
  owned: boolean
  orgId: string | null
  config: TConfig | null
}

export interface SaveViewInput<TConfig> {
  name: string
  config: TConfig
  sharedWithTeam: boolean
  orgId?: string | null
}

export interface SavedViewsApi<TConfig> {
  views: SavedView<TConfig>[]
  isSaving: boolean
  saveView: (input: SaveViewInput<TConfig>) => void
  updateView: (viewId: string, input: SaveViewInput<TConfig>) => void
  deleteView: (viewId: string) => void
}

function parseConfig<TConfig>(view: SavedViewResponse): TConfig | null {
  try {
    return JSON.parse(view.config) as TConfig
  } catch {
    return null
  }
}

export function useSavedViews<TConfig>(scope: SavedViewScope): SavedViewsApi<TConfig> {
  const queryClient = useQueryClient()
  const queryKey = ['saved-views', scope]

  const { data = [] } = useQuery<SavedViewResponse[]>({
    queryKey,
    queryFn: () => savedViewsApi.list(scope),
  })

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey })
  }

  const createMutation = useMutation({
    mutationFn: (input: SaveViewInput<TConfig>) =>
      savedViewsApi.create({
        scope,
        name: input.name,
        config: JSON.stringify(input.config),
        sharedWithTeam: input.sharedWithTeam,
        orgId: input.orgId ?? null,
      }),
    onSuccess: invalidate,
  })

  const updateMutation = useMutation({
    mutationFn: ({ viewId, input }: { viewId: string; input: SaveViewInput<TConfig> }) =>
      savedViewsApi.update(viewId, {
        name: input.name,
        config: JSON.stringify(input.config),
        sharedWithTeam: input.sharedWithTeam,
        orgId: input.orgId ?? null,
      }),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: savedViewsApi.delete,
    onSuccess: invalidate,
  })

  const views = useMemo(
    () =>
      data.map((view) => ({
        id: view.id,
        name: view.name,
        sharedWithTeam: view.sharedWithTeam,
        owned: view.owned,
        orgId: view.orgId,
        config: parseConfig<TConfig>(view),
      })),
    [data]
  )

  return {
    views,
    isSaving: createMutation.isPending || updateMutation.isPending,
    saveView: (input) => createMutation.mutate(input),
    updateView: (viewId, input) => updateMutation.mutate({ viewId, input }),
    deleteView: (viewId) => deleteMutation.mutate(viewId),
  }
}
