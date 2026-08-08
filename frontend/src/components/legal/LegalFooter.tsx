import { LegalLinks } from './LegalLinks'

export function LegalFooter(): JSX.Element {
  return (
    <footer className="px-4 pb-8">
      <LegalLinks className="justify-center" />
    </footer>
  )
}
