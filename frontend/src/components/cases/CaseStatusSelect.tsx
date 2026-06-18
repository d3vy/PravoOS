import type { CaseStatus } from '../../types'
import { CASE_STATUS_CONFIG, CASE_STATUS_ORDER } from '../ui/Badge'

interface CaseStatusSelectProps {
  value: CaseStatus
  onChange: (status: CaseStatus) => void
  disabled?: boolean
}

export function CaseStatusSelect({ value, onChange, disabled }: CaseStatusSelectProps): JSX.Element {
  return (
    <select
      value={value}
      disabled={disabled}
      onClick={(e) => e.stopPropagation()}
      onChange={(e) => onChange(e.target.value as CaseStatus)}
      className="px-2.5 py-1 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-xs focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent disabled:opacity-60"
    >
      {CASE_STATUS_ORDER.map((status) => (
        <option key={status} value={status}>
          {CASE_STATUS_CONFIG[status].label}
        </option>
      ))}
    </select>
  )
}
