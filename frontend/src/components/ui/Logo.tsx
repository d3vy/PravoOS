interface LogoProps {
  className?: string
  withWordmark?: boolean
}

export function PravoIcon({ className = '' }: { className?: string }): JSX.Element {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 32 32"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <rect width="32" height="32" fill="#1e3a5f" />
      <path
        d="M7 8.5 L7 24 M25 8.5 L25 24 M5.5 8.5 L26.5 8.5 M5.5 24 L9 24 M23 24 L26.5 24"
        stroke="#ffffff"
        strokeWidth="2.4"
        strokeLinecap="square"
      />
    </svg>
  )
}

export function Logo({ className = '', withWordmark = true }: LogoProps): JSX.Element {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <PravoIcon className="w-[28px] h-[28px] rounded-sm" />
      {withWordmark && (
        <span className="font-sans text-base font-semibold tracking-tight text-light-text dark:text-dark-text">
          Pravo<span className="font-light">OS</span>
        </span>
      )}
    </span>
  )
}
