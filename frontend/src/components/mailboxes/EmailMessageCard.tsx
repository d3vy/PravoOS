import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import type { EmailMessageResponse } from '../../types'

function locale(): string {
  return i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
}

export function EmailMessageCard({
  message,
  actions,
}: {
  message: EmailMessageResponse
  actions?: ReactNode
}): JSX.Element {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)

  return (
    <div className="p-4 rounded-lg bg-surface border border-line">
      <div className="flex items-start justify-between gap-3 mb-1.5">
        <div className="min-w-0">
          <p className="text-sm font-medium text-fg truncate">
            {message.subject?.trim() || t('emails.noSubject')}
          </p>
          <p className="text-xs text-fg-muted truncate">
            {message.direction === 'OUT'
              ? t('emails.toLine', { addresses: message.toAddresses })
              : t('emails.fromLine', { address: message.fromAddress })}
          </p>
        </div>
        <span className="text-xs text-fg-muted shrink-0">
          {new Date(message.sentAt).toLocaleString(locale(), {
            day: '2-digit',
            month: '2-digit',
            year: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
          })}
        </span>
      </div>

      {message.bodyText && (
        <button
          type="button"
          onClick={() => setExpanded((value) => !value)}
          className={`text-sm text-fg-muted text-left whitespace-pre-wrap ${expanded ? '' : 'line-clamp-2'}`}
        >
          {message.bodyText}
        </button>
      )}

      <div className="flex flex-wrap items-center gap-2 mt-2">
        {message.hasAttachments && (
          <span className="text-xs px-2 py-0.5 rounded-full bg-bg border border-line text-fg-muted">
            {t('emails.attachments', { count: message.attachmentCount })}
          </span>
        )}
        {message.linkSourceName && (
          <span className="text-xs px-2 py-0.5 rounded-full bg-bg border border-line text-fg-muted">
            {message.linkSourceName}
          </span>
        )}
        {actions}
      </div>
    </div>
  )
}
