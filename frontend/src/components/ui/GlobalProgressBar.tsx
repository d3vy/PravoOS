import { useIsFetching, useIsMutating } from '@tanstack/react-query'

export function GlobalProgressBar(): JSX.Element | null {
  const isFetching = useIsFetching()
  const isMutating = useIsMutating()

  if (isFetching === 0 && isMutating === 0) return null

  return (
    <div className="fixed top-0 inset-x-0 z-50 h-0.5 overflow-hidden bg-transparent" role="status" aria-label="loading">
      <div className="h-full w-1/3 bg-accent-solid animate-progress-bar" />
    </div>
  )
}
