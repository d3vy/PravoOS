interface LogoProps {
  className?: string
  withWordmark?: boolean
}

export function ScalesIcon({ className = '' }: { className?: string }): JSX.Element {
  return (
    <svg
      viewBox="0 0 32 32"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="square"
      className={className}
      aria-hidden="true"
    >
      <path d="M7 8.5 L7 24 M25 8.5 L25 24 M5.5 8.5 L26.5 8.5 M5.5 24 L9 24 M23 24 L26.5 24" />
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
