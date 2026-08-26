import { useEffect, type RefObject } from 'react'

export function useScrollToBottom(anchorRef: RefObject<HTMLElement>, trigger: unknown[]): void {
  useEffect(() => {
    if (trigger.length === 0) return
    anchorRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [anchorRef, trigger])
}
