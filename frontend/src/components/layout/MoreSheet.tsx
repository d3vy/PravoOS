import { useEffect } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../../store/authStore'
import { useMoreSheetStore } from '../../store/moreSheetStore'
import { useCommandPaletteStore } from '../../store/commandPaletteStore'
import { authApi } from '../../api/auth'
import { useLawyerAccountLinks, useLawyerNavSections } from './lawyerNav'
import { DashboardIcon, ClientsIcon, InvoiceIcon, SearchIcon } from './navIcons'

export function MoreSheet(): JSX.Element | null {
  const { t } = useTranslation()
  const open = useMoreSheetStore((state) => state.open)
  const close = useMoreSheetStore((state) => state.close)
  const openCommandPalette = useCommandPaletteStore((state) => state.setOpen)
  const lawyerNavSections = useLawyerNavSections()
  const lawyerAccountLinks = useLawyerAccountLinks()
  const { user, clearAuth } = useAuthStore()
  const navigate = useNavigate()

  useEffect(() => {
    if (!open) return
    const handleEscape = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') close()
    }
    document.addEventListener('keydown', handleEscape)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', handleEscape)
      document.body.style.overflow = ''
    }
  }, [open, close])

  if (!open) return null

  const toolsSection = lawyerNavSections.find((section) => section.id === 'tools')

  const quickLinks = [
    { to: '/dashboard', label: t('nav.dashboard'), icon: <DashboardIcon /> },
    { to: '/clients', label: t('nav.clients'), icon: <ClientsIcon /> },
    { to: '/invoices', label: t('nav.invoices'), icon: <InvoiceIcon /> },
  ]

  const handleLogout = async (): Promise<void> => {
    try {
      await authApi.logout()
    } catch {
      // best-effort revocation; local session is cleared regardless
    }
    clearAuth()
    close()
    navigate('/')
  }

  return (
    <div className="md:hidden fixed inset-0 z-50 flex flex-col justify-end">
      <button
        type="button"
        aria-label={t('common.close')}
        onClick={close}
        className="absolute inset-0 bg-black/40"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('nav.more')}
        className="relative bg-surface rounded-t-2xl border-t border-line max-h-[80vh] overflow-y-auto scrollbar-thin"
        style={{ paddingBottom: 'max(1rem, env(safe-area-inset-bottom))' }}
      >
        <div className="w-10 h-1 rounded-full bg-line mx-auto mt-2.5 mb-1" aria-hidden="true" />
        {user?.email && (
          <p className="px-5 pt-2 pb-3 text-xs text-fg-muted truncate border-b border-line">{user.email}</p>
        )}

        <div className="p-2">
          <button
            type="button"
            onClick={() => {
              close()
              openCommandPalette(true)
            }}
            className="w-full text-left flex items-center gap-3 px-3 min-h-11 rounded-lg text-sm font-medium text-fg hover:bg-bg transition-colors"
          >
            <span aria-hidden="true"><SearchIcon /></span>
            {t('nav.search')}
          </button>
          {quickLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              onClick={close}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 min-h-11 rounded-lg text-sm font-medium transition-colors ${
                  isActive ? 'text-accent bg-accent/5' : 'text-fg hover:bg-bg'
                }`
              }
            >
              <span aria-hidden="true">{link.icon}</span>
              {link.label}
            </NavLink>
          ))}
        </div>

        {toolsSection && (
          <div className="p-2 border-t border-line">
            <p className="eyebrow px-3 pt-1 pb-1">{toolsSection.title}</p>
            {toolsSection.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={close}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 min-h-11 rounded-lg text-sm font-medium transition-colors ${
                    isActive ? 'text-accent bg-accent/5' : 'text-fg hover:bg-bg'
                  }`
                }
              >
                <span aria-hidden="true">{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
          </div>
        )}

        <div className="p-2 border-t border-line">
          <p className="eyebrow px-3 pt-1 pb-1">{t('nav.account')}</p>
          {lawyerAccountLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              onClick={close}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 min-h-11 rounded-lg text-sm font-medium transition-colors ${
                  isActive ? 'text-accent bg-accent/5' : 'text-fg hover:bg-bg'
                }`
              }
            >
              <span aria-hidden="true">{link.icon}</span>
              {link.label}
            </NavLink>
          ))}
          <button
            type="button"
            onClick={() => void handleLogout()}
            className="w-full text-left flex items-center px-3 min-h-11 rounded-lg text-sm font-medium text-fg hover:bg-bg transition-colors"
          >
            {t('nav.logout')}
          </button>
        </div>
      </div>
    </div>
  )
}
