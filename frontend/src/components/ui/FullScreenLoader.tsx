import { Logo } from './Logo'
import { Spinner } from './Spinner'

export function FullScreenLoader(): JSX.Element {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-6 bg-light-bg dark:bg-dark-bg">
      <Logo />
      <Spinner size="lg" />
    </div>
  )
}
