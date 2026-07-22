import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type RecentEntityType = 'case' | 'client'

export interface RecentEntity {
  type: RecentEntityType
  id: string
  label: string
  subtitle: string | null
  visitedAt: number
}

const MAX_ENTRIES = 20

interface RecentEntitiesState {
  entries: RecentEntity[]
  record: (entity: Omit<RecentEntity, 'visitedAt'>) => void
}

export const useRecentEntitiesStore = create<RecentEntitiesState>()(
  persist(
    (set, get) => ({
      entries: [],
      record: (entity) => {
        const withoutExisting = get().entries.filter(
          (item) => !(item.type === entity.type && item.id === entity.id)
        )
        set({
          entries: [{ ...entity, visitedAt: Date.now() }, ...withoutExisting].slice(0, MAX_ENTRIES),
        })
      },
    }),
    { name: 'pravoos-recent-entities', version: 1 }
  )
)
