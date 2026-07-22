import { useTranslation } from 'react-i18next'
import type { CaseStatus } from '../../types'
import { caseStatusLabel, CASE_STATUS_ORDER } from '../ui/Badge'

interface CaseStatusSelectProps {
  value: CaseStatus
  onChange: (status: CaseStatus) => void
  disabled?: boolean
}

export function CaseStatusSelect({ value, onChange, disabled }: CaseStatusSelectProps): JSX.Element {
  useTranslation()
  return (
    <select
      value={value}
      disabled={disabled}
      onClick={(e) => e.stopPropagation()}
      onChange={(e) => onChange(e.target.value as CaseStatus)}
      className="px-2.5 py-1 rounded-lg border border-line bg-bg text-fg text-xs focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-60"
    >
      {CASE_STATUS_ORDER.map((status) => (
        <option key={status} value={status}>
          {caseStatusLabel(status)}
        </option>
      ))}
    </select>
  )
}
