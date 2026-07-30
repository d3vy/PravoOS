import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { usersApi } from '../../api/users'
import type { NotificationSettingsResponse, TelegramLinkResponse } from '../../types'
import { usePushNotifications } from '../../hooks/usePushNotifications'
import { useLanguage } from '../../hooks/useLanguage'
import { LANGUAGE_LABELS } from '../../i18n/config'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'

type SettingsTab = 'notifications' | 'integrations' | 'language'

export default function SettingsPage(): JSX.Element {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<SettingsTab>('notifications')

  const tabs: { id: SettingsTab; label: string }[] = [
    { id: 'notifications', label: t('settings.tabNotifications') },
    { id: 'integrations', label: t('settings.tabIntegrations') },
    { id: 'language', label: t('settings.tabLanguage') },
  ]

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-lg">
        <h1 className="text-3xl font-semibold text-fg mb-6">{t('settings.title')}</h1>

        <div className="flex gap-1 border-b border-line mb-8">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-2.5 text-sm font-medium -mb-px border-b-2 transition-colors ${
                activeTab === tab.id
                  ? 'border-accent text-fg'
                  : 'border-transparent text-fg-muted hover:text-fg'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {activeTab === 'notifications' && (
          <NotificationsTab onGoToIntegrations={() => setActiveTab('integrations')} />
        )}
        {activeTab === 'integrations' && <IntegrationsTab />}
        {activeTab === 'language' && <LanguageTab />}
      </div>
    </div>
  )
}

function LanguageTab(): JSX.Element {
  const { t } = useTranslation()
  const { language, supportedLanguages, setLanguage } = useLanguage()

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-fg">{t('settings.languageTitle')}</h2>
        <p className="text-sm text-fg-muted mt-1">{t('settings.languageDesc')}</p>
      </div>
      <div className="flex flex-col gap-2 max-w-xs">
        {supportedLanguages.map((code) => (
          <button
            key={code}
            type="button"
            onClick={() => setLanguage(code)}
            className={`flex items-center justify-between px-4 py-3 rounded-lg border text-sm transition-colors ${
              code === language
                ? 'border-accent text-fg bg-accent/5'
                : 'border-line text-fg hover:bg-surface'
            }`}
          >
            {LANGUAGE_LABELS[code]}
            {code === language && (
              <svg className="w-4 h-4 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            )}
          </button>
        ))}
      </div>
    </div>
  )
}

function NotificationsTab({ onGoToIntegrations }: { onGoToIntegrations: () => void }): JSX.Element {
  const { t } = useTranslation()
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
    loginAlertPush: settings.loginAlertPush,
    caseMessageEmail: settings.caseMessageEmail,
    caseMessageTelegram: settings.caseMessageTelegram,
    caseMessagePush: settings.caseMessagePush,
    digestPush: settings.digestPush,
  }

  const toggleDigestPush = (): void => {
    updateMutation.mutate({
      ...currentRequest,
      digestPush: !settings.digestPush,
    })
  }

  const toggleLoginChannel = (channel: NotificationChannel): void => {
    updateMutation.mutate({
      ...currentRequest,
      loginAlertEmail: channel === 'email' ? !settings.loginAlertEmail : settings.loginAlertEmail,
      loginAlertTelegram: channel === 'telegram' ? !settings.loginAlertTelegram : settings.loginAlertTelegram,
      loginAlertPush: channel === 'push' ? !settings.loginAlertPush : settings.loginAlertPush,
    })
  }

  const toggleCaseMessageChannel = (channel: NotificationChannel): void => {
    updateMutation.mutate({
      ...currentRequest,
      caseMessageEmail: channel === 'email' ? !settings.caseMessageEmail : settings.caseMessageEmail,
      caseMessageTelegram: channel === 'telegram' ? !settings.caseMessageTelegram : settings.caseMessageTelegram,
      caseMessagePush: channel === 'push' ? !settings.caseMessagePush : settings.caseMessagePush,
    })
  }

  const telegramSelectedButNotLinked =
    (settings.loginAlertTelegram || settings.caseMessageTelegram) && !settings.telegramLinked

  return (
    <div className="flex flex-col gap-6">
      <PushDeviceCard pushSelected={settings.loginAlertPush || settings.caseMessagePush} />

      <div>
        <h2 className="text-xl font-semibold text-fg">{t('settings.loginTitle')}</h2>
        <p className="text-sm text-fg-muted mt-1">
          {t('settings.loginDesc')}
        </p>
      </div>

      <ChannelSelect
        options={[
          { key: 'email', label: t('settings.channelEmail'), checked: settings.loginAlertEmail },
          { key: 'telegram', label: t('settings.channelTelegram'), checked: settings.loginAlertTelegram },
          { key: 'push', label: t('settings.channelPush'), checked: settings.loginAlertPush },
        ]}
        onToggle={toggleLoginChannel}
        disabled={updateMutation.isPending}
      />

      <div>
        <h2 className="text-xl font-semibold text-fg">{t('settings.caseMsgTitle')}</h2>
        <p className="text-sm text-fg-muted mt-1">
          {t('settings.caseMsgDesc')}
        </p>
      </div>

      <ChannelSelect
        options={[
          { key: 'email', label: t('settings.channelEmail'), checked: settings.caseMessageEmail },
          { key: 'telegram', label: t('settings.channelTelegram'), checked: settings.caseMessageTelegram },
          { key: 'push', label: t('settings.channelPush'), checked: settings.caseMessagePush },
        ]}
        onToggle={toggleCaseMessageChannel}
        disabled={updateMutation.isPending}
      />

      <div>
        <h2 className="text-xl font-semibold text-fg">{t('settings.digestTitle')}</h2>
        <p className="text-sm text-fg-muted mt-1">
          {t('settings.digestDesc')}
        </p>
      </div>

      <button
        type="button"
        disabled={updateMutation.isPending}
        onClick={toggleDigestPush}
        className="flex items-center gap-3 text-sm text-left text-fg disabled:opacity-50 transition-colors max-w-xs"
      >
        <span
          className={`flex items-center justify-center w-4 h-4 rounded border ${
            settings.digestPush ? 'bg-accent-solid border-accent' : 'border-line'
          }`}
        >
          {settings.digestPush && (
            <svg className="w-3 h-3 text-accent-fg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          )}
        </span>
        {t('settings.digestPushLabel')}
      </button>

      {telegramSelectedButNotLinked && (
        <div className="rounded-lg border border-amber-400/50 bg-amber-50 dark:bg-amber-950/30 p-3">
          <p className="text-sm text-warning">
            {t('settings.telegramNotLinked')}{' '}
            <button
              type="button"
              onClick={onGoToIntegrations}
              className="underline font-medium hover:no-underline"
            >
              {t('settings.linkInIntegrations')}
            </button>
          </p>
        </div>
      )}

      {updateMutation.isError && (
        <p className="text-sm text-danger">{t('settings.saveFailed')}</p>
      )}
      {saved && <p className="text-sm text-success">{t('settings.saved')}</p>}
    </div>
  )
}

type NotificationChannel = 'email' | 'telegram' | 'push'

interface ChannelOption {
  key: NotificationChannel
  label: string
  checked: boolean
}

function PushDeviceCard({ pushSelected }: { pushSelected: boolean }): JSX.Element | null {
  const { t } = useTranslation()
  const { state, isBusy, error, enable, disable } = usePushNotifications()

  if (state === 'unsupported' || state === 'not-configured' || state === 'loading') {
    return null
  }

  return (
    <div className="rounded-lg border border-line p-4 flex flex-col gap-3">
      <div>
        <h2 className="text-base font-semibold text-fg">{t('settings.pushDeviceTitle')}</h2>
        <p className="text-sm text-fg-muted mt-1">
          {state === 'subscribed' ? t('settings.pushSubscribedDesc') : t('settings.pushEnableDesc')}
        </p>
      </div>

      {state === 'blocked' ? (
        <p className="text-sm text-warning">
          {t('settings.pushBlocked')}
        </p>
      ) : (
        <Button
          variant={state === 'subscribed' ? 'secondary' : 'primary'}
          size="sm"
          className="self-start"
          disabled={isBusy}
          onClick={() => void (state === 'subscribed' ? disable() : enable())}
        >
          {state === 'subscribed' ? t('settings.pushDisableDevice') : t('settings.pushEnable')}
        </Button>
      )}

      {state !== 'subscribed' && pushSelected && state !== 'blocked' && (
        <p className="text-sm text-warning">
          {t('settings.pushChannelSelectedNotSubscribed')}
        </p>
      )}

      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  )
}

function ChannelSelect({
  options,
  onToggle,
  disabled,
}: {
  options: ChannelOption[]
  onToggle: (key: NotificationChannel) => void
  disabled: boolean
}): JSX.Element {
  const { t } = useTranslation()
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
  const summary = selectedLabels.length === 0 ? t('settings.dontSend') : selectedLabels.join(', ')

  return (
    <div ref={containerRef} className="relative max-w-xs">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className="input-base w-full flex items-center justify-between gap-2 text-left"
      >
        <span className="text-fg">{summary}</span>
        <ChevronIcon open={open} />
      </button>

      {open && (
        <div className="absolute z-10 mt-1 w-full rounded-lg border border-line bg-overlay shadow-lg py-1">
          {options.map((option) => (
            <button
              key={option.key}
              type="button"
              disabled={disabled}
              onClick={() => onToggle(option.key)}
              className="w-full flex items-center gap-3 px-3 py-2.5 text-sm text-left text-fg hover:bg-surface-2 disabled:opacity-50 transition-colors"
            >
              <span
                className={`flex items-center justify-center w-4 h-4 rounded border ${
                  option.checked
                    ? 'bg-accent-solid border-accent'
                    : 'border-line'
                }`}
              >
                {option.checked && (
                  <svg className="w-3 h-3 text-accent-fg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
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
      className={`w-4 h-4 text-fg-muted transition-transform ${open ? 'rotate-180' : ''}`}
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
  const { t } = useTranslation()
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
        <h2 className="text-xl font-semibold text-fg">Telegram</h2>
        <p className="text-sm text-fg-muted mt-1">
          {t('settings.telegramDesc')}
        </p>
      </div>

      {settings.telegramLinked ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-success">{t('settings.telegramLinked')}</p>
          <div>
            <Button
              variant="secondary"
              loading={unlinkMutation.isPending}
              onClick={() => unlinkMutation.mutate()}
            >
              {t('settings.unlinkTelegram')}
            </Button>
          </div>
          {unlinkMutation.isError && (
            <p className="text-sm text-danger">{t('settings.unlinkFailed')}</p>
          )}
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {!link && (
            <div>
              <Button variant="primary" loading={linkMutation.isPending} onClick={() => linkMutation.mutate()}>
                {t('settings.linkTelegram')}
              </Button>
            </div>
          )}

          {linkMutation.isError && (
            <p className="text-sm text-danger">{t('settings.linkCreateFailed')}</p>
          )}

          {link && (
            <div className="flex flex-col gap-3 rounded-lg border border-line p-4">
              <p className="text-sm text-fg">
                {t('settings.botInstruction')}
              </p>
              <div className="flex flex-wrap gap-2">
                <a href={link.deepLink} target="_blank" rel="noopener noreferrer">
                  <Button variant="primary">{t('settings.openTelegram')}</Button>
                </a>
                <Button
                  variant="secondary"
                  onClick={() => queryClient.invalidateQueries({ queryKey: ['notification-settings'] })}
                >
                  {t('settings.iLinkedRefresh')}
                </Button>
              </div>
              <p className="text-xs text-fg-muted">
                {t('settings.botCommandHint')}{' '}
                <code className="font-mono">/start {link.code}</code>
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
