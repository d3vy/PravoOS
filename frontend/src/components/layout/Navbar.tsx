import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../../store/authStore'
import { useCommandPaletteStore } from '../../store/commandPaletteStore'
import { useShortcutsDialogStore } from '../../store/shortcutsDialogStore'
import { authApi } from '../../api/auth'
import { Button } from '../ui/Button'
import { Logo } from '../ui/Logo'
import { NavTabs } from '../ui/NavTabs'
import { ThemeToggle } from '../ui/ThemeToggle'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'
import { GlobalTimer } from '../time/GlobalTimer'
import { useLawyerAccountLinks, useLawyerNavSections } from './lawyerNav'

const LAWYER_MOBILE_NAV_INDICATOR_ID = 'lawyer-mobile-nav-indicator'

export function Navbar(): JSX.Element {
  const { t } = useTranslation()
  const lawyerNavSections = useLawyerNavSections()
  const lawyerAccountLinks = useLawyerAccountLinks()
  const { user, clearAuth, isAuthenticated, effectiveRole } = useAuthStore()
  const navigate = useNavigate()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const mobileMenuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!mobileMenuOpen) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (mobileMenuRef.current && !mobileMenuRef.current.contains(event.target as Node)) {
        setMobileMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [mobileMenuOpen])

  const handleLogout = async (): Promise<void> => {
    try {
      await authApi.logout()
    } catch {
      // best-effort revocation; local session is cleared regardless
    }
    clearAuth()
    setMobileMenuOpen(false)
    navigate('/')
  }

  const role = effectiveRole()
  const dashboardPath = role === 'ADMIN' ? '/admin/applications' : '/dashboard'
  const authenticated = isAuthenticated()

  return (
    <header
      ref={mobileMenuRef}
      className="sticky top-0 z-50 bg-bg/90 backdrop-blur-md border-b border-line"
    >
      <div className="page-container">
        <nav className="flex items-center justify-between h-16">
          <Link to={authenticated ? dashboardPath : '/'} className="hover:opacity-80 transition-opacity">
            <Logo />
          </Link>

          <div className="flex items-center gap-3">
            {authenticated && role === 'LAWYER' && <GlobalTimer />}
            {authenticated && role === 'LAWYER' && (
              <div className="hidden md:block">
                <CreateMenu />
              </div>
            )}
            {authenticated && role === 'LAWYER' && <CommandTrigger />}
            {authenticated && role === 'LAWYER' && <ShortcutsTrigger />}
            <LanguageSwitcher />
            <ThemeToggle />

            {authenticated ? (
              <>
                {role === 'ADMIN' && (
                  <>
                    <Link to={dashboardPath} className="hidden md:block">
                      <Button variant="ghost" size="sm">
                        {t('nav.workspace')}
                      </Button>
                    </Link>
                    <div className="hidden md:block">
                      <Button variant="secondary" size="sm" onClick={() => void handleLogout()}>
                        {t('nav.logout')}
                      </Button>
                    </div>
                  </>
                )}
                {role === 'LAWYER' && (
                  <div className="hidden md:block">
                    <UserMenu email={user?.email ?? ''} onLogout={() => void handleLogout()} />
                  </div>
                )}
                <button
                  type="button"
                  aria-label={t('nav.menu')}
                  aria-expanded={mobileMenuOpen}
                  onClick={() => setMobileMenuOpen((open) => !open)}
                  className="md:hidden flex items-center justify-center w-10 h-10 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors"
                >
                  <HamburgerIcon open={mobileMenuOpen} />
                </button>
              </>
            ) : (
              <>
                <Link to="/login">
                  <Button variant="ghost" size="sm">
                    {t('nav.login')}
                  </Button>
                </Link>
                <Link to="/apply" className="hidden sm:block">
                  <Button variant="primary" size="sm">
                    {t('nav.apply')}
                  </Button>
                </Link>
              </>
            )}
          </div>
        </nav>
      </div>

      {authenticated && mobileMenuOpen && (
        <div className="md:hidden border-t border-line bg-bg/95 backdrop-blur-md">
          <div className="page-container py-3 flex flex-col gap-1">
            {role === 'LAWYER' && (
              <>
                <div className="mb-2">
                  <p className="eyebrow px-4 mb-1">{t('nav.create')}</p>
                  {CREATE_ACTIONS.map((action) => (
                    <Link
                      key={action.to}
                      to={action.to}
                      onClick={() => setMobileMenuOpen(false)}
                      className="block px-4 py-3 rounded-lg text-sm font-medium text-fg hover:bg-surface transition-colors"
                    >
                      {t(action.labelKey)}
                    </Link>
                  ))}
                </div>
                {lawyerNavSections.map((section) => (
                  <div key={section.id} className="mb-2">
                    <p className="eyebrow px-4 mb-1">{section.title}</p>
                    <NavTabs
                      items={section.items}
                      indicatorId={LAWYER_MOBILE_NAV_INDICATOR_ID}
                      orientation="vertical"
                      onNavigate={() => setMobileMenuOpen(false)}
                    />
                  </div>
                ))}
                <div className="mb-2">
                  <p className="eyebrow px-4 mb-1">{t('nav.account')}</p>
                  <NavTabs
                    items={lawyerAccountLinks}
                    indicatorId={LAWYER_MOBILE_NAV_INDICATOR_ID}
                    orientation="vertical"
                    onNavigate={() => setMobileMenuOpen(false)}
                  />
                </div>
              </>
            )}
            {role === 'ADMIN' && (
              <Link
                to={dashboardPath}
                onClick={() => setMobileMenuOpen(false)}
                className="px-4 py-3 rounded-lg text-sm font-medium text-fg hover:bg-surface transition-colors"
              >
                {t('nav.workspace')}
              </Link>
            )}
            <button
              onClick={() => void handleLogout()}
              className="text-left px-4 py-3 rounded-lg text-sm font-medium text-fg hover:bg-surface transition-colors"
            >
              {t('nav.logout')}
            </button>
          </div>
        </div>
      )}
    </header>
  )
}

const CREATE_ACTIONS: { to: string; labelKey: string }[] = [
  { to: '/cases?new=1', labelKey: 'nav.newCase' },
  { to: '/clients?new=1', labelKey: 'nav.newClient' },
  { to: '/chat', labelKey: 'nav.askAi' },
]

function CreateMenu(): JSX.Element {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const handleEscape = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  return (
    <div ref={menuRef} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className="inline-flex items-center gap-1.5 pl-2.5 pr-3 py-1.5 rounded-lg bg-accent-solid text-accent-fg text-sm font-medium hover:opacity-90 transition-opacity focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
      >
        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        {t('nav.create')}
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-52 rounded-xl border border-line bg-surface shadow-lg py-2 z-50"
        >
          {CREATE_ACTIONS.map((action) => (
            <Link
              key={action.to}
              to={action.to}
              role="menuitem"
              onClick={() => setOpen(false)}
              className="block px-4 py-2.5 text-sm text-fg hover:bg-bg transition-colors"
            >
              {t(action.labelKey)}
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}

function UserMenu({ email, onLogout }: { email: string; onLogout: () => void }): JSX.Element {
  const { t } = useTranslation()
  const lawyerAccountLinks = useLawyerAccountLinks()
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const initial = email.trim().charAt(0).toUpperCase() || '?'

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const handleEscape = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  return (
    <div ref={menuRef} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t('nav.userMenu')}
        onClick={() => setOpen((current) => !current)}
        className="flex items-center justify-center w-9 h-9 rounded-full bg-accent/10 text-accent text-sm font-semibold hover:bg-accent/20 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
      >
        {initial}
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-56 rounded-xl border border-line bg-surface shadow-lg py-2 z-50"
        >
          {email && (
            <p className="px-4 pt-1 pb-2 text-xs text-fg-muted truncate border-b border-line mb-1">
              {email}
            </p>
          )}
          {lawyerAccountLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              role="menuitem"
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                `flex items-center gap-2.5 px-4 py-2.5 text-sm transition-colors ${
                  isActive
                    ? 'text-accent'
                    : 'text-fg hover:bg-bg'
                }`
              }
            >
              <span aria-hidden="true">{link.icon}</span>
              {link.label}
            </NavLink>
          ))}
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false)
              onLogout()
            }}
            className="w-full text-left px-4 py-2.5 mt-1 border-t border-line text-sm text-fg hover:bg-bg transition-colors"
          >
            {t('nav.logout')}
          </button>
        </div>
      )}
    </div>
  )
}

function CommandTrigger(): JSX.Element {
  const { t } = useTranslation()
  const toggle = useCommandPaletteStore((state) => state.toggle)
  const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.userAgent)

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={t('nav.commandPalette')}
      className="hidden md:inline-flex items-center gap-2 pl-3 pr-2 py-1.5 rounded-lg border border-line text-fg-muted hover:text-fg hover:border-fg/30 transition-colors"
    >
      <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="11" cy="11" r="7" />
        <line x1="16.5" y1="16.5" x2="21" y2="21" />
      </svg>
      <span className="text-sm">{t('nav.search')}</span>
      <kbd className="inline-flex items-center rounded border border-line px-1.5 py-0.5 text-[11px] font-medium">
        {isMac ? '⌘K' : 'Ctrl K'}
      </kbd>
    </button>
  )
}

function ShortcutsTrigger(): JSX.Element {
  const { t } = useTranslation()
  const toggle = useShortcutsDialogStore((state) => state.toggle)

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={t('hotkeys.dialogTitle')}
      title={t('hotkeys.dialogTitle')}
      className="hidden md:inline-flex items-center justify-center w-9 h-9 rounded-lg border border-line text-fg-muted hover:text-fg hover:border-fg/30 transition-colors text-sm font-medium"
    >
      ?
    </button>
  )
}

function HamburgerIcon({ open }: { open: boolean }): JSX.Element {
  return (
    <svg className="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      {open ? (
        <>
          <line x1="6" y1="6" x2="18" y2="18" />
          <line x1="6" y1="18" x2="18" y2="6" />
        </>
      ) : (
        <>
          <line x1="4" y1="7" x2="20" y2="7" />
          <line x1="4" y1="12" x2="20" y2="12" />
          <line x1="4" y1="17" x2="20" y2="17" />
        </>
      )}
    </svg>
  )
}
