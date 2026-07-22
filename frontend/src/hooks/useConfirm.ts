import { useCallback } from 'react'
import { useConfirmStore, type ConfirmOptions } from '../store/confirmStore'

export function useConfirm(): (options: ConfirmOptions) => Promise<boolean> {
  const ask = useConfirmStore((state) => state.ask)
  return useCallback((options: ConfirmOptions) => ask(options), [ask])
}
