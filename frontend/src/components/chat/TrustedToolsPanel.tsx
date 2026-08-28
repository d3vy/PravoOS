import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { chatApi } from '../../api/chat'
import { Spinner } from '../ui/Spinner'

export function TrustedToolsPanel(): JSX.Element {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const panelRef = useRef<HTMLDivElement>(null)
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  const { data: trustedTools = [], isLoading } = useQuery({
    queryKey: ['ai', 'trusted-tools'],
    queryFn: chatApi.getTrustedTools,
    enabled: open,
  })

  const revokeMutation = useMutation({
    mutationFn: chatApi.revokeTrustedTool,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai', 'trusted-tools'] }),
  })

  return (
    <div className="relative" ref={panelRef}>
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        title={t('chat.trustedTools.openLabel')}
        aria-label={t('chat.trustedTools.openLabel')}
        className="w-7 h-7 rounded-md flex items-center justify-center text-fg-muted hover:text-accent hover:bg-bg transition-colors shrink-0"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
        </svg>
      </button>

      {open && (
        <div className="absolute top-full right-0 mt-2 w-72 max-h-80 overflow-y-auto rounded-xl border border-line bg-surface shadow-lg z-20 p-3 flex flex-col gap-2">
          <p className="text-xs font-medium text-fg">{t('chat.trustedTools.title')}</p>
          {isLoading ? (
            <div className="flex justify-center py-4">
              <Spinner size="sm" />
            </div>
          ) : trustedTools.length === 0 ? (
            <p className="text-xs text-fg-muted">{t('chat.trustedTools.empty')}</p>
          ) : (
            <ul className="flex flex-col gap-1">
              {trustedTools.map((trustedTool) => (
                <li
                  key={trustedTool.toolName}
                  className="flex items-center justify-between gap-2 text-xs text-fg"
                >
                  <span className="truncate">
                    {t(`chat.toolStep.${trustedTool.toolName}`, {
                      defaultValue: trustedTool.toolName,
                    })}
                  </span>
                  <button
                    type="button"
                    onClick={() => revokeMutation.mutate(trustedTool.toolName)}
                    disabled={revokeMutation.isPending}
                    className="shrink-0 text-fg-muted hover:text-danger transition-colors disabled:opacity-50"
                  >
                    {t('chat.trustedTools.revoke')}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
