import apiClient from './client'
import { DEFAULT_PAGE_SIZE, readTotal, type Page } from './pagination'
import type { RecycleBinEntry, RecycleBinFilterParams } from '../types'

function buildParams(
  filter: RecycleBinFilterParams,
  page: number,
  size: number
): Record<string, string | number> {
  const params: Record<string, string | number> = { page, size }
  if (filter.area) params.area = filter.area
  if (filter.deletedByRole) params.deletedByRole = filter.deletedByRole
  if (filter.from) params.from = filter.from
  if (filter.to) params.to = filter.to
  if (filter.q && filter.q.trim()) params.q = filter.q.trim()
  return params
}

export const recycleBinApi = {
  list: async (
    filter: RecycleBinFilterParams = {},
    page = 0,
    size = DEFAULT_PAGE_SIZE
  ): Promise<Page<RecycleBinEntry>> => {
    const response = await apiClient.get<RecycleBinEntry[]>('/api/ai/recycle-bin', {
      params: buildParams(filter, page, size),
    })
    return { items: response.data, total: readTotal(response.headers, response.data.length) }
  },

  items: async (entryId: string): Promise<RecycleBinEntry[]> => {
    const response = await apiClient.get<RecycleBinEntry[]>(
      `/api/ai/recycle-bin/${entryId}/items`
    )
    return response.data
  },

  restore: async (entryId: string): Promise<void> => {
    await apiClient.post(`/api/ai/recycle-bin/${entryId}/restore`)
  },
}
