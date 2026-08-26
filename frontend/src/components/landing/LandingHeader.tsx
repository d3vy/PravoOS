import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'
import { LandingLogo } from './LandingLogo'

const NAV_LINKS = [
  { href: '#product', key: 'landing.navProduct' },
  { href: '#early-access', key: 'landing.navEarlyAccess' },
  { href: '#contacts', key: 'landing.navContacts' },
]

function homePathForRole(role: string | undefined): string {
  if (role === 'ADMIN') return '/admin/applications'
  if (role === 'CLIENT') return '/portal'
  return '/dashboard'
}

export function LandingHeader(): JSX.Element {
  const { t } = useTranslation()
  const { isAuthenticated, effectiveRole } = useAuthStore()
  const [menuOpen, setMenuOpen] = useState(false)
  const authenticated = isAuthenticated()

  return (
    <header className="relative z-30">
      <div className="mx-auto flex h-[76px] w-full max-w-[1920px] items-center justify-between px-5 md:h-[80px] md:px-10">
        <LandingLogo />

        <nav className="hidden items-center gap-8 text-[18px] leading-none md:flex">
          {NAV_LINKS.map((link) => (
            <a key={link.href} href={link.href} className="transition-opacity hover:opacity-70">
              {t(link.key)}
            </a>
          ))}
        </nav>

        <div className="hidden items-center gap-3 md:flex">
          <LanguageSwitcher tone="landing" />
          {authenticated ? (
            <Link
              to={homePathForRole(effectiveRole())}
              className="inline-flex h-[42px] items-center rounded-full bg-[#1A0F0A] px-5 text-[17px] leading-none text-white transition-opacity hover:opacity-90"
            >
              {t('landing.navWorkspace')}
            </Link>
          ) : (
            <>
              <Link
                to="/login"
                className="inline-flex h-[42px] items-center rounded-full border border-[#1A0F0A]/20 px-5 text-[17px] leading-none transition-colors hover:bg-[#1A0F0A0D]"
              >
                {t('landing.navLogin')}
              </Link>
              <Link
                to="/apply"
                className="inline-flex h-[42px] items-center rounded-full bg-[#1A0F0A] px-5 text-[17px] leading-none text-white transition-opacity hover:opacity-90"
              >
                {t('landing.navApply')}
              </Link>
            </>
          )}
        </div>

        <button
          type="button"
          aria-label={t('nav.menu')}
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
          className="flex h-6 w-6 flex-col justify-center gap-[5px] md:hidden"
        >
          <span
            className={`h-px bg-[#1A0F0A] transition-all ${menuOpen ? 'w-5 translate-y-[6px] -rotate-45' : 'w-5'}`}
          />
          <span className={`h-px w-5 bg-[#1A0F0A] transition-all ${menuOpen ? 'opacity-0' : ''}`} />
          <span
            className={`h-px bg-[#1A0F0A] transition-all ${menuOpen ? 'w-5 -translate-y-[6px] rotate-45' : 'w-5'}`}
          />
        </button>
      </div>

      {menuOpen && (
        <div className="flex flex-col gap-4 px-5 pb-6 md:hidden">
          {NAV_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              onClick={() => setMenuOpen(false)}
              className="text-[16px] leading-none"
            >
              {t(link.key)}
            </a>
          ))}
          <div className="flex flex-wrap items-center gap-2 pt-1">
            <LanguageSwitcher tone="landing" />
            {authenticated ? (
              <Link
                to={homePathForRole(effectiveRole())}
                className="rounded-full bg-[#1A0F0A] px-5 py-2.5 text-[15px] leading-none text-white"
              >
                {t('landing.navWorkspace')}
              </Link>
            ) : (
              <>
                <Link
                  to="/login"
                  className="rounded-full border border-[#1A0F0A33] px-5 py-2.5 text-[15px] leading-none"
                >
                  {t('landing.navLogin')}
                </Link>
                <Link
                  to="/apply"
                  className="rounded-full bg-[#1A0F0A] px-5 py-2.5 text-[15px] leading-none text-white"
                >
                  {t('landing.navApply')}
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  )
}
