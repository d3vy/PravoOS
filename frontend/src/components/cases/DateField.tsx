interface DateFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
}

export function DateField({ label, value, onChange }: DateFieldProps): JSX.Element {
  return (
    <div>
      <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">
        {label} <span className="text-light-secondary dark:text-dark-secondary font-normal">(опционально)</span>
      </label>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
      />
    </div>
  )
}
