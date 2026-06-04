interface LogoProps {
  className?: string
  withWordmark?: boolean
}

export function ScalesIcon({ className = '' }: { className?: string }): JSX.Element {
  return (
    <svg
      viewBox="0 0 28 28"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <rect x="13" y="3" width="2" height="22" fill="currentColor" rx="1" />
      <rect x="4" y="8" width="20" height="1.5" fill="currentColor" rx="0.75" />
      <circle cx="7.5" cy="16" r="3.5" stroke="currentColor" strokeWidth="1.5" fill="none" />
      <circle cx="20.5" cy="16" r="3.5" stroke="currentColor" strokeWidth="1.5" fill="none" />
    </svg>
  )
}

export function Logo({ className = '', withWordmark = true }: LogoProps): JSX.Element {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <span className="text-light-text dark:text-dark-text">
        <ScalesIcon className="w-[20px] h-[20px]" />
      </span>
      {withWordmark && (
        <span className="font-sans text-base font-semibold tracking-tight text-light-text dark:text-dark-text">
          Pravo<span className="font-light">OS</span>
        </span>
      )}
    </span>
  )
}
