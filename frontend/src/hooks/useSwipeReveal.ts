import { useRef, useState, type TouchEvent } from 'react'

interface SwipeRevealHandlers {
  offsetX: number
  dragging: boolean
  onTouchStart: (event: TouchEvent<HTMLElement>) => void
  onTouchMove: (event: TouchEvent<HTMLElement>) => void
  onTouchEnd: () => void
  reset: () => void
}

const SWIPE_ACTIVATION_PX = 8

export function useSwipeReveal(revealWidth: number): SwipeRevealHandlers {
  const [open, setOpen] = useState(false)
  const [offsetX, setOffsetX] = useState(0)
  const [dragging, setDragging] = useState(false)
  const startX = useRef(0)
  const startY = useRef(0)
  const axisLocked = useRef<'x' | 'y' | null>(null)

  const onTouchStart = (event: TouchEvent<HTMLElement>): void => {
    startX.current = event.touches[0].clientX
    startY.current = event.touches[0].clientY
    axisLocked.current = null
    setDragging(true)
  }

  const onTouchMove = (event: TouchEvent<HTMLElement>): void => {
    const deltaX = event.touches[0].clientX - startX.current
    const deltaY = event.touches[0].clientY - startY.current

    if (!axisLocked.current) {
      if (Math.abs(deltaX) < SWIPE_ACTIVATION_PX && Math.abs(deltaY) < SWIPE_ACTIVATION_PX) return
      axisLocked.current = Math.abs(deltaX) > Math.abs(deltaY) ? 'x' : 'y'
    }
    if (axisLocked.current !== 'x') return

    const base = open ? -revealWidth : 0
    const next = Math.min(0, Math.max(-revealWidth, base + deltaX))
    setOffsetX(next)
  }

  const onTouchEnd = (): void => {
    setDragging(false)
    const shouldOpen = offsetX < -revealWidth / 2
    setOpen(shouldOpen)
    setOffsetX(shouldOpen ? -revealWidth : 0)
  }

  const reset = (): void => {
    setOpen(false)
    setOffsetX(0)
  }

  return { offsetX, dragging, onTouchStart, onTouchMove, onTouchEnd, reset }
}
