import { useRef } from 'react'
import { useTranslation } from 'react-i18next'

export interface CaseTabDescriptor {
  id: string
  label: string
  badge?: number
}

interface CaseTabsNavProps {
  tabs: CaseTabDescriptor[]
  activeTab: string
  onSelect: (tabId: string) => void
}

export function CaseTabsNav({ tabs, activeTab, onSelect }: CaseTabsNavProps): JSX.Element {
  const { t } = useTranslation()
  const buttonsRef = useRef<(HTMLButtonElement | null)[]>([])

  const handleKeyDown = (event: React.KeyboardEvent, index: number): void => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return
    event.preventDefault()
    const delta = event.key === 'ArrowRight' ? 1 : -1
    const nextIndex = (index + delta + tabs.length) % tabs.length
    const nextTab = tabs[nextIndex]
    onSelect(nextTab.id)
    buttonsRef.current[nextIndex]?.focus()
  }

  return (
    <div
      role="tablist"
      aria-label={t('cases.tabsAria')}
      className="flex gap-1 overflow-x-auto scrollbar-thin border-b border-line mb-6 -mx-1 px-1"
    >
      {tabs.map((tab, index) => {
        const isActive = tab.id === activeTab
        return (
          <button
            key={tab.id}
            ref={(el) => (buttonsRef.current[index] = el)}
            role="tab"
            type="button"
            id={`case-tab-${tab.id}`}
            aria-selected={isActive}
            aria-controls={`case-panel-${tab.id}`}
            tabIndex={isActive ? 0 : -1}
            onClick={() => onSelect(tab.id)}
            onKeyDown={(e) => handleKeyDown(e, index)}
            className={`shrink-0 flex items-center gap-2 px-3.5 py-2.5 -mb-px border-b-2 text-sm font-medium whitespace-nowrap transition-colors ${
              isActive
                ? 'border-accent text-fg'
                : 'border-transparent text-fg-muted hover:text-fg'
            }`}
          >
            {tab.label}
            {tab.badge !== undefined && tab.badge > 0 && (
              <span
                className={`inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-medium ${
                  isActive
                    ? 'bg-accent/15 text-accent'
                    : 'bg-bg text-fg-muted'
                }`}
              >
                {tab.badge}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}
