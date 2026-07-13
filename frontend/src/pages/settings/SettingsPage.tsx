import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { usersApi } from '../../api/users'
import type { NotificationSettingsResponse, TelegramLinkResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'

type SettingsTab = 'notifications' | 'integrations'

const TABS: { id: SettingsTab; label: string }[] = [
  { id: 'notifications', label: 'Уведомления' },
  { id: 'integrations', label: 'Интеграции' },
]

export default function SettingsPage(): JSX.Element {
  const [activeTab, setActiveTab] = useState<SettingsTab>('notifications')

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8 max-w-lg">
        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-6">Настройки</h1>

        <div className="flex gap-1 border-b border-light-border dark:border-dark-border mb-8">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-2.5 text-sm font-medium -mb-px border-b-2 transition-colors ${
                activeTab === tab.id
                  ? 'border-light-accent dark:border-dark-accent text-light-text dark:text-dark-text'
                  : 'border-transparent text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {activeTab === 'notifications' ? (
          <NotificationsTab onGoToIntegrations={() => setActiveTab('integrations')} />
        ) : (
          <IntegrationsTab />
        )}
      </div>
    </div>
  )
}

function NotificationsTab({ onGoToIntegrations }: { onGoToIntegrations: () => void }): JSX.Element {
  const queryClient = useQueryClient()

  const { data: settings, isLoading } = useQuery<NotificationSettingsResponse>({
    queryKey: ['notification-settings'],
    queryFn: usersApi.getNotificationSettings,
  })

  const [saved, setSaved] = useState(false)

  const updateMutation = useMutation({
    mutationFn: usersApi.updateNotificationSettings,
    onSuccess: (data) => {
      queryClient.setQueryData(['notification-settings'], data)
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    },
  })

  if (isLoading || !settings) {
    return <Spinner size="sm" />
  }

  const currentRequest = {
    loginAlertEmail: settings.loginAlertEmail,
    loginAlertTelegram: settings.loginAlertTelegram,
    caseMessageEmail: settings.caseMessageEmail,
    caseMessageTelegram: settings.caseMessageTelegram,
  }

  const toggleLoginChannel = (channel: 'email' | 'telegram'): void => {
    updateMutation.mutate({
      ...currentRequest,
      loginAlertEmail: channel === 'email' ? !settings.loginAlertEmail : settings.loginAlertEmail,
      loginAlertTelegram: channel === 'telegram' ? !settings.loginAlertTelegram : settings.loginAlertTelegram,
    })
  }

  const toggleCaseMessageChannel = (channel: 'email' | 'telegram'): void => {
    updateMutation.mutate({
      ...currentRequest,
      caseMessageEmail: channel === 'email' ? !settings.caseMessageEmail : settings.caseMessageEmail,
      caseMessageTelegram: channel === 'telegram' ? !settings.caseMessageTelegram : settings.caseMessageTelegram,
    })
  }

  const telegramSelectedButNotLinked =
    (settings.loginAlertTelegram || settings.caseMessageTelegram) && !settings.telegramLinked

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="text-xl font-semibold text-light-text dark:text-dark-text">Вход в аккаунт</h2>
        <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
          Куда присылать уведомление о входе с нового устройства.
        </p>
      </div>

      <ChannelSelect
        options={[
          { key: 'email', label: 'На почту', checked: settings.loginAlertEmail },
          { key: 'telegram', label: 'В Telegram', checked: settings.loginAlertTelegram },
        ]}
        onToggle={toggleLoginChannel}
        disabled={updateMutation.isPending}
      />

      <div>
        <h2 className="text-xl font-semibold text-light-text dark:text-dark-text">Сообщения по делу</h2>
        <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
          Куда присылать уведомление о новом сообщении в переписке по делу.
        </p>
      </div>

      <ChannelSelect
        options={[
          { key: 'email', label: 'На почту', checked: settings.caseMessageEmail },
          { key: 'telegram', label: 'В Telegram', checked: settings.caseMessageTelegram },
        ]}
        onToggle={toggleCaseMessageChannel}
        disabled={updateMutation.isPending}
      />

      {telegramSelectedButNotLinked && (
        <div className="rounded-lg border border-amber-400/50 bg-amber-50 dark:bg-amber-950/30 p-3">
          <p className="text-sm text-amber-700 dark:text-amber-400">
            Telegram не привязан — уведомления туда не дойдут.{' '}
            <button
              type="button"
              onClick={onGoToIntegrations}
              className="underline font-medium hover:no-underline"
            >
              Привязать в «Интеграциях»
            </button>
          </p>
        </div>
      )}

      {updateMutation.isError && (
        <p className="text-sm text-red-600 dark:text-red-400">Не удалось сохранить. Попробуйте снова.</p>
      )}
      {saved && <p className="text-sm text-green-600 dark:text-green-400">Сохранено</p>}
    </div>
  )
}

interface ChannelOption {
  key: 'email' | 'telegram'
  label: string
  checked: boolean
}

function ChannelSelect({
  options,
  onToggle,
  disabled,
}: {
  options: ChannelOption[]
  onToggle: (key: 'email' | 'telegram') => void
  disabled: boolean
}): JSX.Element {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  const selectedLabels = options.filter((option) => option.checked).map((option) => option.label)
  const summary = selectedLabels.length === 0 ? 'Не присылать' : selectedLabels.join(', ')

  return (
    <div ref={containerRef} className="relative max-w-xs">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className="input-base w-full flex items-center justify-between gap-2 text-left"
      >
        <span className="text-light-text dark:text-dark-text">{summary}</span>
        <ChevronIcon open={open} />
      </button>

      {open && (
        <div className="absolute z-10 mt-1 w-full rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-surface shadow-lg py-1">
          {options.map((option) => (
            <button
              key={option.key}
              type="button"
              disabled={disabled}
              onClick={() => onToggle(option.key)}
              className="w-full flex items-center gap-3 px-3 py-2.5 text-sm text-left text-light-text dark:text-dark-text hover:bg-light-surface dark:hover:bg-dark-bg disabled:opacity-50 transition-colors"
            >
              <span
                className={`flex items-center justify-center w-4 h-4 rounded border ${
                  option.checked
                    ? 'bg-light-accent dark:bg-dark-accent border-light-accent dark:border-dark-accent'
                    : 'border-light-border dark:border-dark-border'
                }`}
              >
                {option.checked && (
                  <svg className="w-3 h-3 text-white dark:text-dark-bg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                )}
              </span>
              {option.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function ChevronIcon({ open }: { open: boolean }): JSX.Element {
  return (
    <svg
      className={`w-4 h-4 text-light-secondary dark:text-dark-secondary transition-transform ${open ? 'rotate-180' : ''}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <polyline points="6 9 12 15 18 9" />
    </svg>
  )
}

function IntegrationsTab(): JSX.Element {
  const queryClient = useQueryClient()
  const [link, setLink] = useState<TelegramLinkResponse | null>(null)

  const { data: settings, isLoading } = useQuery<NotificationSettingsResponse>({
    queryKey: ['notification-settings'],
    queryFn: usersApi.getNotificationSettings,
  })

  const linkMutation = useMutation({
    mutationFn: usersApi.createTelegramLinkCode,
    onSuccess: (data) => setLink(data),
  })

  const unlinkMutation = useMutation({
    mutationFn: usersApi.unlinkTelegram,
    onSuccess: () => {
      setLink(null)
      queryClient.invalidateQueries({ queryKey: ['notification-settings'] })
      queryClient.invalidateQueries({ queryKey: ['profile'] })
    },
  })

  if (isLoading || !settings) {
    return <Spinner size="sm" />
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-light-text dark:text-dark-text">Telegram</h2>
        <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
          Привяжите Telegram, чтобы получать уведомления о входе и напоминания о дедлайнах.
        </p>
      </div>

      {settings.telegramLinked ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-green-600 dark:text-green-400">Telegram привязан</p>
          <div>
            <Button
              variant="secondary"
              loading={unlinkMutation.isPending}
              onClick={() => unlinkMutation.mutate()}
            >
              Отвязать Telegram
            </Button>
          </div>
          {unlinkMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">Не удалось отвязать. Попробуйте снова.</p>
          )}
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {!link && (
            <div>
              <Button variant="primary" loading={linkMutation.isPending} onClick={() => linkMutation.mutate()}>
                Привязать Telegram
              </Button>
            </div>
          )}

          {linkMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">Не удалось создать ссылку. Попробуйте снова.</p>
          )}

          {link && (
            <div className="flex flex-col gap-3 rounded-lg border border-light-border dark:border-dark-border p-4">
              <p className="text-sm text-light-text dark:text-dark-text">
                Откройте бота и нажмите «Запустить» — привязка произойдёт автоматически.
              </p>
              <div className="flex flex-wrap gap-2">
                <a href={link.deepLink} target="_blank" rel="noopener noreferrer">
                  <Button variant="primary">Открыть Telegram</Button>
                </a>
                <Button
                  variant="secondary"
                  onClick={() => queryClient.invalidateQueries({ queryKey: ['notification-settings'] })}
                >
                  Я привязал — обновить
                </Button>
              </div>
              <p className="text-xs text-light-secondary dark:text-dark-secondary">
                Если ссылка не сработала, отправьте боту команду:{' '}
                <code className="font-mono">/start {link.code}</code>
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
