import type { ComponentPropsWithoutRef } from 'react'

interface InputProps extends ComponentPropsWithoutRef<'input'> {
  label?: string
  error?: string
}

export function Input({ label, error, id, className = '', ...props }: InputProps): JSX.Element {
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label
          htmlFor={id}
          className="text-sm font-medium text-light-text dark:text-dark-text"
        >
          {label}
        </label>
      )}
      <input
        id={id}
        className={`
          input-base
          ${error ? 'border-red-500 focus:ring-red-500' : ''}
          ${className}
        `}
        {...props}
      />
      {error && (
        <p className="text-xs text-red-500 mt-0.5">{error}</p>
      )}
    </div>
  )
}
