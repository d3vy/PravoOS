export interface Page<T> {
  items: T[]
  total: number
}

export const DEFAULT_PAGE_SIZE = 20

export const MAX_PAGE_SIZE = 100

export function readTotal(headers: unknown, fallback: number): number {
  const raw = (headers as Record<string, string> | undefined)?.['x-total-count']
  const parsed = raw ? Number.parseInt(raw, 10) : NaN
  return Number.isFinite(parsed) ? parsed : fallback
}
