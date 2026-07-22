import { useId, type ComponentPropsWithoutRef, type ReactNode } from 'react'

interface InputProps extends ComponentPropsWithoutRef<'input'> {
  label?: string
  error?: string
  rightElement?: ReactNode
}

export function Input({ label, error, id, className = '', rightElement, ...props }: InputProps): JSX.Element {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const errorId = `${inputId}-error`

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label
          htmlFor={inputId}
          className="text-sm font-medium text-fg"
        >
          {label}
        </label>
      )}
      <div className="relative">
        <input
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          className={`
            input-base
            ${error ? 'border-red-500 focus:ring-red-500' : ''}
            ${rightElement ? 'pr-11' : ''}
            ${className}
          `}
          {...props}
        />
        {rightElement && (
          <div className="absolute inset-y-0 right-1.5 flex items-center">
            {rightElement}
          </div>
        )}
      </div>
      {error && (
        <p id={errorId} className="text-xs text-red-500 mt-0.5">{error}</p>
      )}
    </div>
  )
}
