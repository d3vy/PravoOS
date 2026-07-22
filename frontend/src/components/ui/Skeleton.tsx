interface SkeletonProps {
  className?: string
}

export function Skeleton({ className = '' }: SkeletonProps): JSX.Element {
  return <div className={`animate-pulse rounded-md bg-surface-2 ${className}`} aria-hidden="true" />
}

export function SkeletonCard(): JSX.Element {
  return (
    <div className="h-full p-5 rounded-xl bg-surface border border-line">
      <div className="flex items-start justify-between gap-2 mb-3">
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="h-5 w-16 rounded-full shrink-0" />
      </div>
      <Skeleton className="h-3 w-2/5 mb-3" />
      <Skeleton className="h-3 w-full mb-1.5" />
      <Skeleton className="h-3 w-4/5" />
    </div>
  )
}

export function SkeletonCardGrid({ count = 6 }: { count?: number }): JSX.Element {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  )
}

export function SkeletonRow(): JSX.Element {
  return (
    <div className="flex items-center gap-4 p-4 rounded-xl bg-surface border border-line">
      <div className="flex-1 min-w-0 space-y-2">
        <Skeleton className="h-4 w-1/3" />
        <Skeleton className="h-3 w-1/2" />
      </div>
      <Skeleton className="h-5 w-20 rounded-full shrink-0" />
      <Skeleton className="h-4 w-16 shrink-0" />
    </div>
  )
}

export function SkeletonList({ count = 4 }: { count?: number }): JSX.Element {
  return (
    <div className="flex flex-col gap-2" aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonRow key={i} />
      ))}
    </div>
  )
}
