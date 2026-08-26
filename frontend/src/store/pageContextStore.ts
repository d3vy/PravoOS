import { create } from 'zustand'
import type { PageContextRef } from '../types'

export type PageContextEntityType = 'CASE' | 'DOCUMENT' | 'CLIENT' | 'INVOICE' | 'DRAFT'

export interface PageContext {
  route: string
  label: string
  entityType?: PageContextEntityType
  entityId?: string
}

interface PageContextState {
  context: PageContext | null
  setContext: (context: PageContext) => void
  clearContext: (route: string) => void
  toRequest: () => PageContextRef | undefined
}

export const usePageContextStore = create<PageContextState>((set, get) => ({
  context: null,

  setContext: (context) => set({ context }),

  clearContext: (route) =>
    set((state) => (state.context?.route === route ? { context: null } : state)),

  toRequest: () => {
    const context = get().context
    if (!context) return undefined
    return {
      route: context.route,
      entityType: context.entityType,
      entityId: context.entityId,
    }
  },
}))
