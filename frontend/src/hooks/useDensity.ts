import { useCallback, useState } from 'react'

export type Density = 'comfortable' | 'compact'

const STORAGE_KEY = 'pravoos.dashboard.density'

function readStoredDensity(): Density {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'compact' ? 'compact' : 'comfortable'
}

export function useDensity(): [Density, () => void] {
  const [density, setDensity] = useState<Density>(readStoredDensity)

  const toggle = useCallback(() => {
    setDensity((prev) => {
      const next: Density = prev === 'comfortable' ? 'compact' : 'comfortable'
      localStorage.setItem(STORAGE_KEY, next)
      return next
    })
  }, [])

  return [density, toggle]
}
