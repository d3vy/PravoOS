interface LogoProps {
  className?: string
  withWordmark?: boolean
}

export function ScalesIcon({ className = '' }: { className?: string }): JSX.Element {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M12 3.5v17" />
      <path d="M7.5 20.5h9" />
      <path d="M4 7.5h16" />
      <circle cx="12" cy="5" r="1" fill="currentColor" stroke="none" />
      <path d="M4 7.5 1.5 13h5L4 7.5z" />
      <path d="M20 7.5 17.5 13h5L20 7.5z" />
      <path d="M1.5 13a2.5 2.5 0 0 0 5 0" />
      <path d="M17.5 13a2.5 2.5 0 0 0 5 0" />
    </svg>
  )
}

export function Logo({ className = '', withWordmark = true }: LogoProps): JSX.Element {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <span className="text-light-gold dark:text-dark-gold">
        <ScalesIcon className="w-[22px] h-[22px]" />
      </span>
      {withWordmark && (
        <span className="font-display text-lg font-semibold tracking-tight text-light-text dark:text-dark-text">
          Pravo<span className="text-light-accent dark:text-dark-accent">OS</span>
        </span>
      )}
    </span>
  )
}
