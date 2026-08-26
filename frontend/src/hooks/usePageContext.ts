import { useEffect } from 'react'
import { usePageContextStore, type PageContext } from '../store/pageContextStore'

export function usePageContext(context: PageContext | null): void {
  const route = context?.route
  const label = context?.label
  const entityType = context?.entityType
  const entityId = context?.entityId

  useEffect(() => {
    if (!route || !label) return
    usePageContextStore.getState().setContext({ route, label, entityType, entityId })
    return () => usePageContextStore.getState().clearContext(route)
  }, [route, label, entityType, entityId])
}
