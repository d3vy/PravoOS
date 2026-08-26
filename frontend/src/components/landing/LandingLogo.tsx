import { Link } from 'react-router-dom'

interface LandingLogoProps {
  inverted?: boolean
  className?: string
}

export function LandingLogo({ inverted = false, className = '' }: LandingLogoProps): JSX.Element {
  return (
    <Link
      to="/"
      aria-label="PravoOS"
      className={`inline-flex shrink-0 items-center gap-2 ${className}`.trim()}
    >
      <span
        className={`flex h-[24px] w-[85px] items-center overflow-hidden md:h-[32px] md:w-[114px] ${
          inverted ? 'invert' : ''
        }`}
      >
        <img
          src="/assets/landing/logo-pravoos.png"
          alt="PravoOS"
          className="h-full w-full object-contain"
        />
      </span>
    </Link>
  )
}
