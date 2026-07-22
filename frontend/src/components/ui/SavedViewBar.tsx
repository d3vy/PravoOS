import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { SavedView } from '../../hooks/useSavedViews'

interface SavedViewBarProps<TConfig> {
  views: SavedView<TConfig>[]
  activeViewId: string | null
  canShare: boolean
  onApply: (view: SavedView<TConfig>) => void
  onSave: (name: string, sharedWithTeam: boolean) => void
  onDelete: (viewId: string) => void
}

export function SavedViewBar<TConfig>({
  views,
  activeViewId,
  canShare,
  onApply,
  onSave,
  onDelete,
}: SavedViewBarProps<TConfig>): JSX.Element {
  const { t } = useTranslation()
  const [naming, setNaming] = useState(false)
  const [name, setName] = useState('')
  const [shared, setShared] = useState(false)

  const reset = (): void => {
    setNaming(false)
    setName('')
    setShared(false)
  }

  const submit = (): void => {
    const trimmed = name.trim()
    if (!trimmed) return
    onSave(trimmed, shared && canShare)
    reset()
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      {views.map((view) => (
        <span
          key={view.id}
          className={`inline-flex items-center rounded-full text-xs font-medium border transition-colors ${
            activeViewId === view.id
              ? 'bg-fg text-bg border-transparent'
              : 'border-line text-fg-muted hover:text-fg'
          }`}
        >
          <button type="button" onClick={() => onApply(view)} className="pl-3 pr-1.5 py-1.5">
            {view.name}
            {view.sharedWithTeam && (
              <span className="ml-1.5 text-[10px] uppercase tracking-wide opacity-70" title={t('savedViews.sharedTitle')}>
                {t('savedViews.sharedMark')}
              </span>
            )}
          </button>
          {view.owned && (
            <button
              type="button"
              onClick={() => onDelete(view.id)}
              aria-label={t('savedViews.deleteAria', { name: view.name })}
              className="pr-2.5 pl-0.5 py-1.5 opacity-60 hover:opacity-100"
            >
              ×
            </button>
          )}
        </span>
      ))}
      {naming ? (
        <span className="inline-flex items-center gap-2">
          <input
            autoFocus
            value={name}
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') submit()
              if (event.key === 'Escape') reset()
            }}
            maxLength={80}
            placeholder={t('savedViews.namePlaceholder')}
            className="px-3 py-1.5 rounded-full text-xs border border-line bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-accent"
          />
          {canShare && (
            <label className="inline-flex items-center gap-1.5 text-xs text-fg-muted cursor-pointer select-none">
              <input
                type="checkbox"
                checked={shared}
                onChange={(event) => setShared(event.target.checked)}
                className="h-3.5 w-3.5 rounded accent-accent"
              />
              {t('savedViews.shareWithTeam')}
            </label>
          )}
          <button
            type="button"
            onClick={submit}
            className="px-3 py-1.5 rounded-full text-xs font-medium bg-fg text-bg"
          >
            {t('common.save')}
          </button>
        </span>
      ) : (
        <button
          type="button"
          onClick={() => setNaming(true)}
          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium border border-dashed border-line text-fg-muted hover:text-fg transition-colors"
        >
          {t('savedViews.saveCurrent')}
        </button>
      )}
    </div>
  )
}
