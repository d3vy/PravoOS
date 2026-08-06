import { Link } from 'react-router-dom'
import { Button } from './Button'

interface EmptyStateAction {
  label: string
  to?: string
  onClick?: () => void
}

export type EmptyStateIllustration =
  | 'generic'
  | 'cases'
  | 'clients'
  | 'documents'
  | 'invoices'
  | 'mail'
  | 'templates'
  | 'users'
  | 'none'

interface EmptyStateProps {
  title?: string
  description: string
  action?: EmptyStateAction
  illustration?: EmptyStateIllustration
}

const SVG_PROPS = {
  viewBox: '0 0 96 96',
  fill: 'none' as const,
  stroke: 'currentColor',
  strokeWidth: 1.5,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

const ILLUSTRATIONS: Record<Exclude<EmptyStateIllustration, 'none'>, JSX.Element> = {
  generic: (
    <svg {...SVG_PROPS}>
      <rect x="18" y="26" width="60" height="46" rx="6" />
      <path d="M18 40h60" />
      <path d="M30 54h22M30 62h34" strokeOpacity="0.5" />
      <circle cx="26" cy="33" r="1.5" />
      <circle cx="33" cy="33" r="1.5" />
    </svg>
  ),
  cases: (
    <svg {...SVG_PROPS}>
      <rect x="16" y="32" width="64" height="42" rx="6" />
      <path d="M38 32v-5a4 4 0 0 1 4-4h12a4 4 0 0 1 4 4v5" />
      <path d="M16 48h64" strokeOpacity="0.5" />
      <path d="M44 48v6h8v-6" />
    </svg>
  ),
  clients: (
    <svg {...SVG_PROPS}>
      <circle cx="40" cy="38" r="10" />
      <path d="M22 70c0-9.4 8-17 18-17s18 7.6 18 17" />
      <circle cx="63" cy="43" r="7" strokeOpacity="0.5" />
      <path d="M60 68c0-7.2 4.6-13 13-13" strokeOpacity="0.5" />
    </svg>
  ),
  documents: (
    <svg {...SVG_PROPS}>
      <path d="M30 20h24l14 14v42a4 4 0 0 1-4 4H30a4 4 0 0 1-4-4V24a4 4 0 0 1 4-4z" />
      <path d="M54 20v14h14" />
      <path d="M36 48h24M36 58h24M36 68h14" strokeOpacity="0.5" />
    </svg>
  ),
  invoices: (
    <svg {...SVG_PROPS}>
      <path d="M28 20h40v56l-8-5-8 5-8-5-8 5-8-5V20z" />
      <path d="M38 36h20M38 46h20M38 56h12" strokeOpacity="0.5" />
    </svg>
  ),
  mail: (
    <svg {...SVG_PROPS}>
      <rect x="16" y="28" width="64" height="42" rx="6" />
      <path d="M16 36l30 20a4 4 0 0 0 4 0l30-20" />
    </svg>
  ),
  templates: (
    <svg {...SVG_PROPS}>
      <rect x="18" y="22" width="60" height="54" rx="6" />
      <path d="M18 38h60" />
      <rect x="28" y="48" width="18" height="18" rx="3" strokeOpacity="0.5" />
      <path d="M54 50h16M54 58h16M54 66h10" strokeOpacity="0.5" />
    </svg>
  ),
  users: (
    <svg {...SVG_PROPS}>
      <circle cx="48" cy="36" r="11" />
      <path d="M26 72c0-11 9.8-20 22-20s22 9 22 20" />
      <path d="M64 26l4 4 8-8" strokeOpacity="0.5" />
    </svg>
  ),
}

export function EmptyState({
  title,
  description,
  action,
  illustration = 'generic',
}: EmptyStateProps): JSX.Element {
  return (
    <div className="text-center py-16 px-6 rounded-xl border border-dashed border-line">
      {illustration !== 'none' && (
        <div
          aria-hidden="true"
          className="w-20 h-20 mx-auto mb-5 text-fg-muted/45 [&>svg]:w-full [&>svg]:h-full"
        >
          {ILLUSTRATIONS[illustration]}
        </div>
      )}
      {title && <p className="text-base font-medium text-fg mb-1">{title}</p>}
      <p className="text-sm text-fg-muted max-w-md mx-auto">{description}</p>
      {action && (
        <div className="mt-4">
          {action.to ? (
            <Link to={action.to}>
              <Button variant="primary" size="sm">
                {action.label}
              </Button>
            </Link>
          ) : (
            <Button variant="primary" size="sm" onClick={action.onClick}>
              {action.label}
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
